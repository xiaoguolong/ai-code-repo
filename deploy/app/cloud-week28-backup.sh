#!/usr/bin/env bash
# Week 28 变更前备份：平台库 + 平台 jar + 状态（Week 28 只发平台，V11 只加列）。
set -euo pipefail
umask 077

TS=$(date +%Y%m%d-%H%M%S)
DEST="/opt/ai-code/backup/week28-${TS}"
mkdir -p "$DEST"
echo "DEST=$DEST"

set -a; . /opt/ai-code/app/platform/.env; set +a
PU="$SPRING_DATASOURCE_USERNAME"; PP="$SPRING_DATASOURCE_PASSWORD"; PDB="${SPRING_DATASOURCE_URL##*/}"; PDB="${PDB%%\?*}"

docker exec -e PGPASSWORD="$PP" pg16 pg_dump -h 127.0.0.1 -U "$PU" -d "$PDB" -Fc > "$DEST/platform-${PDB}.dump"
echo "platform dump: $(stat -c%s "$DEST/platform-${PDB}.dump") bytes"
[ -s "$DEST/platform-${PDB}.dump" ] || { echo "ERROR: dump empty"; exit 1; }

cp -a /opt/ai-code/app/platform/app.jar "$DEST/platform-app.jar"
cp -a /opt/ai-code/app/platform/.env "$DEST/platform.env"
echo "jar+env copied"

{
  echo "### platform flyway"
  docker exec -e PGPASSWORD="$PP" pg16 psql -h 127.0.0.1 -U "$PU" -d "$PDB" -tAc \
    "select installed_rank,version,description,success from flyway_schema_history_platform order by installed_rank;"
  echo "### users"
  docker exec -e PGPASSWORD="$PP" pg16 psql -h 127.0.0.1 -U "$PU" -d "$PDB" -tAc \
    "select id||'|'||username||'|'||role_key||'|'||tenant_key from platform_user order by id;"
  echo "### counts"
  docker exec -e PGPASSWORD="$PP" pg16 psql -h 127.0.0.1 -U "$PU" -d "$PDB" -tAc \
    "select 'memory='||count(*) from platform_memory;
     select 'approval='||count(*) from platform_approval;
     select 'binding='||count(*) from platform_knowledge_binding;"
  echo "### jar sha256"; sha256sum /opt/ai-code/app/platform/app.jar
} > "$DEST/state-before.txt"

du -sh "$DEST"
echo "BACKUP DONE: $DEST"
