# ============================================================================
# 腾讯云远程执行器（读取同目录 server.conf）
# ----------------------------------------------------------------------------
# 位置：仓库根目录（与 server.conf、provision.sh 同级）
# 入库策略：
#   · server.conf        —— 含凭据，**已被 .gitignore 忽略，永不提交**
#   · server.conf.example—— 无凭据的模板，**可以提交**，新环境复制后填写
#   · remote.ps1 / provision.sh —— 不含任何凭据，可以提交
#
# 安全约定（必须遵守）：
#   · 密码只存在于本进程内存，**从不打印**；所有远端输出都会先做脱敏再显示。
#   · 首次连接成功后请立刻执行 -BootstrapKey：它会装好密钥、把 AUTH_MODE 切成 key，
#     并把 server.conf 里的 PASSWORD 就地清成占位符（磁盘上也不再留明文）。
#   · 私钥默认落在仓库根的 .ssh-local/（已 gitignore），不要挪到会被打包分享的位置。
#
# 用法（执行策略为默认 Restricted 时需带 -ExecutionPolicy Bypass）：
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\remote.ps1 -Test
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\remote.ps1 -BootstrapKey
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\remote.ps1 -Provision
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\remote.ps1 -Cmd "docker ps -a"
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\remote.ps1 -Script .\my.sh
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\remote.ps1 -Put ".\a.txt /tmp/a.txt"
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\remote.ps1 -Get "/tmp/a.txt .\a.txt"
# ============================================================================
param(
    [string]$Cmd,
    [string]$Script,
    [string]$Put,
    [string]$Get,
    [switch]$Test,
    [switch]$BootstrapKey,
    [switch]$Provision,
    [switch]$NoSudo
)

# 注意：这里必须用 Continue，不能是 Stop。
# 原因：PowerShell 5.1 在 EAP=Stop 且 `2>&1` 时，会把原生命令（plink/pscp/ssh）的
#       任何 stderr 输出当成终止性 NativeCommandError 抛出，
#       于是"主机指纹提示""密码认证失败"这类正常输出会直接中断整个脚本。
#       因此关键步骤一律用 $LASTEXITCODE 判断，失败时显式 throw。
$ErrorActionPreference = "Continue"
$script:ForceNoSudo = $NoSudo
$OutputEncoding = [System.Text.Encoding]::UTF8
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }

$Here = Split-Path -Parent $MyInvocation.MyCommand.Path
$ConfPath = Join-Path $Here "server.conf"
if (-not (Test-Path $ConfPath)) { throw "缺少配置文件：$ConfPath" }

# ── 读配置（KEY=VALUE，# 或 ; 开头为注释）────────────────────────────────────
# ⚠️ 必须显式 -Encoding UTF8：server.conf 是无 BOM 的 UTF-8，
#    PowerShell 5.1 默认按 ANSI/GBK 读，中文注释的字节会"吃掉"换行导致多行合并，
#    结果 HOST/USER 等键整条丢失（表现为"明明填了却报未填"）。
$Conf = @{}
Get-Content $ConfPath -Encoding UTF8 | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#") -or $line.StartsWith(";")) { return }
    $i = $line.IndexOf("=")
    if ($i -lt 1) { return }
    $Conf[$line.Substring(0, $i).Trim()] = $line.Substring($i + 1).Trim()
}
function Conf($name, $default) {
    if ($Conf.ContainsKey($name) -and $Conf[$name] -ne "") { return $Conf[$name] }
    return $default
}

$HostIp = Conf "HOST" ""
if ($HostIp -like "<*" -or $HostIp -eq "") { throw "server.conf 里的 HOST 还没填（脚本不打印其内容，请自行编辑）" }
$Port = Conf "PORT" "22"
$User = Conf "USER" "root"
$AuthMode = (Conf "AUTH_MODE" "password").ToLower()
$Password = Conf "PASSWORD" ""
$KeyPath = Conf "KEY_PATH" (Join-Path $Here ".ssh-local\id_ed25519")
# 支持相对路径（相对本脚本所在目录），避免换机器后路径写死
if (-not [System.IO.Path]::IsPathRooted($KeyPath)) { $KeyPath = Join-Path $Here $KeyPath }

