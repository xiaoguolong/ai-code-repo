#!/usr/bin/env bash
# 验证 ClickHouse 调优是否生效（日志表是否消失 / 内存护栏 / CPU）
set -uo pipefail
set -a; . /opt/ai-code/langfuse/.env; set +a
CH() {
  docker exec langfuse-clickhouse-1 clickhouse-client \
    -u "$CLICKHOUSE_USER" --password "$CLICKHOUSE_PASSWORD" --query "$1" 2>&1
}

echo "=== 1) 系统日志表是否已关闭（应查不到或为 0 行）==="
for t in text_log trace_log metric_log asynchronous_metric_log part_log; do
  n=$(CH "select count() from system.$t" 2>/dev/null || echo "表不存在")
  printf "  %-24s %s\n" "$t" "$n"
done

echo
echo "=== 2) 内存护栏是否生效 ==="
CH "select name, value from system.settings where name in ('max_server_memory_usage')" | sed 's/^/  /'
CH "select getSetting('max_server_memory_usage') as v" 2>/dev/null | sed 's/^/  getSetting: /'

echo
echo "=== 3) 配置是否真的被读到（看 config.d 挂载与 logger 级别）==="
docker exec langfuse-clickhouse-1 ls -l /etc/clickhouse-server/config.d/ 2>/dev/null | sed 's/^/  /'
echo "  --- 容器内日志级别 ---"
docker exec langfuse-clickhouse-1 sh -c 'grep -o "<level>[a-z]*</level>" /etc/clickhouse-server/config.d/*.xml 2>/dev/null' | sed 's/^/  /'

echo
echo "=== 4) 业务数据是否完好 ==="
CH "select count() from events_full" | sed 's/^/  events_full: /'

echo
echo "=== 5) 当前 CPU / 负载 ==="
uptime | sed 's/^/  /'
docker stats --no-stream --format '{{.Name}}|{{.CPUPerc}}|{{.MemUsage}}' 2>/dev/null \
  | grep -E 'clickhouse|langfuse-web|skywalking-oap' | awk -F'|' '{printf "  %-28s CPU=%-10s MEM=%s\n", $1, $2, $3}'

echo
echo "=== 6) Langfuse 健康 ==="
curl -s -o /dev/null -w "  langfuse health: %{http_code}\n" --max-time 8 http://127.0.0.1:3000/api/public/health
