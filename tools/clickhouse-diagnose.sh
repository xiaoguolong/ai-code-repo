#!/usr/bin/env bash
# ClickHouse 高 CPU 深挖：查询、合并、parts 数量、错误
set -uo pipefail
set -a; . /opt/ai-code/langfuse/.env; set +a
CH() {
  docker exec langfuse-clickhouse-1 clickhouse-client \
    -u "$CLICKHOUSE_USER" --password "$CLICKHOUSE_PASSWORD" --query "$1" 2>/dev/null
}

echo "=== 1) 正在跑的查询 ==="
CH "select count() from system.processes" | sed 's/^/  并发查询数: /'
CH "select user, elapsed, formatReadableSize(memory_usage) mem, substring(query,1,80) q from system.processes order by elapsed desc limit 5" \
  | sed 's/^/  /'

echo
echo "=== 2) 正在进行的合并（merges）==="
CH "select count() from system.merges" | sed 's/^/  合并任务数: /'
CH "select database, table, elapsed, formatReadableSize(memory_usage) mem, progress from system.merges order by elapsed desc limit 5" \
  | sed 's/^/  /'

echo
echo "=== 3) 各表 parts 数量（太多会触发疯狂合并）==="
CH "select table, count() parts, formatReadableSize(sum(bytes_on_disk)) size from system.parts where active group by table order by parts desc limit 10" \
  | sed 's/^/  /'

echo
echo "=== 4) 数据量 ==="
for t in events_full events_core events_core_mv observations; do
  n=$(CH "select count() from $t")
  s=$(CH "select formatReadableSize(sum(bytes_on_disk)) from system.parts where table='$t' and active")
  printf "  %-16s rows=%-10s disk=%s\n" "$t" "$n" "$s"
done

echo
echo "=== 5) 最近的错误 ==="
CH "select name, value, substring(last_error_message,1,90) from system.errors order by value desc limit 6" \
  | sed 's/^/  /'

echo
echo "=== 6) ClickHouse 关键配置 ==="
CH "select name, value from system.settings where name in ('max_threads','max_memory_usage','max_concurrent_queries')" \
  | sed 's/^/  /'
echo "  --- 容器内存上限 ---"
docker inspect langfuse-clickhouse-1 --format '{{.HostConfig.Memory}}' | awk '{printf "  mem_limit: %.0f MiB\n", $1/1024/1024}'

echo
echo "=== 7) Langfuse 是否还在写入（最近 10 分钟新增 event）==="
CH "select count() from events_full where created_at > now() - interval 10 minute" | sed 's/^/  近10分钟: /'
