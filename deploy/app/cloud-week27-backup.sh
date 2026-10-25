#!/usr/bin/env bash
# Week 27 变更前备份：两个业务库 + 运行产物 + 配置 + 状态快照（权限 600，不打印密钥）。
set -euo pipefail
umask 077

TS=$(date +%Y%m%d-%H%M%S)
DEST="/opt/ai-code/backup/week27-${TS}"
mkdir -p "$DEST"
echo "DEST=$DEST"

# ── 1. PostgreSQL 逻辑备份（容器内 pg_dump）──────────────────────────────────
set -a; . /opt/ai-code/app/platform/.env; set +a
PU="$SPRING_DATASOURCE_USERNAME"; PP="$SPRING_DATASOURCE_PASSWORD"; PDB="${SPRING_DATASOURCE_URL##*/}"; PDB="${PDB%%\?*}"
set -a; . /opt/ai-code/app/enterprise/.env; set +a
EU="$SPRING_DATASOURCE_USERNAME"; EP="$SPRING_DATASOURCE_PASSWORD"; EDB="${SPRING_DATASOURCE_URL##*/}"; EDB="${EDB%%\?*}"

docker exec -e PGPASSWORD="$PP" pg16 pg_dump -h 127.0.0.1 -U "$PU" -d "$PDB" -Fc > "$DEST/platform-${PDB}.dump"
echo "platform dump: $(stat -c%s "$DEST/platform-${PDB}.dump") bytes"
docker exec -e PGPASSWORD="$EP" pg16 pg_dump -h 127.0.0.1 -U "$EU" -d "$EDB" -Fc > "$DEST/enterprise-${EDB}.dump"
echo "enterprise dump: $(stat -c%s "$DEST/enterprise-${EDB}.dump") bytes"

[ -s "$DEST/platform-${PDB}.dump" ] || { echo "ERROR: platform dump empty"; exit 1; }
[ -s "$DEST/enterprise-${EDB}.dump" ] || { echo "ERROR: enterprise dump empty"; exit 1; }

# ── 2. 运行产物与配置快照 ────────────────────────────────────────────────────
for t in platform enterprise gateway; do
  cp -a "/opt/ai-code/app/$t/app.jar" "$DEST/$t-app.jar"
  [ -f "/opt/ai-code/app/$t/.env" ] && cp -a "/opt/ai-code/app/$t/.env" "$DEST/$t.env"
done
echo "jars+env copied"

# ── 3. 迁移/业务状态快照（便于回滚对账）──────────────────────────────────────
{
  echo "### platform flyway"
  docker exec -e PGPASSWORD="$PP" pg16 psql -h 127.0.0.1 -U "$PU" -d "$PDB" -tAc \
    "select installed_rank,version,description,success from flyway_schema_history_platform order by installed_rank;"
  echo "### platform counts"
  docker exec -e PGPASSWORD="$PP" pg16 psql -h 127.0.0.1 -U "$PU" -d "$PDB" -tAc \
    "select 'platform_user='||count(*) from platform_user;
     select 'memory='||count(*) from platform_memory;
     select 'approval_'||status||'='||count(*) from platform_approval group by status order by status;
     select 'followup_task='||count(*) from platform_followup_task;
     select 'knowledge_binding='||count(*) from platform_knowledge_binding;
     select 'tenant='||count(*) from platform_tenant;"
  echo "### knowledge bindings"
  docker exec -e PGPASSWORD="$PP" pg16 psql -h 127.0.0.1 -U "$PU" -d "$PDB" -tAc \
    "select tenant_key||'|'||knowledge_base_id||'|'||name||'|enabled='||enabled from platform_knowledge_binding order by tenant_key;"
  echo "### enterprise flyway"
  docker exec -e PGPASSWORD="$EP" pg16 psql -h 127.0.0.1 -U "$EU" -d "$EDB" -tAc \
    "select installed_rank,version,description,success from flyway_schema_history order by installed_rank;"
  echo "### enterprise counts"
  docker exec -e PGPASSWORD="$EP" pg16 psql -h 127.0.0.1 -U "$EU" -d "$EDB" -tAc \
    "select 'app_user='||count(*) from app_user;
     select 'knowledge_base='||count(*) from knowledge_base;
     select 'document='||count(*) from document;
     select 'document_chunk='||count(*) from document_chunk;"
  echo "### jars"
  for t in platform enterprise gateway; do sha256sum "/opt/ai-code/app/$t/app.jar"; done
  echo "### pg16 image"
  docker inspect pg16 --format '{{.Config.Image}}'
  echo "### env keys (names only)"
  for t in platform enterprise; do
    echo "-- $t"
    grep -Eo '^[A-Z0-9_]+' "/opt/ai-code/app/$t/.env" | sort -u
  done
} > "$DEST/state-before.txt"
echo "state snapshot written"

du -sh "$DEST"
echo "BACKUP DONE: $DEST"
