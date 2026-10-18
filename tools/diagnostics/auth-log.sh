#!/usr/bin/env bash
# 查看 ssdh 最近的认证记录（用于判断 Xshell 到底被什么原因拒绝）
LOG=/var/log/auth.log
echo "=== 最近 20 条 sshd 认证相关记录 ==="
grep -aE 'sshd' "$LOG" 2>/dev/null | grep -aE 'Failed|Accepted|error|Invalid|Connection closed' | tail -20
echo
echo "=== 其中与公钥指纹相关的行 ==="
grep -aE 'sshd' "$LOG" 2>/dev/null | grep -aE 'SHA256|MD5' | tail -10
