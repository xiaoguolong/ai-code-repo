#!/usr/bin/env bash
# =============================================================================
# Week 23 云端验收 —— 患者分析 HITL + 记忆生命周期（在云主机本机执行）
# -----------------------------------------------------------------------------
# 前置：
#   1) 平台已发布 Week 23 代码并重启，Flyway 已应用 V6（日志出现 now at version v6）；
#   2) seed 用户存在：admin/admin123、operator/operator123；
#   3) P002 为演示用 HIGH 风险患者（HealthMetricTool 内置）。
# 用法：bash /opt/ai-code/app/cloud-accept-week23.sh
# 退出码：0 全通过；1 有失败项（逐项打印 PASS/FAIL）
# 注意：会真实调用模型（P002 一次），并在库里写入/删除一条随访记忆。
# =============================================================================
set -uo pipefail

BASE="http://127.0.0.1:8084"
PASS=0
FAIL=0
TOKEN=""
ADMIN_TOKEN=""

ok()   { PASS=$((PASS + 1)); echo "PASS  $*"; }
bad()  { FAIL=$((FAIL + 1)); echo "FAIL  $*"; }
json() { python3 -c "import sys,json;d=json.load(sys.stdin);print($1)" 2>/dev/null; }
field() { printf '%s' "$1" | python3 -c "import sys,json;d=json.load(sys.stdin);print($2)" 2>/dev/null; }

login() {
  curl -s -X POST "$BASE/api/v1/platform/auth/login" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"$2\"}" | json "d['data']['token']"
}

echo "=== 1) 健康检查与登录 ==="
CODE="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$BASE/actuator/health")"
[ "$CODE" = "200" ] && ok "health 200" || bad "health=$CODE"
ADMIN_TOKEN="$(login admin admin123)"
TOKEN="$(login operator operator123)"
[ -n "$ADMIN_TOKEN" ] && ok "admin 登录" || bad "admin 登录"
[ -n "$TOKEN" ] && ok "operator 登录" || bad "operator 登录"

echo
echo "=== 2) P002 HIGH 风险 → 待审（不写记忆）==="
RUN="$(curl -s -X POST "$BASE/api/v1/platform/agents/patient-analysis/runs" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"input":{"patientId":"P002"}}' --max-time 180)"
STATUS="$(field "$RUN" "d['data']['status']")"
EXEC_ID="$(field "$RUN" "d['data']['executionId']")"
OUT="$(field "$RUN" "d['data']['outputJson']")"
RISK="$(field "$OUT" "d['riskLevel']")"
APPROVAL_ID="$(field "$OUT" "d['approvalId']")"
MEMID_PENDING="$(field "$OUT" "d['memoryId']")"
TOKENS="$(field "$RUN" "d['data']['usage']['totalTokens']")"
[ "$STATUS" = "PENDING_APPROVAL" ] && ok "执行 PENDING_APPROVAL（tokens=$TOKENS）" || bad "执行 status=$STATUS"
[ "$RISK" = "HIGH" ] && ok "风险 HIGH" || bad "riskLevel=$RISK"
[ -n "$APPROVAL_ID" ] && ok "生成审批任务 approvalId=${APPROVAL_ID:0:8}…" || bad "无 approvalId"
[ -z "$MEMID_PENDING" ] || [ "$MEMID_PENDING" = "None" ] && ok "待审期间不写记忆" || bad "待审已写记忆=$MEMID_PENDING"

echo
echo "=== 3) 待办列表 / 详情（operator 自己的）==="
LIST="$(curl -s "$BASE/api/v1/platform/approvals?status=PENDING&limit=20" -H "Authorization: Bearer $TOKEN")"
LIST_IDS="$(field "$LIST" "[i['approvalId'] for i in d['data']['items']]")"
echo "$LIST_IDS" | grep -q "$APPROVAL_ID" && ok "待办列表包含该审批" || bad "待办列表不含 $APPROVAL_ID"
DETAIL="$(curl -s "$BASE/api/v1/platform/approvals/$APPROVAL_ID" -H "Authorization: Bearer $TOKEN")"
DETAIL_RISK="$(field "$DETAIL" "d['data']['riskLevel']")"
DETAIL_STATUS="$(field "$DETAIL" "d['data']['status']")"
[ "$DETAIL_RISK" = "HIGH" ] && [ "$DETAIL_STATUS" = "PENDING" ] && ok "详情 HIGH/PENDING" || bad "详情 risk=$DETAIL_RISK status=$DETAIL_STATUS"

