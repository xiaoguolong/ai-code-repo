# =============================================================================
# 应用一键部署（Week 22，jar + systemd 过渡形态）
# -----------------------------------------------------------------------------
# 做什么：构建 → （可选跑全量测试）→ 上传 jar 与远端脚本 → 远端备份/切换/重启/健康检查
#         失败时远端自动回滚到 app.jar.prev。
#
# 前置（未满足就先别跑，见 docs/deploy/week-22-app-deploy.md）：
#   1) 云端已装 JDK 17、已建运行用户（默认 aicode）、已放好 systemd 单元；
#   2) server.conf 可连（remote.ps1 -Test 通过）；
#   3) /opt/ai-code/app/<target>/ 已存在且 .env 由 write-app-env.sh 生成。
#
# 用法：
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\deploy-app.ps1 -Target platform -DryRun
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\deploy-app.ps1 -Target platform
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\deploy-app.ps1 -Target platform -SkipTests
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\deploy-app.ps1 -Target platform -Rollback
# =============================================================================
param(
    [ValidateSet("platform", "gateway")]
    [string]$Target = "platform",
    [switch]$SkipTests,
    [switch]$Rollback,
    [switch]$DryRun
)

$ErrorActionPreference = "Continue"

$Repo = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Remote = Join-Path $Repo "remote.ps1"
$Maven = Join-Path $Repo "run-maven-jdk17.ps1"
$DeployScript = Join-Path $Repo "deploy\app\remote-deploy.sh"

$Module = switch ($Target) {
    "platform" { "apps/spring-ai-alibaba-agent" }
    "gateway"  { "apps/agent-gateway" }
}
$JarName = switch ($Target) {
    "platform" { "spring-ai-alibaba-agent-*.jar" }
    "gateway"  { "agent-gateway-*.jar" }
}
$RemoteDir = "/opt/ai-code/app/$Target"

function Invoke-Step {
    param([string]$Label, [scriptblock]$Action)
    Write-Host ""
    Write-Host "=== $Label ===" -ForegroundColor Cyan
    if ($DryRun) {
        Write-Host "[dry-run] 跳过执行" -ForegroundColor Yellow
        return
    }
    & $Action
    if ($LASTEXITCODE -ne 0 -and $null -ne $LASTEXITCODE) {
        throw "步骤失败：$Label (exit=$LASTEXITCODE)"
    }
}

Write-Host "目标=$Target  模块=$Module  回滚=$Rollback  试运行=$DryRun"

if ($Rollback) {
    Invoke-Step "远端回滚到 app.jar.prev" {
        & powershell -NoProfile -ExecutionPolicy Bypass -File $Remote `
            -Cmd "sudo bash /opt/ai-code/app/remote-deploy.sh $Target --rollback"
    }
    exit $LASTEXITCODE
}

if (-not $SkipTests) {
    Invoke-Step "全量测试（构建前门禁）" {
        & powershell -NoProfile -ExecutionPolicy Bypass -File $Maven test
    }
} else {
    Write-Host "跳过测试（-SkipTests）：仅建议在 CI 已跑过测试时使用" -ForegroundColor Yellow
}

Invoke-Step "打包（跳过测试，测试已在上一阶段跑过）" {
    & powershell -NoProfile -ExecutionPolicy Bypass -File $Maven -pl $Module -am -DskipTests package
}

$Jar = Get-ChildItem -Path (Join-Path $Repo "$Module\target") -Filter $JarName |
    Where-Object { $_.Name -notmatch "sources|javadoc" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $Jar) {
    if ($DryRun) {
        Write-Host "[dry-run] 尚未构建产物（正式执行时会先 package）" -ForegroundColor Yellow
    } else {
        throw "未找到产物：$Module\target\$JarName（打包步骤应已生成）"
    }
} else {
    Write-Host ("产物：" + $Jar.FullName + " (" + [math]::Round($Jar.Length / 1MB, 1) + " MB)")
}

Invoke-Step "上传 jar 与远端部署脚本" {
    & powershell -NoProfile -ExecutionPolicy Bypass -File $Remote -Put "$($Jar.FullName) $RemoteDir/app.jar.new"
    if ($LASTEXITCODE -ne 0) { throw "上传 jar 失败 (exit=$LASTEXITCODE)" }
    & powershell -NoProfile -ExecutionPolicy Bypass -File $Remote -Put "$DeployScript /opt/ai-code/app/remote-deploy.sh"
    if ($LASTEXITCODE -ne 0) { throw "上传部署脚本失败 (exit=$LASTEXITCODE)" }
}

Invoke-Step "远端切换 + 健康检查（失败自动回滚）" {
    # 注意：-Cmd 只传「简单命令」，不含内嵌引号——这是第 20 周踩过的坑，复杂逻辑都在 .sh 里
    & powershell -NoProfile -ExecutionPolicy Bypass -File $Remote `
        -Cmd "sudo bash /opt/ai-code/app/remote-deploy.sh $Target"
    if ($LASTEXITCODE -ne 0) { throw "远端部署失败（已尝试自动回滚），请查看 journalctl -u ai-code-$Target -n 200" }
}

Write-Host ""
Write-Host "部署完成。建议验收：" -ForegroundColor Green
$Port = if ($Target -eq "platform") { 8084 } else { 8080 }
Write-Host "  curl -s http://127.0.0.1:$Port/actuator/health"
if ($Target -eq "platform") {
    Write-Host "  登录 + 跑一次 patient-analysis（见 docs/deploy/week-22-app-deploy.md 第 5 节）"
}
