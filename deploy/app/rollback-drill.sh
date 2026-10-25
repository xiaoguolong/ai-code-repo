#!/usr/bin/env bash
# =============================================================================
# Week 24 / TD-04：回滚演练脚本（只写临时库，不碰生产库）
# -----------------------------------------------------------------------------
# 用途：把一份 pg_dump 备份恢复到「临时库 *_drill」，校验关键表行数，
#       用于季度回滚演练。生产库名不匹配时立即退出。
#
# 用法（云端 aicode 用户）：
#   DRILL_DB=aicode_drill \
#   BACKUP_FILE=/opt/ai-code/backup/<ts>/db.sql \
#   PGHOST=127.0.0.1 PGPORT=5432 PGUSER=<db-user> PGPASSWORD=<db-password> \
#   bash deploy/app/rollback-drill.sh
#
# 安全约束：
#   1) DRILL_DB 必须以 _drill 结尾；否则拒绝执行（防手滑 drop 生产库）；
#   2) 只对 DRILL_DB 做 DROP/CREATE，不读取/不修改其它库；
#   3) 脚本不打印密码；PGPASSWORD 由环境变量传入。
# =============================================================================
set -euo pipefail

: "${DRILL_DB:?DRILL_DB is required (must end with _drill)}"
: "${BACKUP_FILE:?BACKUP_FILE is required (pg_dump plain SQL or custom format)}"
PGHOST="${PGHOST:-127.0.0.1}"
PGPORT="${PGPORT:-5432}"
PGUSER="${PGUSER:?PGUSER is required}"

case "$DRILL_DB" in
  *_drill) ;;
  *) echo "[drill] refuse: DRILL_DB must end with _drill (got: $DRILL_DB)" >&2; exit 2 ;;
esac

if [ ! -f "$BACKUP_FILE" ]; then
  echo "[drill] backup file not found: $BACKUP_FILE" >&2
  exit 2
fi

echo "[drill] target=$DRILL_DB host=$PGHOST:$PGPORT user=$PGUSER"
echo "[drill] backup=$BACKUP_FILE ($(du -h "$BACKUP_FILE" | cut -f1))"

echo "[drill] recreating drill database"
psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d postgres -v ON_ERROR_STOP=1 \
  -c "DROP DATABASE IF EXISTS \"$DRILL_DB\";" \
  -c "CREATE DATABASE \"$DRILL_DB\";"

echo "[drill] restoring backup"
case "$BACKUP_FILE" in
  *.dump|*.backup|*.custom)
    pg_restore -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$DRILL_DB" --no-owner --no-privileges "$BACKUP_FILE"
    ;;
  *)
    psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$DRILL_DB" -v ON_ERROR_STOP=1 -f "$BACKUP_FILE"
    ;;
esac

echo "[drill] sanity counts"
psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$DRILL_DB" -v ON_ERROR_STOP=1 <<'SQL'
SELECT 'platform_user' AS table_name, COUNT(*) AS rows FROM platform_user
UNION ALL SELECT 'platform_role', COUNT(*) FROM platform_role
UNION ALL SELECT 'execution_record', COUNT(*) FROM execution_record
UNION ALL SELECT 'platform_approval', COUNT(*) FROM platform_approval
UNION ALL SELECT 'platform_memory', COUNT(*) FROM platform_memory;
SQL

echo "[drill] PASS — drill database is restored; production database untouched"
echo "[drill] record RTO / result in docs/deploy/week-24-ops.md section 8.3"
