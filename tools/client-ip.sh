#!/usr/bin/env bash
# 服务器视角的客户端来源 IP（安全组实际匹配的值）+ 最近认证记录
echo "=== 连到 22 端口的所有对端地址 ==="
ss -tn 2>/dev/null | awk '$4 ~ /:22$/ {split($5,a,":"); print a[1]}' | sort -u | sed 's/^/  /'

echo
echo "=== 最近 6 条 sshd 记录（看来源 IP 与结果）==="
grep -aE 'sshd' /var/log/auth.log 2>/dev/null | grep -aE 'Accepted|Invalid|Failed' | tail -6 \
  | sed -E 's/^([A-Z][a-z]{2} [0-9]+ [0-9:]+).*from ([0-9.]+) .*(Accepted|Invalid|Failed).*/  \1  from \2  \3/'
