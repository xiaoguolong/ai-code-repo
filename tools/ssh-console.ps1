# ============================================================================
# 打开一个到云服务器的交互式 SSH 会话（读取 server.conf）
# ----------------------------------------------------------------------------
# 用法：
#   双击仓库根目录的 connect-cloud.cmd
#   或： powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\ssh-console.ps1
# ============================================================================
$ErrorActionPreference = "Continue"
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }

$Repo = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Conf = Join-Path $Repo "server.conf"
if (-not (Test-Path $Conf)) { Write-Host "缺少 server.conf" -ForegroundColor Red; exit 1 }

$T = @{}
Get-Content $Conf -Encoding UTF8 | ForEach-Object {
    $l = $_.Trim()
    if ($l -eq "" -or $l.StartsWith("#")) { return }
    $i = $l.IndexOf("="); if ($i -lt 1) { return }
    $T[$l.Substring(0, $i).Trim()] = $l.Substring($i + 1).Trim()
}

$h = $T["HOST"]; $u = $T["USER"]; $p = $T["PORT"]
$k = $T["KEY_PATH"]
if (-not $k) { $k = ".ssh-local\id_ed25519" }
if (-not [System.IO.Path]::IsPathRooted($k)) { $k = Join-Path $Repo $k }
if (-not (Test-Path $k)) { Write-Host "找不到私钥：$k" -ForegroundColor Red; exit 1 }
if (-not $h) { Write-Host "server.conf 里没有 HOST" -ForegroundColor Red; exit 1 }
if (-not $u) { $u = "ubuntu" }
if (-not $p) { $p = "22" }

Write-Host "连接 $u@$($h.Substring(0,3))***:$p （退出：输入 exit 或 Ctrl+D）" -ForegroundColor Cyan
& ssh -i $k -p $p -o StrictHostKeyChecking=accept-new -o ServerAliveInterval=30 "$u@$h"
