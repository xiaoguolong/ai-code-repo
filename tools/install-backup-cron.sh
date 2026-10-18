#!/usr/bin/env bash
# 安装每日备份 cron，并做一次「恢复演练」验证 dump 可用
set -uo pipefail

echo "=== 1) 安装 cron（每天 03:30，Asia/Shanghai）==="
CRON_LINE="30 3 * * * /bin/bash /opt/ai-code/backup/backup.sh >/dev/null 2>&1"
CURRENT=$(crontab -l 2>/dev/null || true)
if printf '%s\n' "$CURRENT" | grep -qF '/opt/ai-code/backup/backup.sh'; then
    echo "  已存在，跳过"
else
    { printf '%s\n' "$CURRENT"; printf '%s\n' "$CRON_LINE"; } | sed '/^$/d' | crontab -
    echo "  已安装"
fi
echo "  当前 crontab:"
crontab -l 2>/dev/null | sed 's/^/    /'
echo "  时区: $(timedatectl show -p Timezone --value)"

echo
echo "=== 2) 恢复演练：把最新 dump 灌进临时库 ==="
LATEST=$(ls -1t /opt/ai-code/backup/dumps/aidemo-*.dump 2>/dev/null | head -1)
if [ -z "$LATEST" ]; then echo "  没有 dump，跳过"; exit 0; fi
echo "  使用: $(basename "$LATEST")"

docker exec pg16 psql -U aidemo -d postgres -c "DROP DATABASE IF EXISTS aidemo_restore_test" >/dev/null 2>&1
docker exec pg16 psql -U aidemo -d postgres -c "CREATE DATABASE aidemo_restore_test OWNER aidemo" >/dev/null 2>&1

if docker exec -i pg16 pg_restore -U aidemo -d aidemo_restore_test --no-owner < "$LATEST" 2>/tmp/restore.err; then
    TABLES=$(docker exec pg16 psql -U aidemo -d aidemo_restore_test -tAc \
        "select count(*) from information_schema.tables where table_schema='public'" | tr -d '\r')
    USERS=$(docker exec pg16 psql -U aidemo -d aidemo_restore_test -tAc \
        "select count(*) from platform_user" 2>/dev/null | tr -d '\r')
    EXEC=$(docker exec pg16 psql -U aidemo -d aidemo_restore_test -tAc \
        "select count(*) from execution_record" 2>/dev/null | tr -d '\r')
    echo "  恢复后: 表=$TABLES  platform_user=$USERS  execution_record=$EXEC"
    if [ "$TABLES" -ge 8 ] && [ "$USERS" -eq 3 ]; then
        echo "  ✅ 恢复演练通过（结构与种子数据完整）"
    else
        echo "  ⚠️ 恢复结果与预期不符"
    fi
else
    echo "  ❌ pg_restore 失败："
    head -5 /tmp/restore.err | sed 's/^/    /'
fi

echo
echo "=== 3) 清理演练库 ==="
docker exec pg16 psql -U aidemo -d postgres -c "DROP DATABASE IF EXISTS aidemo_restore_test" >/dev/null 2>&1 && echo "  已删除 aidemo_restore_test"
rm -f /tmp/restore.err
