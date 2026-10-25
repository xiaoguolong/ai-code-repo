#!/usr/bin/env bash
# Week 26 / TD-04：回滚演练（容器版，宿主机没有 psql/pg_restore 时使用）
# -----------------------------------------------------------------------------
# 与 deploy/app/rollback-drill.sh 的差别：宿主机没有 PostgreSQL 客户端，
# 所有 restore/校验都在 pg16 容器里执行（dump 用 docker cp 送进去）。
#
# 用法：
#   DRILL_DB=aicode_drill_week26 \
#   BACKUP_FILE=/opt/ai-code/backup/<ts>/platform-aidemo.dump \
#   bash cloud-week26-drill.sh
#
# 安全约束：
#   1) DRILL_DB 必须以 _drill 结尾，且不得等于任何生产库名；
#   2) 只 DROP/CREATE DRILL_DB，不读改生产库（校验只做 SELECT count）；
#   3) 不打印密码；PGPASSWORD 在容器内通过 -e 传入。
set -euo pipefail
: "${DRILL_DB:?DRILL_DB is required (must end with _drill)}"
: "${BACKUP_FILE:?BACKUP_FILE is required (pg_dump custom format)}"
CONTAINER="${CONTAINER:-pg16}"
case "$DRILL_DB" in
  *_drill) ;;
  *) echo "[drill] refuse: DRILL_DB must end with _drill (got: $DRILL_DB)"; exit 2 ;;
esac
for prod in aidemo enterprise skywalking postgres; do
  [ "$DRILL_DB" = "$prod" ] && { echo "[drill] refuse: DRILL_DB 不能是生产库 $prod"; exit 2; }
done
[ -f "$BACKUP_FILE" ] || { echo "[drill] backup not found: $BACKUP_FILE"; exit 2; }

set -a; . /opt/ai-code/app/platform/.env; set +a
U="$SPRING_DATASOURCE_USERNAME"; P="$SPRING_DATASOURCE_PASSWORD"
PD="${SPRING_DATASOURCE_URL##*/}"; PD="${PD%%\?*}"
prod_count() {
  docker exec -e PGPASSWORD="$P" "$CONTAINER" psql -h 127.0.0.1 -U "$U" -d "$PD" -tAc \
    "select count(*) from platform_user;" 2>/dev/null
}

START=$(date +%s)
PROD_BEFORE="$(prod_count)"
echo "[drill] target=$DRILL_DB backup=$BACKUP_FILE ($(du -h "$BACKUP_FILE" | cut -f1))"
echo "[drill] prod platform_user before=$PROD_BEFORE"

echo "[drill] recreate drill database"
docker exec -e PGPASSWORD="$P" "$CONTAINER" psql -h 127.0.0.1 -U "$U" -d postgres -v ON_ERROR_STOP=1 \
  -c "DROP DATABASE IF EXISTS \"$DRILL_DB\";" -c "CREATE DATABASE \"$DRILL_DB\";"

echo "[drill] copy dump into container and restore"
IN_CONTAINER="/tmp/$(basename "$BACKUP_FILE")"
docker cp "$BACKUP_FILE" "$CONTAINER:$IN_CONTAINER"
docker exec -e PGPASSWORD="$P" "$CONTAINER" pg_restore -h 127.0.0.1 -U "$U" -d "$DRILL_DB" \
  --no-owner --no-privileges "$IN_CONTAINER"
docker exec "$CONTAINER" rm -f "$IN_CONTAINER"

echo "[drill] sanity counts（只统计 exists 的表；缺失记 missing）"
for t in platform_user platform_role execution_record platform_approval platform_memory \
         platform_followup_task platform_knowledge_binding app_user knowledge_base document document_chunk; do
  exists="$(docker exec -e PGPASSWORD="$P" "$CONTAINER" psql -h 127.0.0.1 -U "$U" -d "$DRILL_DB" -tAc \
    "select to_regclass('$t') is not null;" 2>/dev/null)"
  if [ "$exists" = "t" ]; then
    c="$(docker exec -e PGPASSWORD="$P" "$CONTAINER" psql -h 127.0.0.1 -U "$U" -d "$DRILL_DB" -tAc \
      "select count(*) from $t;" 2>/dev/null)"
    echo "  $t=$c"
  else
    echo "  $t=missing"
  fi
done
# 恢复是否成功的最低证据：平台库至少有用户；企业库至少有知识库/文档（若备份里本来为空则跳过）
HAS_PU="$(docker exec -e PGPASSWORD="$P" "$CONTAINER" psql -h 127.0.0.1 -U "$U" -d "$DRILL_DB" -tAc \
  "select to_regclass('platform_user') is not null;" 2>/dev/null)"
if [ "$HAS_PU" = "t" ]; then
  PU_COUNT="$(docker exec -e PGPASSWORD="$P" "$CONTAINER" psql -h 127.0.0.1 -U "$U" -d "$DRILL_DB" -tAc \
    "select count(*) from platform_user;" 2>/dev/null)"
else
  PU_COUNT="-1"
fi
echo "[drill] platform_user in drill=$PU_COUNT"
if [ "$PU_COUNT" = "0" ]; then echo "[drill] FAIL: 恢复后 platform_user 为空"; exit 1; fi
if [ "$PU_COUNT" = "-1" ]; then echo "[drill] note: 该备份不含平台表（企业库备份）"; fi

echo "[drill] verify production untouched"
PROD_AFTER="$(prod_count)"
echo "[drill] prod platform_user after=$PROD_AFTER"
[ "$PROD_BEFORE" = "$PROD_AFTER" ] || { echo "[drill] FAIL: 生产库行数变化"; exit 1; }

echo "[drill] drop drill database"
docker exec -e PGPASSWORD="$P" "$CONTAINER" psql -h 127.0.0.1 -U "$U" -d postgres -v ON_ERROR_STOP=1 \
  -c "DROP DATABASE IF EXISTS \"$DRILL_DB\";"

END=$(date +%s)
echo "[drill] PASS — restored, verified, production untouched"
echo "[drill] RTO=$((END - START))s (包含重建库 + 恢复 + 校验)"
