#!/usr/bin/env bash
# Week 26 · 三 Agent 回归 + 安全语义（限流 429 / 未绑定 404）
# 真实调用模型；不修改业务数据（除正常的执行记录/随访任务）。
set -uo pipefail
BASE="http://127.0.0.1:8084"
PASS=0; FAIL=0
ok()  { PASS=$((PASS + 1)); echo "PASS  $*"; }
bad() { FAIL=$((FAIL + 1)); echo "FAIL  $*"; }
json() { python3 -c "import sys,json;d=json.load(sys.stdin);print($1)" 2>/dev/null; }
login() {
  curl -s -X POST "$BASE/api/v1/platform/auth/login" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"$2\"}"
}

echo "=== 0) 登录 ==="
ADMIN="$(login admin admin123 | json "d['data']['token']")"
OPER="$(login operator operator123 | json "d['data']['token']")"
[ -n "$ADMIN" ] && ok "admin 登录" || bad "admin 登录"
[ -n "$OPER" ] && ok "operator 登录" || bad "operator 登录"

echo
echo "=== 1) Agent 1 患者分析（P001，非 HIGH 应直接完成）==="
R1="$(curl -s --max-time 180 -X POST "$BASE/api/v1/platform/agents/patient-analysis/runs" \
  -H "Authorization: Bearer $OPER" -H 'Content-Type: application/json' -d '{"input":{"patientId":"P001"}}')"
S1="$(printf '%s' "$R1" | json "d['data']['status']")"
D1="$(printf '%s' "$R1" | json "d['data']['outputJson']" | json "d['degraded']")"
[ "$S1" = "COMPLETED" ] && ok "patient-analysis COMPLETED（degraded=$D1）" || bad "patient-analysis status=$S1"
echo "INFO  executionId=$(printf '%s' "$R1" | json "d['data']['executionId']") stepCount=$(printf '%s' "$R1" | json "d['data']['outputJson']" | json "len(d.get('steps',[]))")"

echo
echo "=== 2) Agent 4 随访（P003）==="
R2="$(curl -s --max-time 180 -X POST "$BASE/api/v1/platform/agents/followup/runs" \
  -H "Authorization: Bearer $OPER" -H 'Content-Type: application/json' -d '{"input":{"patientId":"P003"}}')"
S2="$(printf '%s' "$R2" | json "d['data']['status']")"
C2="$(printf '%s' "$R2" | json "d['data']['outputJson']" | json "len(d.get('taskIds',[]))")"
[ "$S2" = "COMPLETED" ] && ok "followup COMPLETED（tasks=$C2）" || bad "followup status=$S2"

echo
echo "=== 3) Agent 3 知识库问答（kb=1）==="
R3="$(curl -s --max-time 180 -X POST "$BASE/api/v1/platform/agents/knowledge-qa/runs" \
  -H "Authorization: Bearer $OPER" -H 'Content-Type: application/json' \
  -d '{"input":{"knowledgeBaseId":1,"question":"随访频率和质控指标是什么？"}}')"
S3="$(printf '%s' "$R3" | json "d['data']['status']")"
OUT3="$(printf '%s' "$R3" | json "d['data']['outputJson']")"
H3="$(printf '%s' "$OUT3" | json "d['hitCount']")"
CIT3="$(printf '%s' "$OUT3" | json "len(d.get('citations') or [])")"
DG3="$(printf '%s' "$OUT3" | json "d['degraded']")"
[ "$S3" = "COMPLETED" ] && ok "knowledge-qa COMPLETED（hits=$H3 citations=$CIT3 degraded=$DG3）" || bad "knowledge-qa status=$S3"
[ "${H3:-0}" -ge 1 ] && [ "${CIT3:-0}" -ge 1 ] && ok "检索命中且带引用" || bad "无引用/无命中"

echo
echo "=== 4) 安全语义 ==="
U404="$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/v1/platform/agents/knowledge-qa/runs" \
  -H "Authorization: Bearer $OPER" -H 'Content-Type: application/json' \
  -d '{"input":{"knowledgeBaseId":987654,"question":"q"}}')"
[ "$U404" = "404" ] && ok "未绑定知识库 404" || bad "未绑定返回 $U404"

# 限流：用一次性用户名，避免锁住真实账号（按 IP+用户名 计数，5 次失败封 5 分钟）
RL=""
for i in 1 2 3 4 5; do
  RL="$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/v1/platform/auth/login" \
    -H 'Content-Type: application/json' -d '{"username":"rl_probe_week26","password":"wrong"}')"
done
LAST="$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/v1/platform/auth/login" \
  -H 'Content-Type: application/json' -d '{"username":"rl_probe_week26","password":"wrong"}')"
echo "INFO  前 5 次最后一次=$RL，第 6 次=$LAST"
[ "$LAST" = "429" ] && ok "登录限流返回 429" || bad "登录限流=$LAST（RL=$RL）"

echo
echo "=== 结果：PASS=$PASS FAIL=$FAIL ==="
[ "$FAIL" -eq 0 ] || exit 1
