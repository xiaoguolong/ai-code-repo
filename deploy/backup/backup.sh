#!/usr/bin/env bash
# ============================================================================
# 云上数据备份：PostgreSQL + 各栈 .env（配置）
# ----------------------------------------------------------------------------
# 设计取舍：
#   · 只备业务库 aidemo —— SkyWalking 的观测历史（135 表按天分表）量大价值低，
#     灾难恢复时重建即可，不进备份（需要时把 SKY_SW 设为 1）
#   · Langfuse 的 trace 存在 ClickHouse volume 里，dump 不适用；如需保留用 volume 打包
#   · .env 里是全部密码，必须一起备（否则库恢复了也连不上）—— 单独打 600 的包
#   · 保留最近 7 份，按名字排序删除最旧
# ============================================================================
set -uo pipefail

BACKUP_DIR=/opt/ai-code/backup
DUMP_DIR=$BACKUP_DIR/dumps
LOG=$BACKUP_DIR/backup.log
KEEP=7
SKY_SW=${SKY_SW:-0}

mkdir -p "$DUMP_DIR"
TS=$(date +%Y%m%d-%H%M)
log() { printf '%s %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*" | tee -a "$LOG"; }

log "=== backup start (ts=$TS) ==="

# 1) 业务库
OUT="$DUMP_DIR/aidemo-$TS.dump"
if docker exec pg16 pg_dump -U aidemo -d aidemo -Fc > "$OUT" 2>>"$LOG"; then
    log "OK   aidemo -> $(basename "$OUT") ($(du -h "$OUT" | cut -f1))"
else
    log "FAIL aidemo dump"; rm -f "$OUT"
fi

# 2) SkyWalking 存储（可选）
if [ "$SKY_SW" = "1" ]; then
    OUTF="$DUMP_DIR/skywalking-$TS.dump"
    if docker exec pg16 pg_dump -U aidemo -d skywalking -Fc > "$OUTF" 2>>"$LOG"; then
        log "OK   skywalking -> $(basename "$OUTF") ($(du -h "$OUTF" | cut -f1))"
    else
        log "FAIL skywalking dump"; rm -f "$OUTF"
    fi
fi

# 3) 配置文件（密码在里面，权限 600）
ENVF="$DUMP_DIR/env-$TS.tar.gz"
tar czf "$ENVF" -C /opt/ai-code data/.env skywalking/.env langfuse/.env app 2>>"$LOG" \
    && chmod 600 "$ENVF" && log "OK   env -> $(basename "$ENVF")"

# 4) 保留策略
COUNT=$(ls -1 "$DUMP_DIR"/aidemo-*.dump 2>/dev/null | wc -l)
if [ "$COUNT" -gt "$KEEP" ]; then
    ls -1t "$DUMP_DIR"/aidemo-*.dump | tail -n +$((KEEP+1)) | while read -r f; do
        rm -f "$f"; log "prune $(basename "$f")"
    done
fi
COUNT=$(ls -1 "$DUMP_DIR"/env-*.tar.gz 2>/dev/null | wc -l)
if [ "$COUNT" -gt "$KEEP" ]; then
    ls -1t "$DUMP_DIR"/env-*.tar.gz | tail -n +$((KEEP+1)) | while read -r f; do
        rm -f "$f"; log "prune $(basename "$f")"
    done
fi

log "=== backup done ==="
echo
echo "--- 当前备份 ---"
ls -lth "$DUMP_DIR" | head -8
echo "--- 目录占用 ---"
du -sh "$DUMP_DIR" 2>/dev/null
