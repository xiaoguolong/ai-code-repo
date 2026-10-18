#!/usr/bin/env bash
# 验证本地应用的数据是否真的写到了云上三套后端
set -uo pipefail
D=$(date -u +%Y%m%d)
P="docker exec pg16 psql -U aidemo -d aidemo -tAc"

echo "=== 1) 云端业务库 aidemo：审计日志（登录/接口调用会写）==="
$P "select count(*) from audit_log" | sed 's/^/总计: /'
$P "select to_char(created_at,'HH24:MI:SS') || ' | ' || username || ' | ' || action from audit_log order by created_at desc limit 5" 2>&1 | sed 's/^/  /'

echo
echo "=== 2) 云端业务库：执行记录 ==="
$P "select count(*) from execution_record" | sed 's/^/总计: /'

echo
echo "=== 3) SkyWalking：今天上报的实例与分段数 ==="
SW="docker exec pg16 psql -U aidemo -d skywalking -tAc"
echo "实例（instance_traffic）:"
$SW "select instance_traffic_name || ' (last_ping ' || last_ping || ')' from instance_traffic_$D order by last_ping desc" 2>&1 | sed 's/^/  /'
echo "分段数（segment_$D）:"
$SW "select count(*) from segment_$D" 2>&1 | sed 's/^/  /'
echo "按服务统计:"
$SW "select service_id || ' -> ' || count(*) from segment_$D group by service_id order by 2 desc" 2>&1 | sed 's/^/  /'

echo
echo "=== 4) Langfuse：最近的 trace（走它自己的 API，密钥取自服务器 .env）==="
set -a; . /opt/ai-code/langfuse/.env; set +a
PK="$LANGFUSE_INIT_PROJECT_PUBLIC_KEY"
SK="$LANGFUSE_INIT_PROJECT_SECRET_KEY"
curl -s --max-time 10 -u "$PK:$SK" "http://127.0.0.1:3000/api/public/traces?limit=3" \
  | head -c 600
echo
echo
echo "=== 5) Langfuse：trace 总数（ClickHouse）==="
docker exec langfuse-clickhouse-1 clickhouse-client -u "$CLICKHOUSE_USER" --password "$CLICKHOUSE_PASSWORD" \
  --query "select count() from traces" 2>/dev/null | sed 's/^/  traces: /' || echo "  (查询失败)"
