# ============================================================================
# 把 SSH 隧道注册为 Windows 计划任务（开机/登录后自动常驻）
# ----------------------------------------------------------------------------
# 用法：
#   注册： powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\register-tunnel-task.ps1
#   卸载： powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\register-tunnel-task.ps1 -Unregister
#   查看： schtasks /Query /TN "ai-code-cloud-tunnel" /V /FO LIST
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
