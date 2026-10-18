# ============================================================================
# 本地 → 云上中间件的 SSH 隧道（免改服务器绑定、免开安全组）
# ----------------------------------------------------------------------------
# 原理：ssh -N -L 把本地端口转发到服务器回环上的服务。
#       对本地应用来说，连 127.0.0.1:5432 就等于连云端 PG；云端仍只绑 127.0.0.1。
#
# 用法：
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\tunnel.ps1          # 启动
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\tunnel.ps1 -Status  # 查看
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\tunnel.ps1 -Stop    # 停止
#
# 映射表：
#   本地 5432  -> 云端 PostgreSQL 16
#   本地 6379  -> 云端 Redis 7.2
#   本地 3000  -> 云端 Langfuse
#   本地 8088  -> 云端 SkyWalking UI
#   本地 11800 -> 云端 SkyWalking OAP gRPC（本地应用挂 Agent 时用）
# ============================================================================
param(
    [switch]$Stop,
    [switch]$Status
)

$ErrorActionPreference = "Continue"
$Repo = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Conf = Join-Path $Repo "server.conf"
$PidFile = Join-Path $Repo ".ssh-local\tunnel.pid"

if (-not (Test-Path $Conf)) { throw "缺少 server.conf" }
$T = @{}
Get-Content $Conf -Encoding UTF8 | ForEach-Object {
    $l = $_.Trim()
    if ($l -eq "" -or $l.StartsWith("#")) { return }
    $i = $l.IndexOf("="); if ($i -lt 1) { return }
    $T[$l.Substring(0, $i).Trim()] = $l.Substring($i + 1).Trim()
}
$HostIp = $T["HOST"]; $Port = $T["PORT"]; $User = $T["USER"]
$KeyPath = $T["KEY_PATH"]
if (-not [System.IO.Path]::IsPathRooted($KeyPath)) { $KeyPath = Join-Path $Repo $KeyPath }

$Forwards = @(
    @{ L = 5432;  R = 5432;  Name = "PostgreSQL" },
    @{ L = 6379;  R = 6379;  Name = "Redis" },
    @{ L = 3000;  R = 3000;  Name = "Langfuse" },
    @{ L = 8088;  R = 8088;  Name = "SkyWalking UI" },
    @{ L = 11800; R = 11800; Name = "SkyWalking OAP gRPC" }
)

function Probe-Pg([int]$port) {
    # PostgreSQL：发 SSLRequest（8 字节固定报文），正常会回 'S'（支持 SSL）或 'N'
    try {
        $c = New-Object System.Net.Sockets.TcpClient
        if (-not $c.ConnectAsync("127.0.0.1", $port).Wait(1500)) { return "DOWN" }
        $c.ReceiveTimeout = 2000
        $req = [byte[]](0,0,0,8,4,210,22,47)
        $s = $c.GetStream(); $s.Write($req, 0, $req.Length); $s.Flush()
        $buf = New-Object byte[] 1
        $n = $s.Read($buf, 0, 1)
        $c.Close()
        if ($n -eq 1 -and ($buf[0] -eq 83 -or $buf[0] -eq 78)) { return "OK (PostgreSQL handshake)" }
        return "REFUSED"
    } catch { return "DOWN" }
}

function Probe-Redis([int]$port) {
    # Redis：未带密码 PING 会回 -NOAUTH，正好证明端到端可达
    try {
        $c = New-Object System.Net.Sockets.TcpClient
        if (-not $c.ConnectAsync("127.0.0.1", $port).Wait(1500)) { return "DOWN" }
        $c.ReceiveTimeout = 2000
        $s = $c.GetStream()
        $bytes = [System.Text.Encoding]::ASCII.GetBytes("PING`r`n")
        $s.Write($bytes, 0, $bytes.Length); $s.Flush()
        $buf = New-Object byte[] 256
        $n = $s.Read($buf, 0, 256)
        $c.Close()
        $txt = [System.Text.Encoding]::ASCII.GetString($buf, 0, [Math]::Max($n, 0))
        if ($txt -match 'NOAUTH') { return "OK (Redis, auth required)" }
        if ($txt -match '\+PONG') { return "OK (Redis)" }
        return "REFUSED"
    } catch { return "DOWN" }
}

