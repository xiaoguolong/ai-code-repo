# ============================================================================
# 隧道守护脚本（供 Windows 计划任务调用，不弹窗、写日志、幂等）
# ----------------------------------------------------------------------------
# 行为：
#   1. 已有存活隧道 -> 直接退出（重复触发安全）
#   2. 没有 -> 启动 ssh -N -L 全套转发，写 pid 与日志
#   3. 启动后 5 秒内探测关键端口，失败则重试（最多 3 次）
#
# 日志：<repo>\.ssh-local\tunnel.log
# ============================================================================
param([switch]$Force)

$ErrorActionPreference = "Continue"
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }

$Repo = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Conf = Join-Path $Repo "server.conf"
$LogDir = Join-Path $Repo ".ssh-local"
$PidFile = Join-Path $LogDir "tunnel.pid"
$LogFile = Join-Path $LogDir "tunnel.log"
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

function Log($msg) {
    $line = "{0} {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $msg
    Add-Content -Path $LogFile -Value $line -Encoding UTF8
}

if (-not (Test-Path $Conf)) { Log "ERROR: server.conf 不存在"; exit 1 }

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
if (-not $h -or -not (Test-Path $k)) { Log "ERROR: HOST 为空或私钥不存在 ($k)"; exit 1 }

# 已有存活进程则直接返回
if ((-not $Force) -and (Test-Path $PidFile)) {
    $old = (Get-Content $PidFile -ErrorAction SilentlyContinue | Select-Object -First 1)
    if ($old -and (Get-Process -Id $old -ErrorAction SilentlyContinue)) {
        Log "already running (pid=$old), nothing to do"
        exit 0
    }
}

$forwards = @(
    @{ L = 5432;  R = 5432 },
    @{ L = 6379;  R = 6379 },
    @{ L = 3000;  R = 3000 },
    @{ L = 8088;  R = 8088 },
    @{ L = 11800; R = 11800 }
)

for ($attempt = 1; $attempt -le 3; $attempt++) {
    $args = @("-N", "-T", "-i", "`"$k`"", "-p", $p,
        "-o", "BatchMode=yes",
        "-o", "ExitOnForwardFailure=yes",
        "-o", "ServerAliveInterval=30",
        "-o", "ServerAliveCountMax=3",
        "-o", "StrictHostKeyChecking=accept-new")
    foreach ($f in $forwards) { $args += @("-L", "$($f.L):127.0.0.1:$($f.R)") }
    $args += "$u@$h"

    $proc = Start-Process -FilePath "ssh" -ArgumentList $args -PassThru -WindowStyle Hidden
    $proc.Id | Set-Content -Path $PidFile -Encoding ASCII
    Log "started attempt=$attempt pid=$($proc.Id)"
    Start-Sleep -Seconds 5

    # 探活：5432（PG）与 3000（Langfuse HTTP）
    $pgOk = $false
    try {
        $c = New-Object System.Net.Sockets.TcpClient
        $pgOk = $c.ConnectAsync("127.0.0.1", 5432).Wait(1500)
        $c.Close()
    } catch { }
    $lfOk = $false
    try {
        $r = Invoke-WebRequest -Uri "http://127.0.0.1:3000/api/public/health" -TimeoutSec 5 -UseBasicParsing -ErrorAction Stop
        $lfOk = ($r.StatusCode -eq 200)
    } catch { }

    if ($pgOk -and $lfOk) {
        Log "OK  tunnel healthy (pg=$pgOk langfuse=$lfOk)"
        exit 0
    }
    Log "WARN tunnel unhealthy (pg=$pgOk langfuse=$lfOk), killing pid=$($proc.Id)"
    Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 3
}
Log "ERROR: 3 次尝试均失败"
exit 1
