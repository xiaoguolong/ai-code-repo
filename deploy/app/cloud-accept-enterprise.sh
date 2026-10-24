#!/usr/bin/env bash
# =============================================================================
# enterprise + SSO 真机验收（Week 22 批次 2）
# -----------------------------------------------------------------------------
# 前提：平台 8084 在跑；enterprise 8081 在跑；enterprise .env 里 SSO_ENABLED=true
# 用法：bash cloud-accept-enterprise.sh
# 覆盖：进程可达 / Flyway 版本 / SSO 首次登录自动开户 / 二次登录复用 / 本地会话可用
# =============================================================================
set -uo pipefail

ENT="http://127.0.0.1:8081"
PLAT="http://127.0.0.1:8084"
PASS=0
FAIL=0
ok()  { PASS=$((PASS + 1)); echo "PASS  $*"; }
bad() { FAIL=$((FAIL + 1)); echo "FAIL  $*"; }
field() { printf '%s' "$1" | python3 -c "import sys,json;d=json.load(sys.stdin);print($2)" 2>/dev/null; }

echo "=== 1) 进程可达（/api/v1/auth/me 未登录应为 401）==="
CODE="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$ENT/api/v1/auth/me")"
[ "$CODE" = "401" ] && ok "enterprise 已在监听（401）" || bad "enterprise /auth/me=$CODE"

echo
echo "=== 2) Flyway 版本（应 V1-V5 + V7，无 V6）==="
VERS="$(set -a; . /opt/ai-code/data/.env; set +a; docker exec pg16 psql -U "$POSTGRES_USER" -d enterprise -tAc "select coalesce(string_agg(version, ',' order by installed_rank),'') from flyway_schema_history")"
echo "       versions: $VERS"
echo "$VERS" | grep -q '7' && ok "V7（sso_user_link）已应用" || bad "V7 未应用"
if echo "$VERS" | grep -q '6'; then bad "V6（pgvector）意外执行"; else ok "V6（pgvector）已跳过"; fi

echo
echo "=== 3) SSO 首次登录（平台账号 -> 本地自动开户 + 建链）==="
FIRST="$(curl -s --max-time 20 -X POST "$ENT/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"operator","password":"operator123"}')"
FCODE="$(field "$FIRST" "d['code']")"
FTOKEN="$(field "$FIRST" "d['data']['token']")"
FUID="$(field "$FIRST" "d['data']['user']['userId']")"
FUSER="$(field "$FIRST" "d['data']['user']['username']")"
[ "$FCODE" = "SUCCESS" ] && ok "SSO 登录成功（user=$FUSER userId=$FUID）" || bad "SSO 登录失败：$FIRST"
[ -n "$FUID" ] && ok "返回本地 userId=$FUID" || bad "未返回 userId"
[ -n "$FTOKEN" ] && ok "拿到本地会话 token（长度 ${#FTOKEN}）" || bad "未拿到 token"

echo
echo "=== 4) SSO 二次登录（应复用同一条链，不重复开户）==="
SECOND="$(curl -s --max-time 20 -X POST "$ENT/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"operator","password":"operator123"}')"
SUID="$(field "$SECOND" "d['data']['user']['userId']")"
[ -n "$SUID" ] && [ "$SUID" = "$FUID" ] && ok "二次登录复用同一本地用户（userId=$SUID）" || bad "二次登录产生新用户（$FUID -> $SUID）"

echo
echo "=== 5) 建链与本地用户落库 ==="
LINK="$(set -a; . /opt/ai-code/data/.env; set +a; docker exec pg16 psql -U "$POSTGRES_USER" -d enterprise -tAc "select platform_user_id||'->'||enterprise_user_id from sso_user_link order by platform_user_id")"
echo "       links: $(echo "$LINK" | tr '\n' ' ')"
echo "$LINK" | grep -q -- '->' && ok "sso_user_link 已写入" || bad "sso_user_link 为空"
USERS="$(set -a; . /opt/ai-code/data/.env; set +a; docker exec pg16 psql -U "$POSTGRES_USER" -d enterprise -tAc "select count(*) from app_user")"
[ "${USERS:-0}" -ge 1 ] && ok "app_user 已开户（$USERS 个）" || bad "app_user 为空"

echo
echo "=== 6) 本地会话可用（/auth/me）==="
ME="$(curl -s --max-time 5 "$ENT/api/v1/auth/me" -H "satoken: $FTOKEN")"
MUSER="$(field "$ME" "d['data']['username']")"
[ "$MUSER" = "operator" ] && ok "本地会话有效（me=$MUSER）" || bad "本地会话无效：$ME"

echo
echo "=== 7) 平台用同一账号仍可独立登录（身份源未受影响）==="
PCODE="$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 -X POST "$PLAT/api/v1/platform/auth/login" \
  -H 'Content-Type: application/json' -d '{"username":"operator","password":"operator123"}')"
[ "$PCODE" = "200" ] && ok "平台登录仍 200" || bad "平台登录=$PCODE"

echo
echo "================ 汇总 ================"
echo "PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ] || exit 1
