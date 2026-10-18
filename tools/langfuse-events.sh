#!/usr/bin/env bash
# 确认本次执行（traceId 前缀 week19 / 返回的 traceId）是否落进 Langfuse v4 的 events 表
set -uo pipefail
set -a; . /opt/ai-code/langfuse/.env; set +a
CH() {
  docker exec langfuse-clickhouse-1 clickhouse-client \
    -u "$CLICKHOUSE_USER" --password "$CLICKHOUSE_PASSWORD" --query "$1" 2>/dev/null
}

echo "=== events_full 列名 ==="
CH "describe table events_full" | awk '{printf "%s ", $1}'
echo; echo

echo "=== 最近 8 条 event（时间 / 类型 / 名称 / trace_id）==="
CH "select toString(timestamp) as ts, type, name, trace_id from events_full order by timestamp desc limit 8" \
  | sed 's/^/  /'

echo
echo "=== 观测类型分布（type -> 数量）==="
CH "select type, count() from events_full group by type order by 2 desc" | sed 's/^/  /'

echo
echo "=== 指定 trace 是否存在（本次执行返回的 traceId）==="
for tid in 703a334201a2585ba47779c8ac4ca687 a0c51f307a5114c3808cfab4dec40448 bfd10d6dacd96ee0f6727bca1780709a; do
  n=$(CH "select count() from events_full where trace_id = '$tid'")
  printf "  %s -> %s 条\n" "$tid" "$n"
done
