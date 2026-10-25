#!/usr/bin/env bash
# Week 28 变更前只读巡检：V11 未应用、服务/数据/配置状态。
set -uo pipefail

echo "### date"; date '+%F %T %Z'
echo "### services"
for t in platform enterprise gateway; do
  printf '%-12s active=%-8s enabled=%s\n' "$t" \
    "$(systemctl is-active ai-code-$t 2>/dev/null || true)" \
    "$(systemctl is-enabled ai-code-$t 2>/dev/null || true)"
done
echo "### jars"
for t in platform enterprise gateway; do
  f="/opt/ai-code/app/$t/app.jar"
  [ -f "$f" ] && stat -c '%n size=%s mtime=%y' "$f"
done
echo "### health"
for p in 8084 8081 8080; do
  printf 'port %s -> %s\n' "$p" "$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 http://127.0.0.1:$p/actuator/health || true)"
done
printf 'enterprise auth/me -> %s\n' "$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 http://127.0.0.1:8081/api/v1/auth/me || true)"

set -a; . /opt/ai-code/app/platform/.env; set +a
PU="$SPRING_DATASOURCE_USERNAME"; PP="$SPRING_DATASOURCE_PASSWORD"; PDB="${SPRING_DATASOURCE_URL##*/}"; PDB="${PDB%%\?*}"
PSQL() { docker exec -e PGPASSWORD="$PP" pg16 psql -h 127.0.0.1 -U "$PU" -d "$PDB" -tAc "$1"; }
echo "### platform flyway"
PSQL "select installed_rank,version,description,success from flyway_schema_history_platform order by installed_rank;" | tail -5
echo "### v11 precheck"
PSQL "select 'disabled_at='||count(*) from information_schema.columns where table_name='platform_user' and column_name='disabled_at';"
echo "### users"
PSQL "select id||'|'||username||'|'||role_key||'|'||tenant_key from platform_user order by id;"
echo "### counts"
PSQL "select 'memory='||count(*) from platform_memory; select 'approval='||count(*) from platform_approval; select 'binding='||count(*) from platform_knowledge_binding;"
echo "### resources"; free -m | sed -n '1,3p'; df -h / | tail -1
echo "### done"