# 自动发现：若指定的私钥不存在，但 .ssh-local 下恰好有一个 .pem（腾讯云下载的密钥对），就用它。
# 这样把 xxx.pem 丢进 .ssh-local 即可，无需改配置。
if (-not (Test-Path $KeyPath)) {
    $pems = Get-ChildItem (Join-Path $Here ".ssh-local") -Filter "*.pem" -File -ErrorAction SilentlyContinue
    if ($pems -and $pems.Count -eq 1) {
        $KeyPath = $pems[0].FullName
        Write-Host "[i] 自动发现私钥：.ssh-local\$($pems[0].Name)"
    }
}

# Windows OpenSSH 会因私钥 ACL 过宽而拒绝使用；这里只给当前用户读权限（幂等、静默）。
if (Test-Path $KeyPath) {
    try {
        & icacls $KeyPath /inheritance:r /grant:r "$($env:USERNAME):R" 2>&1 | Out-Null
    } catch { }
}
$SwapGb = Conf "SWAP_GB" "4"
$Tz = Conf "TIMEZONE" "Asia/Shanghai"
$DeployDir = Conf "DEPLOY_DIR" "/opt/ai-code"
$Mirror = Conf "DOCKER_MIRROR" ""

if ($Password -like "<*") { $Password = "" }
$hasKey = (Test-Path $KeyPath)
$useKey = ($AuthMode -eq "key" -or ($hasKey -and $AuthMode -ne "password"))
if (-not $useKey -and $Password -eq "") { throw "既没有可用密钥、也没有密码，无法连接（检查 server.conf 的 AUTH_MODE/PASSWORD/KEY_PATH）" }

$Plink = "C:\Program Files (x86)\PuTTY\plink.exe"
$Pscp = "C:\Program Files (x86)\PuTTY\pscp.exe"

# ── 脱敏：任何输出在显示前把密码替换掉（含未知长度的兜底）────────────────────
function Protect-Text {
    param([string]$Text)
    if ([string]::IsNullOrEmpty($Text)) { return $Text }
    if ($Password) { $Text = $Text.Replace($Password, "***REDACTED***") }
    return $Text
}
function Write-Safe { param($Lines) ; foreach ($l in $Lines) { Write-Host (Protect-Text ([string]$l)) } }
function Write-Head($text) { Write-Host "`n=== $text ===" -ForegroundColor Cyan }

# ── 远端执行（脚本内容上传后执行，避开引号问题；输出统一脱敏）─────────────────
function Invoke-Remote {
    param([Parameter(Mandatory)][string]$Content, [switch]$NoSudo)
    $g = [guid]::NewGuid().ToString("N").Substring(0, 8)
    $remoteSh = "/tmp/w19-$g.sh"
    $localSh = Join-Path $env:TEMP "w19-$g.sh"
    $Content = $Content -replace "`r`n", "`n"
    [System.IO.File]::WriteAllText($localSh, "#!/usr/bin/env bash`n" + $Content,
        (New-Object System.Text.UTF8Encoding($false)))
    $prefix = if ($User -eq "root" -or $NoSudo -or $script:ForceNoSudo) { "" } else { "sudo -n " }
    try {
        if ($useKey) {
            & scp -i $KeyPath -P $Port -o StrictHostKeyChecking=accept-new -o BatchMode=yes -q $localSh "${User}@${HostIp}:${remoteSh}" 2>&1 | Out-Null
        } else {
            & $Pscp -pw $Password -P $Port -batch -q $localSh "${User}@${HostIp}:${remoteSh}" 2>&1 | Out-Null
        }
        if ($LASTEXITCODE -ne 0) { throw "上传脚本失败（exit=$LASTEXITCODE）" }

        $remoteCmd = "${prefix}env LANG=C.UTF-8 bash ${remoteSh}; rc=`$?; rm -f ${remoteSh}; exit `$rc"
        if ($useKey) {
            $out = & ssh -i $KeyPath -p $Port -o StrictHostKeyChecking=accept-new -o BatchMode=yes "${User}@${HostIp}" $remoteCmd 2>&1
        } else {
            $out = & $Plink -ssh -pw $Password -P $Port -batch -no-antispoof "${User}@${HostIp}" $remoteCmd 2>&1
        }
        Write-Safe $out
        return $LASTEXITCODE
    } finally {
        Remove-Item $localSh -Force -ErrorAction SilentlyContinue
    }
}

