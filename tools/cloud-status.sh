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
echo "=== 监控栈（Week 20）==="
docker ps --format '{{.Names}}|{{.Status}}' 2>/dev/null | grep -E '^(prometheus|grafana|alertmanager)\|' || echo "  未启动"
printf '  prometheus /-/ready : %s\n' "$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://127.0.0.1:19090/-/ready)"
printf '  grafana /api/health  : %s\n' "$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://127.0.0.1:3001/api/health)"
printf '  指标反向隧道 18084   : %s\n' "$(ss -lnt 2>/dev/null | grep -q '127.0.0.1:18084' && echo LISTEN || echo DOWN)"
printf '  指标反向隧道 18080   : %s\n' "$(ss -lnt 2>/dev/null | grep -q '127.0.0.1:18080' && echo LISTEN || echo DOWN)"
echo
echo "=== ubuntu 可用的公钥数量 ==="
wc -l < /home/ubuntu/.ssh/authorized_keys 2>/dev/null || echo 0
