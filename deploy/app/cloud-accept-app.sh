#!/usr/bin/env bash
# =============================================================================
# 云端应用验收（Week 22）——在云主机本机执行（应用只监听 127.0.0.1:8084）
# -----------------------------------------------------------------------------
# 用法：sudo bash /opt/ai-code/app/cloud-accept-app.sh   （普通用户也可，curl 不需要 root）
# 覆盖：健康 / 登录带租户 / 租户列表权限 / 真实患者分析一次 / 记忆隔离 / 限流 429
# 退出码：0 全通过；1 有失败项（逐项打印 PASS/FAIL）
# =============================================================================
set -uo pipefail

BASE="http://127.0.0.1:8084"
PASS=0
FAIL=0
TOKEN=""
ADMIN_TOKEN=""
DOCTOR_TOKEN=""

ok()   { PASS=$((PASS + 1)); echo "PASS  $*"; }
bad()  { FAIL=$((FAIL + 1)); echo "FAIL  $*"; }
json() { python3 -c "import sys,json;d=json.load(sys.stdin);print($1)" 2>/dev/null; }

login() { # $1=user $2=pass -> prints token
  curl -s -X POST "$BASE/api/v1/platform/auth/login" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"$2\"}" | json "d['data']['token']"
}
field() { # $1=json-text $2=python-expr on d
  printf '%s' "$1" | python3 -c "import sys,json;d=json.load(sys.stdin);print($2)" 2>/dev/null
}

echo "=== 1) 健康检查 ==="
CODE="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$BASE/actuator/health")"
[ "$CODE" = "200" ] && ok "health 200" || bad "health=$CODE"

echo
echo "=== 2) 登录（应带 tenantKey）==="
ADMIN_TOKEN="$(login admin admin123)"
TOKEN="$(login operator operator123)"
DOCTOR_TOKEN="$(login doctor_b doctor123)"
[ -n "$ADMIN_TOKEN" ] && ok "admin 登录" || bad "admin 登录"
[ -n "$TOKEN" ] && ok "operator 登录" || bad "operator 登录"
[ -n "$DOCTOR_TOKEN" ] && ok "doctor_b 登录" || bad "doctor_b 登录"

echo
echo "=== 3) 租户列表（管理员 200 / 运营 403）==="
TENANTS="$(curl -s "$BASE/api/v1/platform/tenants" -H "Authorization: Bearer $ADMIN_TOKEN")"
TCOUNT="$(field "$TENANTS" "len(d['data'])")"
[ "$TCOUNT" = "2" ] && ok "租户数=2" || bad "租户数=$TCOUNT"
OCODE="$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/v1/platform/tenants" -H "Authorization: Bearer $TOKEN")"
[ "$OCODE" = "403" ] && ok "运营查租户 403" || bad "运营查租户=$OCODE"

echo
echo "=== 4) 真实患者分析（真实 LLM + 真实 embedding）==="
RUN_START=$(date +%s)
RUN="$(curl -s -X POST "$BASE/api/v1/platform/agents/patient-analysis/runs" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"input":{"patientId":"P001"}}' --max-time 180)"
RUN_END=$(date +%s)
STATUS="$(field "$RUN" "d['data']['status']")"
TOKENS="$(field "$RUN" "d['data']['usage']['totalTokens']")"
OUT="$(field "$RUN" "d['data']['outputJson']")"
DEGRADED="$(field "$OUT" "d['degraded']")"
RISK="$(field "$OUT" "d['riskLevel']")"
MEMID="$(field "$OUT" "d['memoryId']")"
STEPS="$(field "$OUT" "d['totalSteps']")"
[ "$STATUS" = "COMPLETED" ] && ok "执行 COMPLETED（$((RUN_END - RUN_START))s, tokens=$TOKENS）" || bad "执行 status=$STATUS"
[ "$STEPS" = "5" ] && ok "5 步轨迹" || bad "steps=$STEPS"
[ -n "$RISK" ] && ok "风险等级=$RISK" || bad "无 riskLevel"
[ "$DEGRADED" = "False" ] && ok "模型真实生成（degraded=False）" || bad "degraded=$DEGRADED（模型不可用，已降级）"
[ -n "$MEMID" ] && ok "写入记忆 memoryId=${MEMID:0:8}…" || bad "未写入记忆"

echo
echo "=== 5) 记忆隔离（operator 有 / doctor_b 无）==="
OP_MEM="$(curl -s "$BASE/api/v1/platform/memories?patientId=P001&limit=20" -H "Authorization: Bearer $TOKEN")"
OP_COUNT="$(field "$OP_MEM" "len(d['data']['items'])")"
OP_TENANT="$(field "$OP_MEM" "d['data']['tenantKey']")"
DOC_MEM="$(curl -s "$BASE/api/v1/platform/memories?patientId=P001&limit=20" -H "Authorization: Bearer $DOCTOR_TOKEN")"
DOC_COUNT="$(field "$DOC_MEM" "len(d['data']['items'])")"
DOC_TENANT="$(field "$DOC_MEM" "d['data']['tenantKey']")"
[ "$OP_TENANT" = "default" ] && ok "operator 租户=default，记忆 $OP_COUNT 条" || bad "operator 租户=$OP_TENANT"
[ "$DOC_TENANT" = "hospital-b" ] && ok "doctor_b 租户=hospital-b，记忆 $DOC_COUNT 条（跨租户不可见）" || bad "doctor_b 租户=$DOC_TENANT"
[ "${OP_COUNT:-0}" -ge 1 ] && ok "operator 能查到自己的记忆" || bad "operator 记忆为空"
[ "${DOC_COUNT:-1}" -eq 0 ] && ok "doctor_b 查不到 operator 的记忆" || bad "doctor_b 看到 $DOC_COUNT 条（隔离失效！）"

echo
echo "=== 6) 登录限流（6 次错误密码，期望 401×5 + 429）==="
CODES=""
for i in $(seq 1 6); do
  C="$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/v1/platform/auth/login" \
        -H 'Content-Type: application/json' \
        -d '{"username":"ratelimit_probe","password":"wrong"}')"
  CODES="$CODES $C"
done
echo "       codes:$CODES"
echo "$CODES" | grep -q " 429" && ok "出现 429（限流生效）" || bad "未出现 429"
FIRST5="$(echo "$CODES" | awk '{print $1" "$2" "$3" "$4" "$5}')"
[ "$FIRST5" = "401 401 401 401 401" ] && ok "前 5 次为 401" || bad "前 5 次=$FIRST5"

echo
echo "=== 7) 指标出口 ==="
METRIC="$(curl -s "$BASE/actuator/prometheus" | grep -cE '^(agent_run_count_total|llm_tokens_total)' || true)"
[ "${METRIC:-0}" -ge 1 ] && ok "prometheus 指标行数=$METRIC" || bad "指标为空"

echo
echo "================ 汇总 ================"
echo "PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ] || exit 1
