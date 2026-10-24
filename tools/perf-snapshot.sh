#!/usr/bin/env bash
# CPU / 内存 / 容器占用快照（用于前后对比）
echo "=== 负载 ==="
uptime
echo
echo "=== 容器 CPU Top 6 ==="
docker stats --no-stream --format '{{.Name}}|{{.CPUPerc}}|{{.MemUsage}}' 2>/dev/null \
  | sort -t'|' -k2 -rn | head -6 | awk -F'|' '{printf "  %-28s CPU=%-10s MEM=%s\n", $1, $2, $3}'
echo
echo "=== 内存 / swap ==="
free -m | sed -n '1,3p' | sed 's/^/  /'
echo
echo "=== 磁盘 ==="
df -h / | tail -1 | sed 's/^/  /'
