# ============================================================================
# 指标反向隧道（Week 20）：让云上的 Prometheus 抓到本地应用的 /actuator/prometheus
# ----------------------------------------------------------------------------
# 原理：ssh -N -R 把「云端 127.0.0.1:18084/18080」反向映射到「本地 8084/8080」。
#       sshd 只绑回环，公网不可达；Prometheus（host 网络）直接抓 127.0.0.1:18084。
#
# 与 tools\tunnel.ps1 的分工：
#   tunnel.ps1        本地 -> 云端（-L）：连 PG / Redis / Langfuse / SkyWalking / Grafana
#   metrics-tunnel.ps1 云端 -> 本地（-R）：Prometheus 抓本地应用指标
# 两者互相独立：只开 -L 不影响业务；不开本脚本，Prometheus 的 target 会 down（告警会提示）。
#
# 用法：
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\metrics-tunnel.ps1
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\metrics-tunnel.ps1 -Status
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\metrics-tunnel.ps1 -Stop
# ============================================================================
param(
    [switch]$Stop,
    [switch]$Status,
    [switch]$Force
)

$ErrorActionPreference = "Continue"
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }

$Repo = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Conf = Join-Path $Repo "server.conf"
$LogDir = Join-Path $Repo ".ssh-local"
$PidFile = Join-Path $LogDir "metrics-tunnel.pid"
$LogFile = Join-Path $LogDir "metrics-tunnel.log"
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

if (-not (Test-Path $Conf)) { throw "缺少 server.conf" }

$T = @{}
Get-Content $Conf -Encoding UTF8 | ForEach-Object {
    $l = $_.Trim()
    if ($l -eq "" -or $l.StartsWith("#")) { return }
    $i = $l.IndexOf("="); if ($i -lt 1) { return }
    $T[$l.Substring(0, $i).Trim()] = $l.Substring($i + 1).Trim()
}
$h = $T["HOST"]; $u = $T["USER"]; $p = $T["PORT"]
$k = $T["KEY_PATH"]
if (-not $k) { $k = ".ssh-local\xshell_rsa" }
if (-not [System.IO.Path]::IsPathRooted($k)) { $k = Join-Path $Repo $k }
if (-not $h -or -not (Test-Path $k)) { throw "server.conf 的 HOST 为空或私钥不存在：$k" }

function Log([string]$msg) {
    $line = "{0} {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $msg
    Add-Content -Path $LogFile -Value $line -Encoding UTF8
}

function Ssh-Run([string]$remoteCommand) {
    $out = & ssh -i $k -p $p -o BatchMode=yes -o StrictHostKeyChecking=accept-new `
        "$u@$h" $remoteCommand 2>&1
    return ($out -join "`n")
}

function Probe-Local([int]$port, [string]$path) {
    try {
        $r = Invoke-WebRequest -Uri "http://127.0.0.1:$port$path" -TimeoutSec 3 -UseBasicParsing -ErrorAction Stop
        return "OK (HTTP $($r.StatusCode))"
    } catch {
        if ($_.Exception.Response) { return "OK (HTTP $([int]$_.Exception.Response.StatusCode))" }
        return "DOWN"
    }
}

function Show-Status {
    Write-Host "`n=== 本地应用（指标源）===" -ForegroundColor Cyan
    "  127.0.0.1:8084 /actuator/prometheus : {0}" -f (Probe-Local 8084 "/actuator/prometheus")
    "  127.0.0.1:8080 /actuator/prometheus : {0}" -f (Probe-Local 8080 "/actuator/prometheus")

    Write-Host "`n=== 云端反向端口（18084 / 18080）===" -ForegroundColor Cyan
    $listening = Ssh-Run "ss -lnt 2>/dev/null | grep -E '127.0.0.1:(18084|18080)' || true"
    if ([string]::IsNullOrWhiteSpace($listening)) {
        "  未监听（执行本脚本启动反向隧道）"
    } else {
        $listening
    }

    if (Test-Path $PidFile) {
        $tp = (Get-Content $PidFile -ErrorAction SilentlyContinue | Select-Object -First 1)
        $alive = [bool](Get-Process -Id $tp -ErrorAction SilentlyContinue)
        "`n隧道进程 PID=$tp 存活=$alive"
    } else {
        "`n没有 PID 记录（可能未启动）"
    }
}

if ($Status) { Show-Status; exit 0 }

if ($Stop) {
    if (Test-Path $PidFile) {
        $tp = (Get-Content $PidFile | Select-Object -First 1)
        Stop-Process -Id $tp -Force -ErrorAction SilentlyContinue
        Remove-Item $PidFile -Force
        Log "stopped pid=$tp"
    }
    Get-CimInstance Win32_Process -Filter "Name='ssh.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -match 'R 127\.0\.0\.1:1808[04]:' } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
    Write-Host "已停止指标反向隧道" -ForegroundColor Yellow
    exit 0
}

# 幂等：已存活则不重复启动（除非 -Force）
if ((-not $Force) -and (Test-Path $PidFile)) {
    $oldPid = (Get-Content $PidFile -ErrorAction SilentlyContinue | Select-Object -First 1)
    if ($oldPid -and (Get-Process -Id $oldPid -ErrorAction SilentlyContinue)) {
        Write-Host "指标隧道已在运行（PID=$oldPid）；如需重启加 -Force" -ForegroundColor Green
        Show-Status
        exit 0
    }
}

# 预检：云端端口若已被别的进程占用，-R 会失败但仍返回成功，导致「以为开了、其实没开」。
$busy = Ssh-Run "ss -lnt 2>/dev/null | grep -E '127.0.0.1:(18084|18080)' || true"
if (-not [string]::IsNullOrWhiteSpace($busy)) {
    Write-Host "云端 18084/18080 已被占用（可能是上次未清理的隧道）：" -ForegroundColor Red
    $busy
    Write-Host "请先在服务器上确认并清理，或用 -Stop 后重试。" -ForegroundColor Yellow
    exit 1
}

# 注意 ExitOnForwardFailure=no：单个 -R 失败不应让整个隧道退出；
# 启动后会用 -Status 的方式自查云端监听，避免「静默半开」。
$sshArgs = @("-N", "-T", "-i", $k, "-p", $p,
    "-o", "BatchMode=yes",
    "-o", "ExitOnForwardFailure=no",
    "-o", "ServerAliveInterval=30",
    "-o", "ServerAliveCountMax=3",
    "-o", "StrictHostKeyChecking=accept-new",
    "-R", "127.0.0.1:18084:127.0.0.1:8084",
    "-R", "127.0.0.1:18080:127.0.0.1:8080",
    "$u@$h")

$proc = Start-Process -FilePath "ssh" -ArgumentList $sshArgs -PassThru -WindowStyle Hidden
$proc.Id | Set-Content -Path $PidFile -Encoding ASCII
Log "started pid=$($proc.Id)"
Write-Host "指标反向隧道已启动 PID=$($proc.Id)，等待握手..." -ForegroundColor Green
Start-Sleep -Seconds 4

Show-Status
Write-Host "`n提示：本脚本只负责「云端 Prometheus 抓本地指标」；业务流量隧道仍是 tools\tunnel.ps1。" -ForegroundColor Cyan
