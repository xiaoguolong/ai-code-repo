#!/usr/bin/env bash
# 记录三处计数：业务库执行记录、Langfuse traces、SkyWalking 当日 segment
set -uo pipefail
TODAY=$(date -u +%Y%m%d)
set -a; . /opt/ai-code/langfuse/.env; set +a

echo "=== 基线/当前计数 ==="
echo -n "  execution_record : "
docker exec pg16 psql -U aidemo -d aidemo -tAc "select count(*) from execution_record" 2>/dev/null | tr -d '\r'

echo -n "  langfuse events  : "
docker exec langfuse-clickhouse-1 clickhouse-client \
  -u "$CLICKHOUSE_USER" --password "$CLICKHOUSE_PASSWORD" \
  --query "select count() from events_full" 2>/dev/null | tr -d '\r'

echo -n "  langfuse genertns: "
docker exec langfuse-clickhouse-1 clickhouse-client \
  -u "$CLICKHOUSE_USER" --password "$CLICKHOUSE_PASSWORD" \
  --query "select count() from events_full where type='GENERATION'" 2>/dev/null | tr -d '\r'

echo -n "  SW segment($TODAY) : "
docker exec pg16 psql -U aidemo -d skywalking -tAc "select count(*) from segment_$TODAY" 2>/dev/null | tr -d '\r'

echo
echo "=== 若已有数据，列出最近 5 条 event ==="
docker exec langfuse-clickhouse-1 clickhouse-client \
  -u "$CLICKHOUSE_USER" --password "$CLICKHOUSE_PASSWORD" \
  --query "select name, toString(start_time), total_cost from events_full order by start_time desc limit 5" \
  --format TSV 2>/dev/null | sed 's/^/  /' || echo "  (无)"
