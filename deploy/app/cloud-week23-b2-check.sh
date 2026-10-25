#!/usr/bin/env bash
# Week 23 批次 2 迁移后巡检（只读）
# 在云主机执行：bash /tmp/cloud-week23-b2-check.sh
set -uo pipefail

ENV_FILE="/opt/ai-code/app/platform/.env"
set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a
URL="${SPRING_DATASOURCE_URL:?missing SPRING_DATASOURCE_URL}"
REST="${URL#jdbc:postgresql://}"
HOSTPORT="${REST%%/*}"
DB="${REST#*/}"; DB="${DB%%\?*}"
DBHOST="${HOSTPORT%%:*}"; DBPORT="${HOSTPORT##*:}"
DBUSER="${SPRING_DATASOURCE_USERNAME:?missing username}"
DBPASS="${SPRING_DATASOURCE_PASSWORD:?missing password}"

q() {
  docker exec -e PGPASSWORD="$DBPASS" pg16 \
    psql -h "$DBHOST" -p "$DBPORT" -U "$DBUSER" -d "$DB" -Atc "$1" 2>&1
}

echo "=== 服务 ==="
for svc in ai-code-platform ai-code-gateway ai-code-enterprise; do
  printf '%-22s %s\n' "$svc" "$(systemctl is-active "$svc" 2>/dev/null || echo inactive)"
done
printf 'platform health      %s\n' "$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8084/actuator/health || echo ERR)"

echo
echo "=== Flyway（应到 v7）==="
q "SELECT version || '|' || description || '|' || success FROM flyway_schema_history_platform ORDER BY installed_rank;"

echo
echo "=== 角色按租户（每个租户 3 个基础角色）==="
q "SELECT tenant_key, count(*) AS roles FROM platform_role GROUP BY 1 ORDER BY 1;"
q "SELECT tenant_key, role_key, count(*) AS grants FROM platform_role_agent GROUP BY 1,2 ORDER BY 1,2;"

echo
echo "=== 执行记录 tenant ==="
q "SELECT tenant_key, count(*) FROM execution_record GROUP BY 1 ORDER BY 1;"
echo -n "tenant mismatch vs user: "
q "SELECT count(*) FROM execution_record e JOIN platform_user u ON u.id=e.user_id WHERE e.tenant_key <> u.tenant_key;"

echo
echo "=== 资源 ==="
free -m | awk '/Mem:/{print "mem: "$2" MB total, "$7" MB available"}'
df -h / | awk 'NR==2{print "disk: "$2" total, "$4" avail ("$5" used)"}'
