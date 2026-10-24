#!/usr/bin/env bash
# 服务器 CPU 告警排查
echo "=== 1) 负载与运行时长 ==="
uptime
echo "CPU 核数: $(nproc)"
echo "当前时间: $(date '+%F %T %Z')"

echo
echo "=== 2) CPU 占用 Top 10 进程 ==="
ps -eo pid,ppid,pcpu,pmem,etime,comm,args --sort=-pcpu 2>/dev/null | head -11 \
  | cut -c1-160 | sed 's/^/  /'

echo
echo "=== 3) 容器 CPU 实时占用（按 CPU 排序）==="
docker stats --no-stream --format 'table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.PIDs}}' 2>/dev/null | head -14

echo
echo "=== 4) 内存与 swap ==="
free -m | sed 's/^/  /'

echo
echo "=== 5) 最近是否有 OOM ==="
dmesg -T 2>/dev/null | grep -iE 'out of memory|oom-kill' | tail -5 | sed 's/^/  /' || echo "  (无 OOM 记录)"

echo
echo "=== 6) 是否有异常进程（挖矿特征：高 CPU + 陌生路径）==="
ps -eo pcpu,comm,args --sort=-pcpu 2>/dev/null | awk '$1 > 30 {print "  " $0}' | cut -c1-150 | head -8

echo
echo "=== 7) 磁盘 ==="
df -h / | sed 's/^/  /'

echo
echo "=== 8) 最近登录记录（看是否被入侵）==="
last -n 8 2>/dev/null | sed 's/^/  /'
