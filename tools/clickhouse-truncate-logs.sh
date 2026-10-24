#!/usr/bin/env bash
# 止血：清空 ClickHouse 系统诊断日志表（不含任何业务数据）
set -uo pipefail
set -a; . /opt/ai-code/langfuse/.env; set +a
CH() {
  docker exec langfuse-clickhouse-1 clickhouse-client \
    -u "$CLICKHOUSE_USER" --password "$CLICKHOUSE_PASSWORD" --query "$1" 2>&1
}

echo "=== 清理前：磁盘与 parts ==="
df -h / | tail -1 | sed 's/^/  /'
CH "select table, count() parts, formatReadableSize(sum(bytes_on_disk)) size from system.parts where active and database='system' group by table order by sum(bytes_on_disk) desc limit 6" \
  | sed 's/^/  /'

echo
echo "=== 逐个 TRUNCATE 系统日志（这些是诊断日志，删了无影响）==="
for t in text_log trace_log metric_log asynchronous_metric_log part_log query_log error_log processors_profile_log opentelemetry_span_log background_schedule_pool_log; do
  out=$(CH "TRUNCATE TABLE IF EXISTS system.$t")
  if [ -z "$out" ]; then echo "  OK   system.$t"; else echo "  FAIL system.$t -> $out"; fi
done

echo
echo "=== 清理后：磁盘与 parts ==="
sleep 3
df -h / | tail -1 | sed 's/^/  /'
CH "select table, count() parts, formatReadableSize(sum(bytes_on_disk)) size from system.parts where active and database='system' group by table order by sum(bytes_on_disk) desc limit 6" \
  | sed 's/^/  /'

echo
echo "=== 合并队列是否清空 ==="
CH "select count() from system.merges" | sed 's/^/  剩余合并任务: /'
