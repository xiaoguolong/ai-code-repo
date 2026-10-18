# 本地应用启动后的全链路验收（等待 8084 → 逐项验证）
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\verify-local-app.ps1
$ErrorActionPreference = "Continue"
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }

$Repo = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$envFile = Join-Path $Repo "apps\spring-ai-alibaba-agent\.env"
$E = @{}
if (Test-Path $envFile) {
    Get-Content $envFile -Encoding UTF8 | ForEach-Object {
        $l = $_.Trim()
        if ($l -eq "" -or $l.StartsWith("#")) { return }
        $i = $l.IndexOf("="); if ($i -lt 1) { return }
        $E[$l.Substring(0, $i).Trim()] = $l.Substring($i + 1).Trim()
    }
}

Write-Host "=== 等待 8084 就绪（最多 4 分钟）===" -ForegroundColor Cyan
$ready = $false
for ($i = 0; $i -lt 80; $i++) {
    try {
        $r = Invoke-WebRequest -Uri "http://localhost:8084/actuator/health" -TimeoutSec 3 -UseBasicParsing -ErrorAction Stop
        if ($r.StatusCode -eq 200) { $ready = $true; break }
    } catch { }
    Start-Sleep -Seconds 3
    if ($i % 10 -eq 9) { Write-Host ("  等待中... {0}s" -f (($i + 1) * 3)) }
}
if (-not $ready) { Write-Host "4 分钟内 8084 未就绪 —— 应用可能启动失败，请看 IDEA 控制台" -ForegroundColor Red; exit 1 }
Write-Host "应用已就绪`n" -ForegroundColor Green

$ok = 0; $fail = 0
function Check($name, $cond, $detail) {
    if ($cond) { Write-Host ("  [PASS] {0}  {1}" -f $name, $detail) -ForegroundColor Green; $script:ok++ }
    else { Write-Host ("  [FAIL] {0}  {1}" -f $name, $detail) -ForegroundColor Red; $script:fail++ }
}

Write-Host "=== 1) 应用与配置 ===" -ForegroundColor Cyan
try {
    $h = Invoke-RestMethod -Uri "http://localhost:8084/actuator/health" -TimeoutSec 5
    Check "health" ($h.status -eq "UP") $h.status
} catch { Check "health" $false $_.Exception.Message }

Write-Host "`n=== 2) 登录（走隧道连云端 PG）===" -ForegroundColor Cyan
$token = $null
# health UP 不等于初始化完成：RBAC 种子由 ApplicationRunner 在启动末尾写入，
# 这里必须重试，否则会拿到 401（实测踩过）。
$body = @{ username = "admin"; password = "admin123" } | ConvertTo-Json
for ($try = 1; $try -le 12; $try++) {
    try {
        $login = Invoke-RestMethod -Uri "http://localhost:8084/api/v1/platform/auth/login" -Method Post -ContentType "application/json" -Body $body -TimeoutSec 20
        if ($login.code -eq "SUCCESS" -and $login.data.token) {
            $token = $login.data.token
            Check "登录" $true "第 $try 次成功  traceId=$($login.traceId)"
            break
        }
    } catch { Start-Sleep -Seconds 3 }
}
if (-not $token) { Check "登录" $false "重试 12 次仍失败"; Write-Host "  后续项跳过" -ForegroundColor Yellow }

if ($token) {
    $H = @{ Authorization = "Bearer $token"; "X-Trace-Id" = "verify-local-$(Get-Random)" }

    Write-Host "`n=== 3) 链路上下文 / SkyWalking 自检 ===" -ForegroundColor Cyan
    try {
        $tc = Invoke-RestMethod -Uri "http://localhost:8084/api/v1/platform/observability/trace-context" -Headers $H -TimeoutSec 15
        Check "trace-context" ($tc.code -eq "SUCCESS") "traceId=$($tc.data.traceId)"
    } catch { Check "trace-context" $false $_.Exception.Message }
    try {
        $sw = Invoke-RestMethod -Uri "http://localhost:8084/api/v1/platform/observability/skywalking-status" -Headers $H -TimeoutSec 15
        Check "skywalking-status" ($sw.code -eq "SUCCESS") "enabled=$($sw.data.enabled) bridgeAvailable=$($sw.data.bridgeAvailable)"
    } catch { Check "skywalking-status" $false $_.Exception.Message }

    Write-Host "`n=== 4) 业务接口（读云端 PG）===" -ForegroundColor Cyan
    try {
        $ag = Invoke-RestMethod -Uri "http://localhost:8084/api/v1/platform/agents" -Headers $H -TimeoutSec 20
        Check "agent 目录" ($ag.code -eq "SUCCESS") "count=$($ag.data.Count)"
    } catch { Check "agent 目录" $false $_.Exception.Message }
    try {
        $ex = Invoke-RestMethod -Uri "http://localhost:8084/api/v1/platform/executions" -Headers $H -TimeoutSec 20
        Check "执行记录查询" ($ex.code -eq "SUCCESS") "count=$($ex.data.Count)"
    } catch { Check "执行记录查询" $false $_.Exception.Message }

    Write-Host "`n=== 5) Redis（记忆/会话，走隧道）===" -ForegroundColor Cyan
    try {
        $me = Invoke-RestMethod -Uri "http://localhost:8084/api/v1/platform/auth/me" -Headers $H -TimeoutSec 15
        Check "auth/me" ($me.code -eq "SUCCESS") "user=$($me.data.username)"
    } catch { Check "auth/me" $false $_.Exception.Message }
}

Write-Host "`n=== 汇总：PASS=$ok  FAIL=$fail ===" -ForegroundColor Cyan
if ($fail -eq 0) { Write-Host "全链路通过 ✅" -ForegroundColor Green } else { Write-Host "有失败项，见上" -ForegroundColor Yellow }
