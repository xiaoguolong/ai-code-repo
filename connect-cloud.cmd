@echo off
REM ============================================================================
REM 双击本文件 = 打开到腾讯云服务器的 SSH 会话（自动读 server.conf 与 .pem 私钥）
REM 不需要 Xshell、不需要密码。关闭窗口即断开。
REM ============================================================================
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\ssh-console.ps1"
echo.
pause
