#!/usr/bin/env bash
# 云上环境总览
echo "=== 容器 ==="
docker ps --format '{{.Names}}|{{.Status}}' 2>/dev/null | sort | column -t -s'|'
echo
echo "=== 资源 ==="
free -m | awk '/Mem:/{print "mem : "$7" MB available of "$2" MB"}'
free -m | awk '/Swap:/{print "swap: "$2" MB"}'
df -h / | awk 'NR==2{print "disk: "$4" available"}'
echo
echo "=== 端口（仅公网可访问的）==="
ss -lntp 2>/dev/null | awk 'NR==1 || /0\.0\.0\.0:(22|3000|8088|8080|8084|11800|12800) /{print}' | head -8
echo
echo "=== 认证策略 ==="
sshd -T 2>/dev/null | grep -iE '^(passwordauthentication|permitrootlogin)' | sed 's/^/  /'
echo
echo "=== ubuntu 可用的公钥数量 ==="
wc -l < /home/ubuntu/.ssh/authorized_keys 2>/dev/null || echo 0