function Probe-Http([int]$port, [string]$path) {
    try {
        $r = Invoke-WebRequest -Uri "http://127.0.0.1:$port$path" -TimeoutSec 4 -UseBasicParsing -ErrorAction Stop
        return "OK (HTTP $($r.StatusCode))"
    } catch {
        if ($_.Exception.Response) { return "OK (HTTP $([int]$_.Exception.Response.StatusCode))" }
        return "DOWN"
    }
}

function Probe-Tcp([int]$port) {
    try {
        $c = New-Object System.Net.Sockets.TcpClient
        $ok = $c.ConnectAsync("127.0.0.1", $port).Wait(1200)
        $c.Close()
        if ($ok) { return "LISTEN (上层协议未验证)" } else { return "DOWN" }
    } catch { return "DOWN" }
}

function Show-Status {
    Write-Host "`n=== 隧道端到端探测（不只看本地监听）===" -ForegroundColor Cyan
    $rows = @(
        @{ P = 5432;  N = "PostgreSQL";       R = (Probe-Pg 5432) },
        @{ P = 6379;  N = "Redis";            R = (Probe-Redis 6379) },
        @{ P = 3000;  N = "Langfuse";         R = (Probe-Http 3000 "/api/public/health") },
        @{ P = 8088;  N = "SkyWalking UI";    R = (Probe-Http 8088 "/") },
        @{ P = 11800; N = "OAP gRPC";         R = (Probe-Tcp 11800) },
        @{ P = 8084;  N = "platform(未部署)"; R = (Probe-Http 8084 "/actuator/health") }
    )
    foreach ($r in $rows) { "  127.0.0.1:{0,-6} {1,-20} {2}" -f $r.P, $r.N, $r.R }
    if (Test-Path $PidFile) {
        $tp = Get-Content $PidFile -ErrorAction SilentlyContinue
        $alive = [bool](Get-Process -Id $tp -ErrorAction SilentlyContinue)
        "`n隧道进程 PID=$tp 存活=$alive"
    } else { "`n没有 PID 记录（可能未启动）" }
}

if ($Status) { Show-Status; exit 0 }

if ($Stop) {
    if (Test-Path $PidFile) {
        $tp = Get-Content $PidFile
        Stop-Process -Id $tp -Force -ErrorAction SilentlyContinue
        Remove-Item $PidFile -Force
        Write-Host "已停止隧道进程 PID=$tp" -ForegroundColor Yellow
    } else { Write-Host "没有 PID 记录" -ForegroundColor Yellow }
    # 兜底：清掉命令行里带本机转发参数的 ssh
    Get-CimInstance Win32_Process -Filter "Name='ssh.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -match 'L 5432:127\.0\.0\.1:5432' } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
    exit 0
}

# 端口占用预检（本地已有 PG/Redis 时会冲突）
$busy = @()
foreach ($f in $Forwards) {
    $conn = Get-NetTCPConnection -LocalPort $f.L -State Listen -ErrorAction SilentlyContinue
    if ($conn) { $busy += "$($f.L) ($($f.Name))" }
}
if ($busy) {
    Write-Host "以下本地端口已被占用，先停掉占用者再开隧道：" -ForegroundColor Red
    $busy | ForEach-Object { "  $_" }
    exit 1
}

$args = @("-N", "-T",
    "-i", "`"$KeyPath`"",
    "-p", $Port,
    "-o", "BatchMode=yes",
    "-o", "ExitOnForwardFailure=yes",
    "-o", "ServerAliveInterval=30",
    "-o", "ServerAliveCountMax=3",
    "-o", "StrictHostKeyChecking=accept-new")
foreach ($f in $Forwards) { $args += @("-L", "$($f.L):127.0.0.1:$($f.R)") }
$args += "$User@$HostIp"

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $PidFile) | Out-Null
$p = Start-Process -FilePath "ssh" -ArgumentList $args -PassThru -WindowStyle Hidden
$p.Id | Set-Content $PidFile

Write-Host "隧道已启动 PID=$($p.Id)，等待握手..." -ForegroundColor Green
Start-Sleep -Seconds 4
Show-Status
Write-Host "`n提示：本地应用把地址写成 127.0.0.1 即可；关闭时执行 -Stop（或直接杀 PID）。" -ForegroundColor Cyan
