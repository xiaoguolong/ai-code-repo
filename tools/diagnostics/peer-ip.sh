#!/usr/bin/env bash
# 从内核连接表看：谁在连我的 22 端口（= 安全组匹配的源地址）
echo "=== 连到 22 端口的对端地址 ==="
ss -tn state established '( sport = :22 )' 2>/dev/null | awk 'NR>1 {print "  " $5}' | sed 's/:22$//' | sort -u

echo
echo "=== 当前 ssh 相关进程的连接（含 sudo 外层）==="
ss -tnp 2>/dev/null | grep -E ':22\s' | awk '{print "  local="$4"  peer="$5}' | sort -u | head -10

echo
echo "=== 服务器公网 IP ==="
curl -s --max-time 8 https://ipinfo.io/ip || echo n/a
