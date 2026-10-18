# ============================================================================
# 用 WMI 启动本地应用（完全脱离当前 shell / 作业的进程树）
# ----------------------------------------------------------------------------
# 为什么不用 Start-Process：它创建的进程仍挂在发起者的进程树下，
# 一旦外层作业被终止，java 会被一起杀掉（本次实测踩过）。
# Win32_Process.Create 由 WMI 服务托管，父进程是 svchost，与调用者无关。
# ============================================================================
param(
    [switch]$Stop,
    [ValidateSet("platform", "gateway")]
    [string]$Target = "platform"
)

$ErrorActionPreference = "Continue"
$Repo = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
if ($Target -eq "gateway") {
    $Module = Join-Path $Repo "apps\agent-gateway"
    $PidFile = Join-Path $Repo ".ssh-local\gateway.pid"
    $LogFile = Join-Path $Repo ".ssh-local\gateway.log"
    $MainClass = "com.aicode.gateway.GatewayApplication"
    $ServiceName = "ai-code-gateway"
    $InstanceName = "gateway-local"
    $Port = 8080
} else {
    $Module = Join-Path $Repo "apps\spring-ai-alibaba-agent"
    $PidFile = Join-Path $Repo ".ssh-local\app.pid"
    $LogFile = Join-Path $Repo ".ssh-local\app.log"
    $MainClass = "com.aicode.framework.SpringAiAlibabaAgentApplication"
    $ServiceName = "ai-code-platform"
    $InstanceName = "platform-local"
    $Port = 8084
}
$Jdk = "E:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot"
$Agent = "E:\skywalking-agent\skywalking-agent.jar"

if ($Stop) {
    $ap = (Get-Content $PidFile -ErrorAction SilentlyContinue | Select-Object -First 1)
    if ($ap) { Stop-Process -Id $ap -Force -ErrorAction SilentlyContinue; Remove-Item $PidFile -Force; Write-Host "已停止 $Target PID=$ap" }
    else { Write-Host "无 PID 记录（$Target）" }
    exit 0
}

$portInfo = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if ($portInfo) { Write-Host "$Port 已被占用 PID $($portInfo.OwningProcess)" -ForegroundColor Red; exit 1 }

$deps = (Get-Content (Join-Path $Module "target\cp.txt") -Raw).Trim()
$cp = "$(Join-Path $Module 'target\classes');$(Join-Path $Repo 'libs\ai-core\target\classes');$deps"

# 注意：Win32_Process.Create 的 CommandLine 由 CreateProcess 直接解析，
# 含空格的路径必须自己加引号。
$cmdLine = '"' + "$Jdk\bin\java.exe" + '"' +
    " -javaagent:`"$Agent`"" +
    " -Dskywalking.agent.service_name=$ServiceName" +
    " -Dskywalking.agent.instance_name=$InstanceName" +
    " -Dskywalking.collector.backend_service=127.0.0.1:11800" +
    " -Dfile.encoding=UTF-8" +
    " -cp `"$cp`"" +
    " $MainClass" +
    " > `"$LogFile`" 2>&1"

Write-Host "通过 WMI 启动 $Target（工作目录=$Module）..."
$r = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{
    CommandLine      = $cmdLine
    CurrentDirectory = $Module
}
if ($r.ReturnValue -ne 0) { Write-Host "创建失败，ReturnValue=$($r.ReturnValue)" -ForegroundColor Red; exit 1 }
$r.ProcessId | Set-Content $PidFile
Write-Host "PID=$($r.ProcessId)（父进程为 WMI 服务，不受当前作业影响）"

Write-Host "等待就绪（最多 180s）..."
$ok = $false
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 3
    try {
        $h = Invoke-WebRequest "http://localhost:$Port/actuator/health" -TimeoutSec 8 -UseBasicParsing -ErrorAction Stop
        if ($h.StatusCode -eq 200) { $ok = $true; break }
    } catch { }
}
if ($ok) { Write-Host "应用已就绪 ✅" -ForegroundColor Green; exit 0 }
else {
    Write-Host "未就绪，日志尾部：" -ForegroundColor Red
    Get-Content $LogFile -Tail 15 -ErrorAction SilentlyContinue
    exit 1
}
