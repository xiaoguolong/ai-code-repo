#!/usr/bin/env bash
# Week 26 变更前备份：两个业务库 + 运行产物 + 配置（权限 600，不打印任何密钥）
set -euo pipefail
umask 077

TS=$(date +%Y%m%d-%H%M%S)
DEST="/opt/ai-code/backup/week26-${TS}"
mkdir -p "$DEST"
echo "DEST=$DEST"

# ── 1. PostgreSQL 逻辑备份（容器内 pg_dump，走本地回环）──────────────────────
set -a; . /opt/ai-code/app/platform/.env; set +a
PU="$SPRING_DATASOURCE_USERNAME"; PP="$SPRING_DATASOURCE_PASSWORD"; PDB="${SPRING_DATASOURCE_URL##*/}"; PDB="${PDB%%\?*}"
set -a; . /opt/ai-code/app/enterprise/.env; set +a
EU="$SPRING_DATASOURCE_USERNAME"; EP="$SPRING_DATASOURCE_PASSWORD"; EDB="${SPRING_DATASOURCE_URL##*/}"; EDB="${EDB%%\?*}"

docker exec -e PGPASSWORD="$PP" pg16 pg_dump -h 127.0.0.1 -U "$PU" -d "$PDB" -Fc > "$DEST/platform-${PDB}.dump"
echo "platform dump: $(stat -c%s "$DEST/platform-${PDB}.dump") bytes"
docker exec -e PGPASSWORD="$EP" pg16 pg_dump -h 127.0.0.1 -U "$EU" -d "$EDB" -Fc > "$DEST/enterprise-${EDB}.dump"
echo "enterprise dump: $(stat -c%s "$DEST/enterprise-${EDB}.dump") bytes"

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
    "select 'memory='||count(*) from platform_memory; select 'approval='||count(*) from platform_approval;"
  echo "### enterprise flyway"
  docker exec -e PGPASSWORD="$EP" pg16 psql -h 127.0.0.1 -U "$EU" -d "$EDB" -tAc \
    "select installed_rank,version,description,success from flyway_schema_history order by installed_rank;"
  echo "### enterprise tables"
  docker exec -e PGPASSWORD="$EP" pg16 psql -h 127.0.0.1 -U "$EU" -d "$EDB" -tAc \
    "select table_name from information_schema.tables where table_schema='public' order by 1;"
  echo "### pg16 image"
  docker inspect pg16 --format '{{.Config.Image}}'
  echo "### pg16 env (key names only)"
  docker inspect pg16 --format '{{range .Config.Env}}{{println .}}{{end}}' | cut -d= -f1
} > "$DEST/state-before.txt"
echo "state snapshot written"

du -sh "$DEST"
echo "BACKUP DONE: $DEST"
