#!/usr/bin/env bash
# =============================================================================
# Week 23 云端迁移前备份（平台库 platform_memory + Flyway 历史 + 当前 jar/.env）
# -----------------------------------------------------------------------------
# 在云主机执行：bash /tmp/cloud-week23-backup.sh
# 产物：/opt/ai-code/backup/week23-<stamp>/
# 不打印数据库密码。
# =============================================================================
set -uo pipefail

ENV_FILE="/opt/ai-code/app/platform/.env"
set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

URL="${SPRING_DATASOURCE_URL:?missing SPRING_DATASOURCE_URL}"
REST="${URL#jdbc:postgresql://}"
HOSTPORT="${REST%%/*}"
DB="${REST#*/}"
DB="${DB%%\?*}"
DBHOST="${HOSTPORT%%:*}"
DBPORT="${HOSTPORT##*:}"
DBUSER="${SPRING_DATASOURCE_USERNAME:?missing username}"
DBPASS="${SPRING_DATASOURCE_PASSWORD:?missing password}"

STAMP="$(date +%Y%m%d-%H%M%S)"
DIR="/opt/ai-code/backup/week23-${STAMP}"
TMP="/tmp/w23-platform-${STAMP}.dump"

echo "=== 备份目录 $DIR ==="
sudo mkdir -p "$DIR"
sudo chown root:root "$DIR"
sudo chmod 700 "$DIR"

echo "=== pg_dump platform_memory + flyway history ==="
docker exec -e PGPASSWORD="$DBPASS" pg16 \
  pg_dump -h "$DBHOST" -p "$DBPORT" -U "$DBUSER" -d "$DB" \
  -Fc -t public.platform_memory -t public.flyway_schema_history_platform \
  > "$TMP"
RC=$?
if [ $RC -ne 0 ]; then
  echo "FAIL  pg_dump exit=$RC"
  exit 1
fi
sudo install -m 600 -o root -g root "$TMP" "$DIR/platform_memory-and-history.dump"
rm -f "$TMP"

echo "=== 备份当前 jar / .env ==="
sudo install -m 640 -o root -g root /opt/ai-code/app/platform/app.jar "$DIR/app.jar.prev"
sudo install -m 600 -o root -g root "$ENV_FILE" "$DIR/platform.env"

echo "=== 生成校验和 ==="
sudo bash -c "cd '$DIR' && sha256sum * > SHA256SUMS && ls -lh && echo '--- sha256 ---' && cat SHA256SUMS"

echo "BACKUP_DIR=$DIR"
