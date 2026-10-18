# ============================================================================
# 本地启动平台应用（与 IDEA 的 "Platform (cloud)" 运行配置等价）
# ----------------------------------------------------------------------------
# 用途：不依赖 IDEA 时也能起应用做验收（例如本次补验 Langfuse trace）。
# 关键点：
#   · 工作目录必须是模块目录 —— application-jdbc.yml 的
#     spring.config.import: optional:file:.env[.properties] 才能读到模块 .env
#   · SkyWalking Agent 的 collector 指向 127.0.0.1:11800（隧道 → 云端 OAP）
#   · 用 target/classes + 依赖 classpath 启动，确保跑的是当前代码（不是旧 jar）
#
# 用法：
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\run-local-app.ps1
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\run-local-app.ps1 -Stop
# ============================================================================
param([switch]$Stop, [int]$WaitSeconds = 180)

$ErrorActionPreference = "Continue"
$Repo = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Module = Join-Path $Repo "apps\spring-ai-alibaba-agent"
$PidFile = Join-Path $Repo ".ssh-local\app.pid"
$LogFile = Join-Path $Repo ".ssh-local\app.log"
$Jdk = "E:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot"
$Agent = "E:\skywalking-agent\skywalking-agent.jar"

if ($Stop) {
    if (Test-Path $PidFile) {
        $ap = (Get-Content $PidFile | Select-Object -First 1)
        Stop-Process -Id $ap -Force -ErrorAction SilentlyContinue
        Remove-Item $PidFile -Force
        Write-Host "已停止应用 PID=$ap" -ForegroundColor Yellow
    } else { Write-Host "没有应用 PID 记录" -ForegroundColor Yellow }
    exit 0
}

$port = Get-NetTCPConnection -LocalPort 8084 -State Listen -ErrorAction SilentlyContinue
if ($port) { Write-Host "8084 已被占用（PID $($port.OwningProcess)），先停止它或改用 IDEA" -ForegroundColor Red; exit 1 }

$cpFile = Join-Path $Module "target\cp.txt"
if (-not (Test-Path $cpFile)) { throw "缺少 $cpFile，先跑：mvn -pl apps/spring-ai-alibaba-agent dependency:build-classpath -Dmdep.outputFile=target\cp.txt" }

$deps = (Get-Content $cpFile -Raw).Trim()
$classpath = "$(Join-Path $Module 'target\classes');$(Join-Path $Repo 'libs\ai-core\target\classes');$deps"

$jvmArgs = @(
    "-javaagent:$Agent",
    "-Dskywalking.agent.service_name=ai-code-platform",
    "-Dskywalking.agent.instance_name=platform-local",
    "-Dskywalking.collector.backend_service=127.0.0.1:11800",
    "-Dfile.encoding=UTF-8",
    "-cp", $classpath,
    "com.aicode.framework.SpringAiAlibabaAgentApplication"
)

Remove-Item $LogFile -Force -ErrorAction SilentlyContinue
Write-Host "启动应用（工作目录=$Module）..." -ForegroundColor Cyan
$proc = Start-Process -FilePath "$Jdk\bin\java.exe" -ArgumentList $jvmArgs -WorkingDirectory $Module `
    -PassThru -WindowStyle Hidden -RedirectStandardOutput $LogFile -RedirectStandardError "$LogFile.err"
$proc.Id | Set-Content $PidFile
Write-Host "PID=$($proc.Id)，日志：$LogFile"

Write-Host "等待就绪（最多 ${WaitSeconds}s）..."
$ok = $false
for ($i = 0; $i -lt [Math]::Ceiling($WaitSeconds / 3); $i++) {
    Start-Sleep -Seconds 3
    try {
        $r = Invoke-WebRequest "http://localhost:8084/actuator/health" -TimeoutSec 3 -UseBasicParsing -ErrorAction Stop
        if ($r.StatusCode -eq 200) { $ok = $true; break }
    } catch { }
}
if ($ok) { Write-Host "应用已就绪 ✅" -ForegroundColor Green }
else {
    Write-Host "未在 ${WaitSeconds}s 内就绪，日志尾部：" -ForegroundColor Red
    Get-Content $LogFile -Tail 20 -ErrorAction SilentlyContinue
    exit 1
}
