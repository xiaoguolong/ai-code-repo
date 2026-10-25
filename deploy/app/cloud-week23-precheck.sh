#!/usr/bin/env bash
# =============================================================================
# Week 23 云端迁移前检查（只读，不改任何东西）
# -----------------------------------------------------------------------------
# 在云主机执行：bash /tmp/cloud-week23-precheck.sh
# 输出：服务状态 / 平台库 Flyway 版本 / V6 对象是否已存在 / 记忆行数 / 资源
# 不打印数据库密码或连接串。
# =============================================================================
set -uo pipefail

ENV_FILE="/opt/ai-code/app/platform/.env"
if [ ! -f "$ENV_FILE" ]; then
  echo "FAIL  找不到 $ENV_FILE"
  exit 1
fi
set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

URL="${SPRING_DATASOURCE_URL:-}"
if [ -z "$URL" ]; then
  echo "FAIL  SPRING_DATASOURCE_URL 为空"
  exit 1
fi
REST="${URL#jdbc:postgresql://}"
HOSTPORT="${REST%%/*}"
DB="${REST#*/}"
DB="${DB%%\?*}"
DBHOST="${HOSTPORT%%:*}"
DBPORT="${HOSTPORT##*:}"
DBUSER="${SPRING_DATASOURCE_USERNAME:-}"
DBPASS="${SPRING_DATASOURCE_PASSWORD:-}"

psql_q() {
  docker exec -e PGPASSWORD="$DBPASS" pg16 \
    psql -h "$DBHOST" -p "$DBPORT" -U "$DBUSER" -d "$DB" -Atc "$1" 2>&1
}

echo "=== 1) systemd ==="
for svc in ai-code-platform ai-code-gateway ai-code-enterprise; do
  printf '%-22s %s\n' "$svc" "$(systemctl is-active "$svc" 2>/dev/null || echo inactive)"
done
printf 'platform health      %s\n' "$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://127.0.0.1:8084/actuator/health || echo ERR)"

echo
echo "=== 2) 数据库连接与 Flyway 历史（平台库）==="
echo "db=$DB host=$DBHOST port=$DBPORT user=$DBUSER"
psql_q "SELECT version || '|' || description || '|' || success FROM flyway_schema_history_platform ORDER BY installed_rank;"

echo
echo "=== 3) V6 对象是否已存在 ==="
echo -n "platform_memory lifecycle columns: "
psql_q "SELECT string_agg(column_name, ',' ORDER BY column_name) FROM information_schema.columns WHERE table_name='platform_memory' AND column_name IN ('expires_at','embedding_model');"
echo -n "platform_approval table: "
psql_q "SELECT COALESCE(to_regclass('public.platform_approval')::text, 'MISSING');"

echo
echo "=== 4) 迁移前数据量 ==="
echo -n "platform_memory rows: "
psql_q "SELECT count(*) FROM platform_memory;"
APPROVAL_EXISTS="$(psql_q "SELECT to_regclass('public.platform_approval');")"
if [ "$APPROVAL_EXISTS" = "platform_approval" ]; then
  echo -n "platform_approval rows: "
  psql_q "SELECT count(*) FROM platform_approval;"
else
  echo "platform_approval rows: TABLE_MISSING"
fi

echo
echo "=== 5) 迁移后向量来源分布（V6 后）==="
psql_q "SELECT COALESCE(embedding_model, 'NULL') AS model, count(*) FROM platform_memory GROUP BY 1 ORDER BY 1;"

echo
echo "=== 6) TTL 与审批/审计 ==="
psql_q "SELECT 'ttl_set=' || count(*) FILTER (WHERE expires_at IS NOT NULL) || ', never_expire=' || count(*) FILTER (WHERE expires_at IS NULL) FROM platform_memory;"
psql_q "SELECT status, count(*) FROM platform_approval GROUP BY 1 ORDER BY 1;"
psql_q "SELECT action, result, count(*) FROM audit_log WHERE action LIKE 'APPROVAL%' OR action LIKE 'MEMORY%' GROUP BY 1,2 ORDER BY 1,2;"

echo
echo "=== 7) 资源 ==="
free -m | awk '/Mem:/{print "mem: "$2" MB total, "$7" MB available"}'
free -m | awk '/Swap:/{print "swap: "$2" MB"}'
df -h / | awk 'NR==2{print "disk: "$2" total, "$4" avail ("$5" used)"}'
