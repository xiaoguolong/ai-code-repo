#!/usr/bin/env bash
# Week 28 云端验收：V11 + 停用/恢复 + 旧 token + 重启 seed 保留。
set -uo pipefail

BASE="http://127.0.0.1:8084"
PASS=0; FAIL=0
ok() { echo "PASS $*"; PASS=$((PASS+1)); }
bad() { echo "FAIL $*"; FAIL=$((FAIL+1)); }
eq() { if [ "$2" = "$3" ]; then ok "$1"; else bad "$1 (actual=$2 expected=$3)"; fi; }

set -a; . /opt/ai-code/app/platform/.env; set +a
PU="$SPRING_DATASOURCE_USERNAME"; PP="$SPRING_DATASOURCE_PASSWORD"; PDB="${SPRING_DATASOURCE_URL##*/}"; PDB="${PDB%%\?*}"
PSQL() { docker exec -e PGPASSWORD="$PP" pg16 psql -h 127.0.0.1 -U "$PU" -d "$PDB" -tAc "$1"; }
JGET() { local expr="$1"; shift; python3 -c "import json,sys;d=json.load(sys.stdin);print($expr)" "$@"; }
login() {
  curl -s -H 'Content-Type: application/json' -d "{\"username\":\"$1\",\"password\":\"$2\"}" \
    "$BASE/api/v1/platform/auth/login" | JGET 'd["data"]["token"]' 2>/dev/null
}

echo "### 1. V11"
eq "flyway v11" "$(PSQL "select version from flyway_schema_history_platform where version='11' and success;")" "11"
eq "disabled_at 列" "$(PSQL "select count(*) from information_schema.columns where table_name='platform_user' and column_name='disabled_at';")" "1"

VIEWER_ID=$(PSQL "select id from platform_user where username='viewer';")
echo "viewer_id=$VIEWER_ID"
ADMIN=$(login admin admin123)
VIEWER=$(login viewer viewer123)
eq "admin 登录" "$([ -n "$ADMIN" ] && echo yes || echo no)" "yes"
eq "viewer 登录" "$([ -n "$VIEWER" ] && echo yes || echo no)" "yes"

echo "### 2. 列表 + 停用"
USERS=$(curl -s -H "Authorization: Bearer $ADMIN" "$BASE/api/v1/platform/users")
eq "users 200" "$(echo "$USERS" | JGET 'd["code"]')" "SUCCESS"
eq "users 含 viewer" "$(echo "$USERS" | JGET 'sum(1 for i in d["data"]["items"] if i["username"]=="viewer")')" "1"

DISABLE=$(curl -s -X POST -H "Authorization: Bearer $ADMIN" "$BASE/api/v1/platform/users/$VIEWER_ID/disable")
echo "disable: $(echo "$DISABLE" | head -c 300)"
eq "停用 disabled=true" "$(echo "$DISABLE" | JGET 'd["data"]["disabled"]')" "True"
eq "旧 token /me 401" "$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $VIEWER" "$BASE/api/v1/platform/auth/me")" "401"
eq "停用后登录 401" "$(login viewer viewer123)" ""

echo "### 3. 重启（seed 不复活）"
systemctl restart ai-code-platform.service
health=""
for i in $(seq 1 45); do
  health=$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$BASE/actuator/health" || true)
  [ "$health" = "200" ] && break
  sleep 2
done
eq "重启后 health 200" "$health" "200"
ADMIN2=$(login admin admin123)
eq "重启后 admin 登录" "$([ -n "$ADMIN2" ] && echo yes || echo no)" "yes"
eq "重启后 viewer 仍停用(401)" "$(login viewer viewer123)" ""
eq "DB disabled_at 仍非空" "$([ -n "$(PSQL "select disabled_at from platform_user where id=$VIEWER_ID;")" ] && echo yes || echo no)" "yes"

echo "### 4. 恢复"
ENABLE=$(curl -s -X POST -H "Authorization: Bearer $ADMIN2" "$BASE/api/v1/platform/users/$VIEWER_ID/enable")
echo "enable: $(echo "$ENABLE" | head -c 300)"
eq "恢复 disabled=false" "$(echo "$ENABLE" | JGET 'd["data"]["disabled"]')" "False"
VIEWER2=$(login viewer viewer123)
eq "恢复后登录 200" "$([ -n "$VIEWER2" ] && echo yes || echo no)" "yes"

echo "### 5. 审计"
ge() { if [ "$2" -ge "$3" ] 2>/dev/null; then ok "$1"; else bad "$1 (actual=$2 min=$3)"; fi; }
ge "USER_DISABLE 审计" "$(PSQL "select count(*) from audit_log where action='USER_DISABLE';")" 1
ge "USER_ENABLE 审计" "$(PSQL "select count(*) from audit_log where action='USER_ENABLE';")" 1

echo "RESULT PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ]
