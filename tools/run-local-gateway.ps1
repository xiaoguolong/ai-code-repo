# ============================================================================
# 本地启动网关应用（8080）—— Week 20 验收用
# ----------------------------------------------------------------------------
# 与 tools\run-local-app.ps1 同构：
#   · 工作目录是模块目录（才能读到 gateway 的 .env）
#   · 可选挂 SkyWalking Agent（metrics 验收不需要，但保持与生产同形）
#   · 用 target/classes + 依赖 classpath 启动当前代码
#
# 用法：
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\run-local-gateway.ps1
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\run-local-gateway.ps1 -Stop
# ============================================================================
param([switch]$Stop, [int]$WaitSeconds = 120)

$ErrorActionPreference = "Continue"
$Repo = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Module = Join-Path $Repo "apps\agent-gateway"
$PidFile = Join-Path $Repo ".ssh-local\gateway.pid"
$LogFile = Join-Path $Repo ".ssh-local\gateway.log"
$Jdk = "E:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot"
$Agent = "E:\skywalking-agent\skywalking-agent.jar"

if ($Stop) {
    if (Test-Path $PidFile) {
        $gp = (Get-Content $PidFile | Select-Object -First 1)
        Stop-Process -Id $gp -Force -ErrorAction SilentlyContinue
        Remove-Item $PidFile -Force
        Write-Host "已停止网关 PID=$gp" -ForegroundColor Yellow
    } else { Write-Host "没有网关 PID 记录" -ForegroundColor Yellow }
    exit 0
}

$port = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($port) { Write-Host "8080 已被占用（PID $($port.OwningProcess)）" -ForegroundColor Red; exit 1 }

$cpFile = Join-Path $Module "target\cp.txt"
if (-not (Test-Path $cpFile)) {
    throw "缺少 $cpFile，先跑：.\run-maven-jdk17.ps1 -pl apps/agent-gateway dependency:build-classpath -Dmdep.outputFile=target\cp.txt"
}

$deps = (Get-Content $cpFile -Raw).Trim()
$classpath = "$(Join-Path $Module 'target\classes');$(Join-Path $Repo 'libs\ai-core\target\classes');$deps"

$jvmArgs = @(
    "-javaagent:$Agent",
    "-Dskywalking.agent.service_name=ai-code-gateway",
    "-Dskywalking.agent.instance_name=gateway-local",
    "-Dskywalking.collector.backend_service=127.0.0.1:11800",
    "-Dfile.encoding=UTF-8",
    "-cp", $classpath,
    "com.aicode.gateway.GatewayApplication"
)

Remove-Item $LogFile -Force -ErrorAction SilentlyContinue
Write-Host "启动网关（工作目录=$Module）..." -ForegroundColor Cyan
$proc = Start-Process -FilePath "$Jdk\bin\java.exe" -ArgumentList $jvmArgs -WorkingDirectory $Module `
    -PassThru -WindowStyle Hidden -RedirectStandardOutput $LogFile -RedirectStandardError "$LogFile.err"
$proc.Id | Set-Content $PidFile
Write-Host "PID=$($proc.Id)，日志：$LogFile"

Write-Host "等待就绪（最多 ${WaitSeconds}s）..."
$ok = $false
for ($i = 0; $i -lt [Math]::Ceiling($WaitSeconds / 3); $i++) {
    Start-Sleep -Seconds 3
    try {
        $r = Invoke-WebRequest "http://localhost:8080/actuator/health" -TimeoutSec 3 -UseBasicParsing -ErrorAction Stop
        if ($r.StatusCode -eq 200) { $ok = $true; break }
    } catch { }
}
if ($ok) { Write-Host "网关已就绪 ✅" -ForegroundColor Green }
else {
    Write-Host "未在 ${WaitSeconds}s 内就绪，日志尾部：" -ForegroundColor Red
    Get-Content $LogFile -Tail 20 -ErrorAction SilentlyContinue
    exit 1
}
