#!/usr/bin/env bash
# 读 ClickHouse 容器内的错误日志（来自命名卷，容器在重启循环里也能读）
set -uo pipefail
VOL=langfuse_langfuse_clickhouse_logs
echo "=== 卷内容 ==="
docker run --rm -v "$VOL":/logs alpine sh -c 'ls -la /logs 2>/dev/null'
echo
echo "=== err.log 尾部 ==="
docker run --rm -v "$VOL":/logs alpine sh -c 'tail -25 /logs/clickhouse-server.err.log 2>/dev/null' | sed 's/^/  /'
echo
echo "=== server.log 尾部（含配置解析错误）==="
docker run --rm -v "$VOL":/logs alpine sh -c 'tail -25 /logs/clickhouse-server.log 2>/dev/null' | sed 's/^/  /'
