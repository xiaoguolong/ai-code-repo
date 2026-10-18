#!/usr/bin/env bash
# 检查云端 aidemo 库：表、种子用户、Flyway 记录
set -uo pipefail
P="docker exec pg16 psql -U aidemo -d aidemo -tAc"

echo "=== 表数量 ==="
$P "select count(*) from information_schema.tables where table_schema='public'"

echo "=== 表清单 ==="
$P "select table_name from information_schema.tables where table_schema='public' order by 1" | tr '\n' ' '
echo

echo "=== platform_user 内容 ==="
$P "select id || ' | ' || username || ' | ' || role_key from platform_user order by id" 2>&1 | head -10

echo "=== platform_role 内容 ==="
$P "select role_key || ' | ' || display_name || ' | admin=' || is_admin from platform_role order by 1" 2>&1 | head -10

echo "=== flyway 历史 ==="
$P "select version || ' | ' || description || ' | ' || case when success then 'OK' else 'FAIL' end from flyway_schema_history_platform order by installed_rank" 2>&1 | head -10
