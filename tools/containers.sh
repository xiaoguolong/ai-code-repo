#!/usr/bin/env bash
# 全容器状态一览
docker ps --format 'table {{.Names}}\t{{.Status}}' 2>/dev/null | sed 's/^/  /'
echo
echo "=== 未运行/异常容器 ==="
docker ps -a --filter "status=exited" --filter "status=restarting" --format '  {{.Names}} | {{.Status}}' 2>/dev/null || echo "  (无)"
