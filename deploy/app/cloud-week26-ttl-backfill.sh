#!/usr/bin/env bash
# Week 26 / TD-05：旧记忆 TTL 一次性回填（幂等）
# 口径：expires_at = created_at + 90 天；只补 expires_at IS NULL 的行。
# 前置：/opt/ai-code/backup/week26-<ts>/ 已有 platform dump（本周已备份）。
set -euo pipefail
SQL="${SQL:-/opt/ai-code/app/ttl-backfill-week24.sql}"
[ -f "$SQL" ] || { echo "FAIL: 缺少 $SQL"; exit 2; }

set -a; . /opt/ai-code/app/platform/.env; set +a
U="$SPRING_DATASOURCE_USERNAME"; P="$SPRING_DATASOURCE_PASSWORD"
D="${SPRING_DATASOURCE_URL##*/}"; D="${D%%\?*}"
psql_db() { docker exec -e PGPASSWORD="$P" pg16 psql -h 127.0.0.1 -U "$U" -d "$D" -tAc "$1"; }

BEFORE_NULL="$(psql_db "select count(*) from platform_memory where expires_at is null;")"
BEFORE_TOTAL="$(psql_db "select count(*) from platform_memory;")"
echo "[ttl] before: total=$BEFORE_TOTAL null=$BEFORE_NULL"
[ "$BEFORE_NULL" = "0" ] && { echo "[ttl] 已是回填后状态，无需执行"; exit 0; }

echo "[ttl] apply $SQL"
docker exec -i -e PGPASSWORD="$P" pg16 psql -h 127.0.0.1 -U "$U" -d "$D" -v ON_ERROR_STOP=1 < "$SQL"

AFTER_NULL="$(psql_db "select count(*) from platform_memory where expires_at is null;")"
AFTER_TTL="$(psql_db "select count(*) from platform_memory where expires_at is not null;")"
echo "[ttl] after: null=$AFTER_NULL with_ttl=$AFTER_TTL"
[ "$AFTER_NULL" = "0" ] || { echo "[ttl] FAIL: 仍有 NULL"; exit 1; }

echo "[ttl] sample（只打印时间，不打印记忆正文）"
psql_db "select memory_id||'|'||created_at||'|'||expires_at from platform_memory order by created_at limit 3;"
echo "[ttl] ROWS 回填完成；回滚方式：从 /opt/ai-code/backup/week26-*/platform-*.dump 恢复 platform_memory"
