#!/usr/bin/env bash
# =============================================================================
# Week 23 批次 2 云端迁移前备份（V7：角色四表 + 执行记录 + 用户 + 历史表）
# -----------------------------------------------------------------------------
# 在云主机执行：bash /tmp/cloud-week23-b2-backup.sh
# 产物：/opt/ai-code/backup/week23-b2-<stamp>/
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
DIR="/opt/ai-code/backup/week23-b2-${STAMP}"
TMP="/tmp/w23-b2-${STAMP}.dump"

echo "=== 备份目录 $DIR ==="
sudo mkdir -p "$DIR"
sudo chmod 700 "$DIR"

echo "=== pg_dump 角色四表 + 执行记录 + 用户 + 历史表 ==="
docker exec -e PGPASSWORD="$DBPASS" pg16 \
  pg_dump -h "$DBHOST" -p "$DBPORT" -U "$DBUSER" -d "$DB" -Fc \
  -t public.platform_role \
  -t public.platform_role_agent \
  -t public.platform_role_tool \
  -t public.platform_role_patient \
  -t public.execution_record \
  -t public.platform_user \
  -t public.flyway_schema_history_platform \
  > "$TMP"
if [ $? -ne 0 ]; then
  echo "FAIL pg_dump"
  exit 1
fi
sudo install -m 600 -o root -g root "$TMP" "$DIR/platform-role-execution.dump"
rm -f "$TMP"

echo "=== 备份 jar / enterprise .env ==="
sudo install -m 640 -o root -g root /opt/ai-code/app/platform/app.jar "$DIR/platform-app.jar.prev"
sudo install -m 640 -o root -g root /opt/ai-code/app/enterprise/app.jar "$DIR/enterprise-app.jar.prev"
sudo install -m 600 -o root -g root /opt/ai-code/app/enterprise/.env "$DIR/enterprise.env"

sudo bash -c "cd '$DIR' && sha256sum * > SHA256SUMS && ls -lh && echo '--- sha256 ---' && cat SHA256SUMS"
echo "BACKUP_DIR=$DIR"
