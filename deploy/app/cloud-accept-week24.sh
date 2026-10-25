#!/usr/bin/env bash
# =============================================================================
# Week 24 云端验收 —— Agent 2 运营分析 + Agent 4 随访 + 观测/一致性收口
# -----------------------------------------------------------------------------
# 前置：
#   1) 平台已发布 Week 24 代码并重启，Flyway 已应用 V8（日志 now at version v8）；
#   2) seed 用户：admin/admin123、operator/operator123、viewer/viewer123；
#   3) 演示数据：P002 = HIGH 风险；P007 在 operator 数据域（P001–P010）内。
# 用法：bash /opt/ai-code/app/cloud-accept-week24.sh
# 退出码：0 全通过；1 有失败项（逐项打印 PASS/FAIL）
# 注意：会真实调用模型（运营日报 1 次 + 随访 1 次 + 患者分析 1 次），并写入随访任务/记忆。
# =============================================================================
set -uo pipefail

BASE="http://127.0.0.1:8084"
PASS=0
FAIL=0
TOKEN=""
ADMIN_TOKEN=""

ok()   { PASS=$((PASS + 1)); echo "PASS  $*"; }
bad()  { FAIL=$((FAIL + 1)); echo "FAIL  $*"; }
field() { printf '%s' "$1" | python3 -c "import sys,json;d=json.load(sys.stdin);print($2)" 2>/dev/null; }
code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }

login() {
  curl -s -X POST "$BASE/api/v1/platform/auth/login" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"$2\"}" \
    | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])" 2>/dev/null
}

echo "=== 1) 健康检查与迁移版本 ==="
[ "$(code --max-time 5 "$BASE/actuator/health")" = "200" ] && ok "health 200" || bad "health not 200"
ADMIN_TOKEN="$(login admin admin123)"
TOKEN="$(login operator operator123)"
[ -n "$ADMIN_TOKEN" ] && ok "admin 登录" || bad "admin 登录"
[ -n "$TOKEN" ] && ok "operator 登录" || bad "operator 登录"

echo
echo "=== 2) Agent 2 运营日报（admin）==="
OPS="$(curl -s -X POST "$BASE/api/v1/platform/agents/operations-analysis/runs" \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"input":{"windowDays":3}}' --max-time 180)"
OPS_STATUS="$(field "$OPS" "d['data']['status']")"
OPS_OUT="$(field "$OPS" "d['data']['outputJson']")"
[ "$OPS_STATUS" = "COMPLETED" ] && ok "运营日报 COMPLETED" || bad "运营日报 status=$OPS_STATUS"
echo "$OPS_OUT" | grep -q '"reportType": "DAILY"\|"reportType":"DAILY"' \
  && ok "reportType=DAILY" || bad "reportType 缺失"
echo "$OPS_OUT" | grep -q '"failureRate"' && ok "summary.failureRate 存在" || bad "failureRate 缺失"
echo "$OPS_OUT" | grep -q '"anomalies"' && ok "anomalies 存在" || bad "anomalies 缺失"
echo "$OPS_OUT" | python3 -c "
import sys,json
d=json.load(sys.stdin); s=d['summary']
assert 'pendingApproval' in s and 'totalTokens' in s, s
print('INFO  window=%s..%s runs=%s failed=%s pending=%s degraded=%s' % (
  d['windowStart'], d['windowEnd'], s['totalRuns'], s['failed'], s['pendingApproval'], d['degraded']))
" && ok "summary 字段齐全" || bad "summary 字段缺失"

OPS_DENY="$(code -X POST "$BASE/api/v1/platform/agents/operations-analysis/runs" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"input":{"windowDays":3}}')"
[ "$OPS_DENY" = "403" ] && ok "operator 调日报 403" || bad "operator 调日报=$OPS_DENY"

echo
echo "=== 3) Agent 4 随访（operator / P007）==="
FU="$(curl -s -X POST "$BASE/api/v1/platform/agents/followup/runs" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"input":{"patientId":"P007"}}' --max-time 180)"
FU_STATUS="$(field "$FU" "d['data']['status']")"
FU_OUT="$(field "$FU" "d['data']['outputJson']")"
[ "$FU_STATUS" = "COMPLETED" ] && ok "随访计划 COMPLETED" || bad "随访 status=$FU_STATUS"
TASK_ID="$(printf '%s' "$FU_OUT" | python3 -c "import sys,json;d=json.load(sys.stdin);print(d['taskIds'][0])" 2>/dev/null)"
TASK_COUNT="$(printf '%s' "$FU_OUT" | python3 -c "import sys,json;d=json.load(sys.stdin);print(len(d['taskIds']))" 2>/dev/null)"
[ "${TASK_COUNT:-0}" -ge 1 ] && ok "生成随访任务 count=$TASK_COUNT" || bad "未生成任务"

