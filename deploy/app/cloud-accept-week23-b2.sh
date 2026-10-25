#!/usr/bin/env bash
# =============================================================================
# Week 23 批次 2 云端验收（V7 角色租户 + 执行记录 tenant + enterprise A1 + 409/503）
# -----------------------------------------------------------------------------
# 前置：平台/enterprise 已发布批次 2；enterprise .env 已有 ENTERPRISE_ADMIN_TOKEN。
# 注意：本脚本会短暂 stop/start 平台（503 用例），并临时解绑/恢复 operator 的 SSO 映射。
# 退出码：0 全通过；1 有失败项。
# =============================================================================
set -uo pipefail

PBASE="http://127.0.0.1:8084"
EBASE="http://127.0.0.1:8081"
PASS=0
FAIL=0

ok()  { PASS=$((PASS + 1)); echo "PASS  $*"; }
bad() { FAIL=$((FAIL + 1)); echo "FAIL  $*"; }
json() { python3 -c "import sys,json;d=json.load(sys.stdin);print($1)" 2>/dev/null; }
field() { printf '%s' "$1" | python3 -c "import sys,json;d=json.load(sys.stdin);print($2)" 2>/dev/null; }
login() { curl -s -X POST "$PBASE/api/v1/platform/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$1\",\"password\":\"$2\"}" | json "d['data']['token']"; }

# enterprise 运维令牌只从运行时 env 读取，不打印
set -a
# shellcheck disable=SC1091
. /opt/ai-code/app/enterprise/.env
set +a
ADMIN_TOKEN="${ENTERPRISE_ADMIN_TOKEN:-}"

echo "=== 0) 服务与令牌配置 ==="
[ -n "$ADMIN_TOKEN" ] && ok "enterprise 运维令牌已配置（长度 ${#ADMIN_TOKEN}）" || bad "ENTERPRISE_ADMIN_TOKEN 为空"
for svc in ai-code-platform ai-code-enterprise; do
  [ "$(systemctl is-active "$svc")" = "active" ] && ok "$svc active" || bad "$svc 未运行"
done

echo
echo "=== 1) enterprise 运维端点（无令牌 403 / 有令牌 200）==="
CODE="$(curl -s -o /tmp/b2-no-token.json -w '%{http_code}' "$EBASE/api/v1/admin/sso-links")"
[ "$CODE" = "403" ] && ok "无令牌 403" || bad "无令牌=$CODE"
CODE="$(curl -s -o /tmp/b2-bad-token.json -w '%{http_code}' "$EBASE/api/v1/admin/sso-links" -H 'X-Admin-Token: wrong')"
[ "$CODE" = "403" ] && ok "错令牌 403" || bad "错令牌=$CODE"
LINKS="$(curl -s "$EBASE/api/v1/admin/sso-links?limit=50" -H "X-Admin-Token: $ADMIN_TOKEN")"
LCOUNT="$(field "$LINKS" "len(d['data']['items'])")"
[ -n "$LCOUNT" ] && ok "有令牌列表 200（$LCOUNT 条）" || bad "有令牌列表失败"

echo
echo "=== 2) 平台角色按租户 / 执行记录 tenant ==="
PTOKEN="$(login operator operator123)"
ATOKEN="$(login admin admin123)"
[ -n "$PTOKEN" ] && ok "operator 登录" || bad "operator 登录"
[ -n "$ATOKEN" ] && ok "admin 登录" || bad "admin 登录"
ROLES="$(curl -s "$PBASE/api/v1/platform/roles?tenantKey=hospital-b" -H "Authorization: Bearer $ATOKEN")"
R_TENANTS="$(field "$ROLES" "sorted(set(i['tenantKey'] for i in d['data']))")"
echo "$R_TENANTS" | grep -q 'hospital-b' && ok "hospital-b 角色可见（$R_TENANTS）" || bad "角色租户=$R_TENANTS"
EXECS="$(curl -s "$PBASE/api/v1/platform/executions" -H "Authorization: Bearer $PTOKEN")"
EXEC_TENANT="$(field "$EXECS" "d['data'][0]['tenantKey'] if d['data'] else 'NONE'")"
[ "$EXEC_TENANT" = "default" ] && ok "执行记录 tenantKey=default" || bad "执行记录 tenantKey=$EXEC_TENANT"