echo
echo "=== 4) 批准 → COMPLETED + 写记忆 ==="
DECISION="$(curl -s -X POST "$BASE/api/v1/platform/approvals/$APPROVAL_ID/decision" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"approved":true,"comment":"week23 验收：同意进入随访"}' --max-time 60)"
D_STATUS="$(field "$DECISION" "d['data']['status']")"
D_EXEC="$(field "$DECISION" "d['data']['executionStatus']")"
D_MEM="$(field "$DECISION" "d['data']['memoryId']")"
[ "$D_STATUS" = "APPROVED" ] && ok "审批 APPROVED" || bad "审批 status=$D_STATUS"
[ "$D_EXEC" = "COMPLETED" ] && ok "执行 COMPLETED" || bad "执行 executionStatus=$D_EXEC"
[ -n "$D_MEM" ] && ok "写入记忆 memoryId=${D_MEM:0:8}…" || bad "未写入记忆"

EXEC="$(curl -s "$BASE/api/v1/platform/executions/$EXEC_ID" -H "Authorization: Bearer $TOKEN")"
EXEC_STATUS="$(field "$EXEC" "d['data']['status']")"
[ "$EXEC_STATUS" = "COMPLETED" ] && ok "执行记录已终态 COMPLETED" || bad "执行记录 status=$EXEC_STATUS"

MEM="$(curl -s "$BASE/api/v1/platform/memories?patientId=P002&limit=20" -H "Authorization: Bearer $TOKEN")"
MEM_IDS="$(field "$MEM" "[i['memoryId'] for i in d['data']['items']]")"
echo "$MEM_IDS" | grep -q "$D_MEM" && ok "记忆列表可查到该条" || bad "记忆列表不含 $D_MEM"

echo
echo "=== 5) 重复决策 → 409 ==="
CODE="$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/v1/platform/approvals/$APPROVAL_ID/decision" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"approved":false}')"
[ "$CODE" = "409" ] && ok "重复决策 409" || bad "重复决策=$CODE"

echo
echo "=== 6) 记忆删除（operator 本人）==="
DEL="$(curl -s -X DELETE "$BASE/api/v1/platform/memories/$D_MEM" -H "Authorization: Bearer $TOKEN")"
DELETED="$(field "$DEL" "d['data']['deleted']")"
[ "$DELETED" = "1" ] && ok "删除 deleted=1" || bad "删除 result=$DELETED"
MEM2="$(curl -s "$BASE/api/v1/platform/memories?patientId=P002&limit=20" -H "Authorization: Bearer $TOKEN")"
MEM2_IDS="$(field "$MEM2" "[i['memoryId'] for i in d['data']['items']]")"
echo "$MEM2_IDS" | grep -q "$D_MEM" && bad "删除后仍可查到" || ok "删除后不可见"

echo
echo "=== 7) 运维端点权限（operator 403 / admin 200）==="
OCODE="$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/v1/platform/memories/rebuild-embeddings?limit=10" \
  -H "Authorization: Bearer $TOKEN")"
[ "$OCODE" = "403" ] && ok "operator 重建 403" || bad "operator 重建=$OCODE"
REBUILD="$(curl -s -X POST "$BASE/api/v1/platform/memories/rebuild-embeddings?limit=10" -H "Authorization: Bearer $ADMIN_TOKEN")"
R_SCANNED="$(field "$REBUILD" "d['data']['scanned']")"
R_MODEL="$(field "$REBUILD" "d['data']['currentModel']")"
[ -n "$R_SCANNED" ] && [ -n "$R_MODEL" ] && ok "admin 重建 scanned=$R_SCANNED model=$R_MODEL" || bad "重建响应异常"
PCODE="$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/v1/platform/memories/purge-expired?limit=10" \
  -H "Authorization: Bearer $TOKEN")"
[ "$PCODE" = "403" ] && ok "operator 清理 403" || bad "operator 清理=$PCODE"
PURGE="$(curl -s -X POST "$BASE/api/v1/platform/memories/purge-expired?limit=10" -H "Authorization: Bearer $ADMIN_TOKEN")"
P_DELETED="$(field "$PURGE" "d['data']['deleted']")"
[ -n "$P_DELETED" ] && ok "admin 清理 deleted=$P_DELETED" || bad "清理响应异常"

echo
echo "================ 汇总 ================"
echo "PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ] || exit 1