function Initialize-HostKey {
    if ($useKey) { return }
    Write-Host "[i] 首次连接，接收主机指纹（密码不出现在屏幕上）..."
    $out = cmd /c "echo y | `"$Plink`" -ssh -pw $Password -P $Port -no-antispoof ${User}@${HostIp} exit" 2>&1
    Write-Safe $out
}

function Install-PublicKey {
    Write-Head "生成并安装 SSH 密钥"
    if (-not (Test-Path $KeyPath)) {
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $KeyPath) | Out-Null
        & ssh-keygen -t ed25519 -f $KeyPath -N '""' -C "week19-cloud" 2>&1 | Out-Null
        Write-Host "[+] 已生成密钥（内容不显示）"
    } else { Write-Host "[i] 复用已有密钥" }
    $pub = (Get-Content "$KeyPath.pub" -Raw).Trim()
    # 单引号 here-string：PowerShell 不做任何展开，bash 的 $( ) 原样保留
    $content = @'
mkdir -p ~/.ssh && chmod 700 ~/.ssh
touch ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys
grep -qF 'week19-cloud' ~/.ssh/authorized_keys 2>/dev/null || echo '__PUBKEY__' >> ~/.ssh/authorized_keys
echo "[ok] authorized_keys 条目数: $(wc -l < ~/.ssh/authorized_keys)"
'@
    $content = $content.Replace('__PUBKEY__', $pub)
    if ((Invoke-Remote -Content $content -NoSudo) -ne 0) { throw "安装公钥失败" }

    Write-Host "[i] 验证密钥登录..."
    $out = & ssh -i $KeyPath -p $Port -o StrictHostKeyChecking=accept-new -o BatchMode=yes -o ConnectTimeout=10 "${User}@${HostIp}" "echo KEY_LOGIN_OK" 2>&1
    Write-Safe $out
    if ($LASTEXITCODE -eq 0) {
        $text = Get-Content $ConfPath -Raw -Encoding UTF8
        $text = $text -replace "(?m)^AUTH_MODE=.*$", "AUTH_MODE=key"
        # 顺手把密码替换成占位符，避免明文长期留在磁盘
        $text = $text -replace "(?m)^PASSWORD=.*$", "PASSWORD=<已切密钥登录，此值不再使用>"
        [System.IO.File]::WriteAllText($ConfPath, $text, (New-Object System.Text.UTF8Encoding($false)))
        Write-Host "[+] 密钥可用；AUTH_MODE 已改为 key，PASSWORD 已就地清除" -ForegroundColor Green
    } else { Write-Warning "密钥验证失败，配置未改动" }
}

function Test-Connection {
    Write-Head "连通性自检（$($(if($useKey){'密钥'}else{'密码'}))）"
    # 单引号 here-string：脚本原样送到远端，避免 PowerShell 抢先展开 $( )
    $content = @'
echo "hostname : $(hostname)"
echo "os       : $(. /etc/os-release 2>/dev/null; echo $PRETTY_NAME)"
echo "kernel   : $(uname -r)"
echo "cpu      : $(nproc) cores"
echo "mem      : $(free -m | awk '/Mem:/{print $2" MB total, "$7" MB available"}')"
echo "swap     : $(free -m | awk '/Swap:/{print $2" MB"}')"
echo "disk     : $(df -h / | awk 'NR==2{print $2" total, "$4" avail ("$5" used)"}')"
echo "timezone : $(timedatectl show -p Timezone --value 2>/dev/null || cat /etc/timezone)"
echo "docker   : $(command -v docker >/dev/null && docker version --format '{{.Server.Version}}' 2>/dev/null || echo 'not installed')"
echo "compose  : $(docker compose version --short 2>/dev/null || echo 'not installed')"
echo "java     : $(command -v java >/dev/null && java -version 2>&1 | head -1 || echo 'not installed')"
echo "uptime   : $(uptime -p 2>/dev/null)"
'@
    if ((Invoke-Remote -Content $content) -ne 0) { Write-Warning "命令返回非 0，见上面的输出" }
}

function Invoke-Provision {
    Write-Head "服务器初始化（swap + docker + 目录）"
    $provSh = Join-Path $Here "provision.sh"
    if (-not (Test-Path $provSh)) { throw "缺少 provision.sh" }
    $body = (Get-Content $provSh -Raw) -replace "`r`n", "`n"
    $body = "export SWAP_GB=$SwapGb; export TZ_NAME=$Tz; export DEPLOY_DIR=$DeployDir; export DOCKER_MIRROR='$Mirror'`n" + $body
    # 注意：这里**不传** -NoSudo —— apt/docker 需要 root；非 root 用户时由外层自动加 sudo -n
    if ((Invoke-Remote -Content $body) -ne 0) { Write-Warning "初始化脚本返回非 0，见上面输出" }
}

