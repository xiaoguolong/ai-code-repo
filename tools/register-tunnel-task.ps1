# ============================================================================
# 把 SSH 隧道注册为 Windows 计划任务（开机/登录后自动常驻）
# ----------------------------------------------------------------------------
# 用法：
#   注册： powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\register-tunnel-task.ps1
#   卸载： powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\register-tunnel-task.ps1 -Unregister
#   查看： schtasks /Query /TN "ai-code-cloud-tunnel" /V /FO LIST`r`n#   看门狗：每 5 分钟自愈（手机热点 IP 变更会让「进程活着但转发全死」，只靠登录触发不够）
#
# 说明：
#   · 任务运行在**当前用户**下，登录时触发，不需要管理员权限
#   · 不弹窗口（-WindowStyle Hidden），日志写在 .ssh-local\tunnel.log
#   · 脚本本身幂等：重复触发时若隧道已存活则直接退出
# ============================================================================
param([switch]$Unregister)

$ErrorActionPreference = "Stop"
$TaskName = "ai-code-cloud-tunnel"
$Repo = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Daemon = Join-Path $Repo "tools\tunnel-daemon.ps1"

if ($Unregister) {
    $t = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
    if ($t) {
Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
        Write-Host "[+] 已删除计划任务：$TaskName" -ForegroundColor Green
        & schtasks /Delete /TN "$TaskName-watchdog" /F 2>&1 | Out-Null
        if ($LASTEXITCODE -eq 0) { Write-Host "[+] 已删除看门狗：$TaskName-watchdog" -ForegroundColor Green }
    } else {
        Write-Host "[i] 计划任务不存在：$TaskName" -ForegroundColor Yellow
    }
    exit 0
}

if (-not (Test-Path $Daemon)) { throw "找不到守护脚本：$Daemon" }

$action = New-ScheduledTaskAction -Execute "powershell.exe" `
    -Argument "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$Daemon`""

# 登录后触发（比「开机」更合适：需要用户环境与私钥路径）
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME

# 断网/省电时也照常启动；错过触发时间则补跑；允许按需手动运行
$settings = New-ScheduledTaskSettingsSet `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -StartWhenAvailable `
    -ExecutionTimeLimit (New-TimeSpan -Hours 0) `
    -MultipleInstances IgnoreNew

$principal = New-ScheduledTaskPrincipal -UserId $env:USERNAME -LogonType Interactive -RunLevel Limited

Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger `
    -Settings $settings -Principal $principal `
    -Description "SSH tunnel: local 5432/6379/3000/8088/11800 -> Tencent Cloud middleware" `
    -Force | Out-Null

Write-Host "[+] 已注册计划任务：$TaskName" -ForegroundColor Green
Write-Host "    触发：用户 $env:USERNAME 登录时"
Write-Host "    动作：powershell -File `"$Daemon`""
Write-Host "    日志：$Repo\.ssh-local\tunnel.log"

# ── 看门狗：每 5 分钟跑一次守护脚本 ──
# 为什么需要：手机热点/宽带重播会让公网 IP 变更，旧 ssh 连接可能「进程存活但转发全死」。
# 守护脚本已改为「先探活再不健康就重启」，所以定时调用即可自愈。
$WatchName = "$TaskName-watchdog"
$WatchCmd = "powershell.exe -NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File $Daemon"
& schtasks /Create /TN $WatchName /TR $WatchCmd /SC MINUTE /MO 5 /IT /F 2>&1 | Out-Null
if ($LASTEXITCODE -eq 0) {
    Write-Host "[+] 已创建看门狗：$WatchName（每 5 分钟自愈）" -ForegroundColor Green
} else {
    Write-Warning "看门狗创建失败（exit=$LASTEXITCODE），可手动执行：schtasks /Create /TN $WatchName ..."
}

Write-Host "`n=== 立即跑一次验证 ===" -ForegroundColor Cyan
Start-ScheduledTask -TaskName $TaskName
Start-Sleep -Seconds 8
$info = Get-ScheduledTaskInfo -TaskName $TaskName
"上次运行时间: $($info.LastRunTime)"
"上次结果    : $($info.LastTaskResult)  (0 = 成功)"
if (Test-Path (Join-Path $Repo ".ssh-local\tunnel.log")) {
    Write-Host "`n--- 日志尾部 ---"
    Get-Content (Join-Path $Repo ".ssh-local\tunnel.log") -Tail 5
}
