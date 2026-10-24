#!/usr/bin/env bash
# 验证系统日志表是否已停止增长（间隔 60 秒采样两次）
set -uo pipefail
set -a; . /opt/ai-code/langfuse/.env; set +a
CH() {
  docker exec langfuse-clickhouse-1 clickhouse-client \
    -u "$CLICKHOUSE_USER" --password "$CLICKHOUSE_PASSWORD" --query "$1" 2>/dev/null
}
count() {
  # 表不存在时返回 "不存在"
  local n
  n=$(CH "select count() from system.$1" 2>/dev/null)
  if [ -z "$n" ]; then echo "不存在"; else echo "$n"; fi
}

TABLES="text_log trace_log metric_log asynchronous_metric_log part_log processors_profile_log query_thread_log"

echo "=== 第一次采样（$(date '+%T')）==="
for t in $TABLES; do printf "  %-26s %s\n" "$t" "$(count $t)"; done

echo
echo "等待 60 秒..."
sleep 60

echo "=== 第二次采样（$(date '+%T')）—— 与上面对比，应完全相同 ==="
for t in $TABLES; do printf "  %-26s %s\n" "$t" "$(count $t)"; done

echo
echo "=== 配置生效确认（logger 级别 / 内存上限文件）==="
docker exec langfuse-clickhouse-1 sh -c 'cat /etc/clickhouse-server/config.d/zz-aicode-tuning.xml | grep -E "level|max_server_memory_usage|remove" | head -12' 2>/dev/null | sed 's/^/  /'

echo
echo "=== 关键指标 ==="
echo -n "  MEMORY_LIMIT_EXCEEDED 累计: "
CH "select value from system.errors where name='MEMORY_LIMIT_EXCEEDED'" | sed 's/^$/(无)/'
echo -n "  当前合并任务: "; CH "select count() from system.merges"
echo -n "  业务 events_full: "; CH "select count() from events_full"