# ── 入口 ────────────────────────────────────────────────────────────────────
$did = $false
if ($Test -or (-not $Cmd -and -not $Script -and -not $Put -and -not $Get -and -not $BootstrapKey -and -not $Provision)) {
    Initialize-HostKey; Test-Connection; $did = $true
}
if ($BootstrapKey) { Initialize-HostKey; Install-PublicKey; $did = $true }
if ($Provision) { Initialize-HostKey; Invoke-Provision; $did = $true }
if ($Cmd) {
    Initialize-HostKey; Write-Head "执行命令"
    if ((Invoke-Remote -Content $Cmd) -ne 0) { Write-Warning "退出码非 0" }
    $did = $true
}
if ($Script) {
    Initialize-HostKey
    if (-not (Test-Path $Script)) { throw "脚本不存在：$Script" }
    Write-Head "执行本地脚本"
    if ((Invoke-Remote -Content ((Get-Content $Script -Raw -Encoding UTF8) -replace "`r`n", "`n")) -ne 0) { Write-Warning "退出码非 0" }
    $did = $true
}
if ($Put) {
    Initialize-HostKey
    $p = $Put -split '\s+', 2
    if ($p.Count -ne 2) { throw "用法：-Put '<本地> <远端>'" }
    if ($useKey) { & scp -i $KeyPath -P $Port -o StrictHostKeyChecking=accept-new -o BatchMode=yes $p[0] "${User}@${HostIp}:$($p[1])" }
    else { & $Pscp -pw $Password -P $Port -batch $p[0] "${User}@${HostIp}:$($p[1])" }
    Write-Host "[+] 上传完成"
    $did = $true
}
if ($Get) {
    Initialize-HostKey
    $p = $Get -split '\s+', 2
    if ($p.Count -ne 2) { throw "用法：-Get '<远端> <本地>'" }
    if ($useKey) { & scp -i $KeyPath -P $Port -o StrictHostKeyChecking=accept-new -o BatchMode=yes "${User}@${HostIp}:$($p[0])" $p[1] }
    else { & $Pscp -pw $Password -P $Port -batch "${User}@${HostIp}:$($p[0])" $p[1] }
    Write-Host "[+] 下载完成"
    $did = $true
}
if (-not $did) { Write-Host "没有指定动作，用 -Test 做连通性自检" -ForegroundColor Yellow }
