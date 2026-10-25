#!/usr/bin/env bash
# Week 27 变更前只读巡检：不写任何业务数据、不重启服务。
# 只打印状态/计数/键名，不打印令牌或密码。
set -uo pipefail

echo "### date"
date '+%F %T %Z'
echo "### host"
hostname; uptime
echo "### mem/disk"
free -m | sed -n '1,3p'
df -h / | tail -1
echo "### services"
for t in platform enterprise gateway; do
  printf '%-12s active=%-8s enabled=%s\n' "$t" \
    "$(systemctl is-active ai-code-$t 2>/dev/null || true)" \
    "$(systemctl is-enabled ai-code-$t 2>/dev/null || true)"
done
echo "### jars"
for t in platform enterprise gateway; do
  f="/opt/ai-code/app/$t/app.jar"
  if [ -f "$f" ]; then stat -c '%n size=%s mtime=%y' "$f"; sha256sum "$f" | cut -c1-16; fi
done
echo "### health"
for p in 8084 8081 8080; do
  printf 'port %s -> %s\n' "$p" "$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 http://127.0.0.1:$p/actuator/health || true)"
done
printf 'enterprise auth/me -> %s\n' "$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 http://127.0.0.1:8081/api/v1/auth/me || true)"

# ── DB 连接信息只从 .env 读取，不打印 ─────────────────────────────────────────
set -a; . /opt/ai-code/app/platform/.env; set +a
PU="$SPRING_DATASOURCE_USERNAME"; PP="$SPRING_DATASOURCE_PASSWORD"; PDB="${SPRING_DATASOURCE_URL##*/}"; PDB="${PDB%%\?*}"
set -a; . /opt/ai-code/app/enterprise/.env; set +a
EU="$SPRING_DATASOURCE_USERNAME"; EP="$SPRING_DATASOURCE_PASSWORD"; EDB="${SPRING_DATASOURCE_URL##*/}"; EDB="${EDB%%\?*}"
echo "### db names platform=$PDB enterprise=$EDB"
echo "### platform flyway"
docker exec -e PGPASSWORD="$PP" pg16 psql -h 127.0.0.1 -U "$PU" -d "$PDB" -tAc \
  "select installed_rank,version,description,success from flyway_schema_history_platform order by installed_rank;" | tail -12
echo "### platform counts"
docker exec -e PGPASSWORD="$PP" pg16 psql -h 127.0.0.1 -U "$PU" -d "$PDB" -tAc \
  "select 'platform_user='||count(*) from platform_user;
   select 'memory='||count(*) from platform_memory;
   select 'approval_total='||count(*) from platform_approval;
   select 'approval_'||status||'='||count(*) from platform_approval group by status order by status;
   select 'followup_task='||count(*) from platform_followup_task;
   select 'knowledge_binding='||count(*) from platform_knowledge_binding;
   select 'tenant='||count(*) from platform_tenant;"
echo "### knowledge bindings (precheck, no sync columns)"
docker exec -e PGPASSWORD="$PP" pg16 psql -h 127.0.0.1 -U "$PU" -d "$PDB" -tAc \
  "select tenant_key||'|'||knowledge_base_id||'|'||name||'|enabled='||enabled from platform_knowledge_binding order by tenant_key;"
echo "### platform columns (v10 precheck)"
docker exec -e PGPASSWORD="$PP" pg16 psql -h 127.0.0.1 -U "$PU" -d "$PDB" -tAc \
  "select 'sync_status='||count(*) from information_schema.columns where table_name='platform_knowledge_binding' and column_name='sync_status';
   select 'reminder_table='||count(*) from information_schema.tables where table_name='platform_approval_reminder';"
echo "### enterprise flyway"
docker exec -e PGPASSWORD="$EP" pg16 psql -h 127.0.0.1 -U "$EU" -d "$EDB" -tAc \
  "select installed_rank,version,description,success from flyway_schema_history order by installed_rank;" | tail -12
echo "### enterprise counts"
docker exec -e PGPASSWORD="$EP" pg16 psql -h 127.0.0.1 -U "$EU" -d "$EDB" -tAc \
  "select 'app_user='||count(*) from app_user;
   select 'knowledge_base='||count(*) from knowledge_base;
   select 'document='||count(*) from document;
   select 'document_chunk='||count(*) from document_chunk;"
echo "### env keys (presence only)"
for t in platform enterprise; do
  echo "-- $t"
  grep -Eo '^[A-Z0-9_]+' "/opt/ai-code/app/$t/.env" | sort -u | tr '\n' ' '; echo
done
# 令牌一致性只记指纹前 12 位
PT=$(grep -E '^PLATFORM_KNOWLEDGE_INTERNAL_TOKEN=' /opt/ai-code/app/platform/.env | cut -d= -f2- || true)
ET=$(grep -E '^ENTERPRISE_INTERNAL_TOKEN=' /opt/ai-code/app/enterprise/.env | cut -d= -f2- || true)
if [ -n "$PT" ] && [ -n "$ET" ]; then
  echo "token_sha256_platform=$(printf '%s' "$PT" | sha256sum | cut -c1-12) enterprise=$(printf '%s' "$ET" | sha256sum | cut -c1-12) equal=$([ "$PT" = "$ET" ] && echo yes || echo no)"
else
  echo "token_configured platform=$([ -n "$PT" ] && echo yes || echo no) enterprise=$([ -n "$ET" ] && echo yes || echo no)"
fi
echo "### pg16"
docker inspect pg16 --format 'image={{.Config.Image}} status={{.State.Status}}'
echo "### scheduling config"
grep -E '^(PLATFORM_APPROVAL_REMINDER|PLATFORM_KNOWLEDGE_RECONCILE)' /opt/ai-code/app/platform/.env 2>/dev/null || echo "no week27 keys in platform .env (defaults apply)"
echo "### done"
