#!/usr/bin/env bash
# 查 Langfuse v4 的 ClickHouse 表结构，确认数据落到哪张表
set -uo pipefail
set -a; . /opt/ai-code/langfuse/.env; set +a
CH() {
  docker exec langfuse-clickhouse-1 clickhouse-client \
    -u "$CLICKHOUSE_USER" --password "$CLICKHOUSE_PASSWORD" --query "$1" 2>/dev/null
}

echo "=== ClickHouse 表与行数 ==="
for t in $(CH "select name from system.tables where database='default' and name not like '.%' order by name"); do
  n=$(CH "select count() from default.\`$t\`")
  printf "  %-30s %s\n" "$t" "$n"
done

echo
echo "=== 若 events 表有数据，看最近 3 条 ==="
CH "select toString(timestamp), type, name from events order by timestamp desc limit 3" 2>/dev/null | sed 's/^/  /' || echo "  (无 events 表或无数据)"

echo
echo "=== Langfuse web 最近日志中的 OTLP/export 关键词 ==="
docker logs --tail 300 langfuse-langfuse-web-1 2>&1 \
  | grep -aiE 'otel|otlp|trace|export|ingest' | tail -8 | sed 's/^/  /' || echo "  (无)"