echo
echo "=== 3) 建链/解绑（探针本地用户，deleteLocalUser=true）==="
PROBE="w23probe$(date +%s)"
REG="$(curl -s -X POST "$EBASE/api/v1/auth/register" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$PROBE\",\"password\":\"Probe123!\"}")"
LOCAL_ID="$(field "$REG" "d['data']['userId']")"
[ -n "$LOCAL_ID" ] && ok "注册探针本地用户 id=$LOCAL_ID" || bad "注册探针失败"
PLATFORM_PROBE=999001
LINK="$(curl -s -X POST "$EBASE/api/v1/admin/sso-links" -H "X-Admin-Token: $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"platformUserId\":$PLATFORM_PROBE,\"enterpriseUserId\":$LOCAL_ID,\"tenantKey\":\"default\"}")"
LINK_OK="$(field "$LINK" "d['data']['platformUserId']")"
[ "$LINK_OK" = "$PLATFORM_PROBE" ] && ok "建链 platform=$PLATFORM_PROBE local=$LOCAL_ID" || bad "建链失败"
LINKS2="$(curl -s "$EBASE/api/v1/admin/sso-links?limit=200" -H "X-Admin-Token: $ADMIN_TOKEN")"
echo "$LINKS2" | grep -q "\"platformUserId\":$PLATFORM_PROBE" && ok "列表含探针映射" || bad "列表不含探针映射"
UNLINK="$(curl -s -X DELETE "$EBASE/api/v1/admin/sso-links/$PLATFORM_PROBE?deleteLocalUser=true" -H "X-Admin-Token: $ADMIN_TOKEN")"
U_DEL="$(field "$UNLINK" "d['data']['localUserDeleted']")"
[ "$U_DEL" = "True" ] && ok "解绑并删本地用户" || bad "解绑结果 localUserDeleted=$U_DEL"

echo
echo "=== 4) 409：本地同名但未建链 ==="
# 找到 operator（platform user 2）的映射；没有就先 SSO 登录建链
OP_LOCAL="$(field "$LINKS2" "next((i['enterpriseUserId'] for i in d['data']['items'] if i['platformUserId']==2), '')")"
if [ -z "$OP_LOCAL" ]; then
  curl -s -X POST "$EBASE/api/v1/auth/login" -H 'Content-Type: application/json' \
    -d '{"username":"operator","password":"operator123"}' >/dev/null
  LINKS2="$(curl -s "$EBASE/api/v1/admin/sso-links?limit=200" -H "X-Admin-Token: $ADMIN_TOKEN")"
  OP_LOCAL="$(field "$LINKS2" "next((i['enterpriseUserId'] for i in d['data']['items'] if i['platformUserId']==2), '')")"
fi
[ -n "$OP_LOCAL" ] && ok "operator 映射存在（local=$OP_LOCAL）" || bad "operator 映射不存在"
if [ -n "$OP_LOCAL" ]; then
  curl -s -X DELETE "$EBASE/api/v1/admin/sso-links/2" -H "X-Admin-Token: $ADMIN_TOKEN" >/dev/null
  C409="$(curl -s -o /tmp/b2-409.json -w '%{http_code}' -X POST "$EBASE/api/v1/auth/login" \
    -H 'Content-Type: application/json' -d '{"username":"operator","password":"operator123"}')"
  [ "$C409" = "409" ] && ok "同名未建链登录 409" || bad "409 用例=$C409"
  curl -s -X POST "$EBASE/api/v1/admin/sso-links" -H "X-Admin-Token: $ADMIN_TOKEN" \
    -H 'Content-Type: application/json' \
    -d "{\"platformUserId\":2,\"enterpriseUserId\":$OP_LOCAL,\"tenantKey\":\"default\"}" >/dev/null
  CLOGIN="$(curl -s -o /dev/null -w '%{http_code}' -X POST "$EBASE/api/v1/auth/login" \
    -H 'Content-Type: application/json' -d '{"username":"operator","password":"operator123"}')"
  [ "$CLOGIN" = "200" ] && ok "恢复映射后登录 200" || bad "恢复后登录=$CLOGIN"
fi

echo
echo "=== 5) 503：平台短暂不可达 ==="
sudo systemctl stop ai-code-platform
sleep 3
C503="$(curl -s -o /tmp/b2-503.json -w '%{http_code}' -X POST "$EBASE/api/v1/auth/login" \
  -H 'Content-Type: application/json' -d '{"username":"operator","password":"operator123"}')"
sudo systemctl start ai-code-platform
for _ in $(seq 1 30); do
  [ "$(curl -s -o /dev/null -w '%{http_code}' "$PBASE/actuator/health")" = "200" ] && break
  sleep 1
done
[ "$C503" = "503" ] && ok "平台不可达 503" || bad "503 用例=$C503"
C_RECOVER="$(curl -s -o /dev/null -w '%{http_code}' -X POST "$EBASE/api/v1/auth/login" \
  -H 'Content-Type: application/json' -d '{"username":"operator","password":"operator123"}')"
[ "$C_RECOVER" = "200" ] && ok "平台恢复后登录 200" || bad "恢复后登录=$C_RECOVER"

echo
echo "================ 汇总 ================"
echo "PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ] || exit 1