LIST="$(curl -s "$BASE/api/v1/platform/followup-tasks?status=PENDING&patientId=P007" \
  -H "Authorization: Bearer $TOKEN")"
field "$LIST" "d['data']['total']" | grep -qE '^[1-9]' && ok "任务列表非空" || bad "任务列表为空"

REM="$(curl -s "$BASE/api/v1/platform/followup-tasks/reminders?days=7" \
  -H "Authorization: Bearer $TOKEN")"
echo "$REM" | grep -q '"overdue"' && ok "提醒含 overdue/dueSoon" || bad "提醒结构缺失"

DONE="$(curl -s -X POST "$BASE/api/v1/platform/followup-tasks/$TASK_ID/complete" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"note":"云端验收"}')"
[ "$(field "$DONE" "d['data']['status']")" = "COMPLETED" ] && ok "完成任务" || bad "完成任务失败"
AGAIN="$(code -X POST "$BASE/api/v1/platform/followup-tasks/$TASK_ID/complete" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"note":"again"}')"
[ "$AGAIN" = "409" ] && ok "重复完成 409" || bad "重复完成=$AGAIN"

echo
echo "=== 4) 审批联动（P002 HIGH → 批准 → 3 条任务）==="
RUN="$(curl -s -X POST "$BASE/api/v1/platform/agents/patient-analysis/runs" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"input":{"patientId":"P002"}}' --max-time 180)"
APPROVAL_ID="$(field "$RUN" "d['data']['outputJson']" | \
  python3 -c "import sys,json;d=json.load(sys.stdin);print(d['approvalId'])" 2>/dev/null)"
if [ -n "$APPROVAL_ID" ]; then
  ok "P002 生成待审 approvalId"
  DEC="$(curl -s -X POST "$BASE/api/v1/platform/approvals/$APPROVAL_ID/decision" \
    -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
    -d '{"approved":true,"comment":"云端验收"}')"
  [ "$(field "$DEC" "d['data']['status']")" = "APPROVED" ] && ok "批准成功" || bad "批准失败"
  [ "$(field "$DEC" "d['data']['executionStatus']")" = "COMPLETED" ] && ok "执行 COMPLETED" || bad "执行状态异常"
  [ -n "$(field "$DEC" "d['data']['memoryId']")" ] && ok "写入记忆" || bad "未写记忆"
  # 注意：field 输出的是 Python repr（单引号），不能直接再喂给 json.load；
  # 必须从原始响应体里取数组长度（Week 26 真机暴露的脚本缺陷）。
  FT_COUNT="$(printf '%s' "$DEC" | python3 -c "import sys,json;d=json.load(sys.stdin);print(len(d['data'].get('followUpTaskIds') or []))" 2>/dev/null)"
  [ "${FT_COUNT:-0}" = "3" ] && ok "生成 3 条随访任务" || bad "随访任务数=${FT_COUNT:-0}"
  DUP="$(code -X POST "$BASE/api/v1/platform/approvals/$APPROVAL_ID/decision" \
    -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"approved":true}')"
  [ "$DUP" = "409" ] && ok "重复决策 409" || bad "重复决策=$DUP"
else
  bad "P002 未生成待审（模型/数据异常？）"
fi

echo
echo "=== 5) 观测（TD-03 Gauge）==="
# 注意 1：抓取可能瞬时失败（响应 270KB + 同时在跑模型），失败重试一次并打印字节数。
# 注意 2：**不能**用 `printf '%s' "$METRICS" | grep -q` —— 脚本开头开了 pipefail，
#         grep -q 命中即退出会让 printf 收到 SIGPIPE(141)，管道整体被判失败，
#         于是「指标明明存在」也会报 FAIL。Week 26 真机踩过（响应 > 64KB 管道缓冲时必现）。
METRICS="$(curl -s --max-time 15 "$BASE/actuator/prometheus")"
PROM_FILE="$(mktemp)"
printf '%s' "$METRICS" > "$PROM_FILE"
if ! grep -q '^agent_run_count_total' "$PROM_FILE"; then
  sleep 2
  curl -s --max-time 15 "$BASE/actuator/prometheus" > "$PROM_FILE"
fi
echo "INFO  prometheus bytes=$(wc -c < "$PROM_FILE")"
grep -q '^approval_pending_count' "$PROM_FILE" && ok "approval_pending_count 已暴露" || bad "缺少 approval_pending_count"
grep '^approval_pending_count' "$PROM_FILE" | head -1 | sed 's/^/INFO  /'
grep -q '^agent_run_count_total' "$PROM_FILE" && ok "agent_run_count_total 仍在" || bad "既有指标丢失"
rm -f "$PROM_FILE"

echo
echo "=== 结果：PASS=$PASS FAIL=$FAIL ==="
[ "$FAIL" -eq 0 ] || exit 1
