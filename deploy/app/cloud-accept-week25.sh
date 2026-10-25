#!/usr/bin/env bash
# =============================================================================
# Week 25 云端验收 —— 知识库 Agent 3（包装 enterprise RAG）
# -----------------------------------------------------------------------------
# 前置：
#   1) enterprise 与平台均已发布 Week 25 代码并重启；
#   2) 两侧 ENTERPRISE_INTERNAL_TOKEN / PLATFORM_KNOWLEDGE_INTERNAL_TOKEN 一致；
#   3) enterprise 已装 pgvector 并补跑 V6（否则问答会 502，脚本会明确报出来）；
#   4) 管理员已完成 SSO 建链，且企业账号下至少有一个知识库。
# 用法：bash /opt/ai-code/app/cloud-accept-week25.sh
# 退出码：0 全通过；1 有失败项（逐项打印 PASS/FAIL）
# =============================================================================
set -uo pipefail

PLATFORM="http://127.0.0.1:8084"
ENTERPRISE="http://127.0.0.1:8081"
PASS=0
FAIL=0

ok()   { PASS=$((PASS + 1)); echo "PASS  $*"; }
bad()  { FAIL=$((FAIL + 1)); echo "FAIL  $*"; }
field() { printf '%s' "$1" | python3 -c "import sys,json;d=json.load(sys.stdin);print($2)" 2>/dev/null; }
code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }

login() {
  curl -s -X POST "$PLATFORM/api/v1/platform/auth/login" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"$2\"}" \
    | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])" 2>/dev/null
}

echo "=== 1) 健康检查 ==="
[ "$(code --max-time 5 "$PLATFORM/actuator/health")" = "200" ] && ok "平台 health 200" || bad "平台 health 异常"
[ "$(code --max-time 5 "$ENTERPRISE/actuator/health")" = "200" ] && ok "enterprise health 200" || bad "enterprise health 异常"

ADMIN_TOKEN="$(login admin admin123)"
OPERATOR_TOKEN="$(login operator operator123)"
[ -n "$ADMIN_TOKEN" ] && ok "admin 登录" || bad "admin 登录"
[ -n "$OPERATOR_TOKEN" ] && ok "operator 登录" || bad "operator 登录"

echo
echo "=== 2) 内部端点 fail-closed（直连 enterprise，不带令牌）==="
NO_TOKEN="$(code "$ENTERPRISE/api/v1/internal/knowledge/bases?platformUserId=1")"
[ "$NO_TOKEN" = "403" ] && ok "无令牌 403" || bad "无令牌返回 $NO_TOKEN（期望 403，检查 InternalTokenGuard）"

echo
echo "=== 3) 导入知识库绑定（admin）==="
IMPORT_CODE="$(code -X POST "$PLATFORM/api/v1/platform/knowledge-bindings/import" \
  -H "Authorization: Bearer $ADMIN_TOKEN")"
if [ "$IMPORT_CODE" = "200" ]; then
  IMPORT="$(curl -s -X POST "$PLATFORM/api/v1/platform/knowledge-bindings/import" \
    -H "Authorization: Bearer $ADMIN_TOKEN")"
  ok "导入 200"
  IMPORTED="$(field "$IMPORT" "d['data']['imported'] + d['data']['updated']")"
  echo "INFO  imported/updated 合计=$IMPORTED，enterpriseUserId=$(field "$IMPORT" "d['data']['enterpriseUserId']")"
else
  bad "导入返回 $IMPORT_CODE（常见原因：内部令牌不一致、管理员未建 SSO 映射、enterprise 不可用）"
fi

echo
echo "=== 4) 列出绑定（operator）==="
LIST="$(curl -s "$PLATFORM/api/v1/platform/knowledge-bindings" -H "Authorization: Bearer $OPERATOR_TOKEN")"
TOTAL="$(field "$LIST" "d['data']['total']")"
[ "${TOTAL:-0}" -ge 1 ] && ok "operator 可见绑定 total=$TOTAL" || bad "operator 看不到绑定（total=${TOTAL:-0}）"
KB_ID="$(field "$LIST" "d['data']['items'][0]['knowledgeBaseId']")"
echo "INFO  knowledgeBaseId=$KB_ID"

echo
echo "=== 5) 知识库问答（operator）==="
if [ -n "${KB_ID:-}" ]; then
  RUN="$(curl -s -X POST "$PLATFORM/api/v1/platform/agents/knowledge-qa/runs" \
    -H "Authorization: Bearer $OPERATOR_TOKEN" -H 'Content-Type: application/json' \
    -d "{\"input\":{\"knowledgeBaseId\":$KB_ID,\"question\":\"这份资料里最关键的随访要求是什么？\"}}" \
    --max-time 180)"
  STATUS="$(field "$RUN" "d['data']['status']")"
  if [ "$STATUS" = "COMPLETED" ]; then
    ok "问答 COMPLETED"
    OUT="$(field "$RUN" "d['data']['outputJson']")"
    echo "$OUT" | grep -q '"citations"' && ok "返回 citations" || bad "缺 citations"
    HITS="$(printf '%s' "$OUT" | python3 -c "import sys,json;print(json.load(sys.stdin)['hitCount'])" 2>/dev/null)"
    echo "INFO  hitCount=${HITS:-0} degraded=$(printf '%s' "$OUT" | python3 -c "import sys,json;print(json.load(sys.stdin)['degraded'])" 2>/dev/null)"
    if [ "${HITS:-0}" -eq 0 ]; then
      echo "WARN  hitCount=0：文档可能未入库（检查 enterprise document_chunk 行数）"
    fi
  else
    bad "问答 status=$STATUS（502 多为 pgvector 缺失/enterprise 不可用/令牌不一致，见日志）"
  fi

  UNBOUND="$(code -X POST "$PLATFORM/api/v1/platform/agents/knowledge-qa/runs" \
    -H "Authorization: Bearer $OPERATOR_TOKEN" -H 'Content-Type: application/json' \
    -d '{"input":{"knowledgeBaseId":999999,"question":"q"}}')"
  [ "$UNBOUND" = "404" ] && ok "未绑定知识库 404" || bad "未绑定返回 $UNBOUND（期望 404）"
else
  bad "跳过问答：没有可用绑定"
fi

echo
echo "=== 6) 权限 ==="
VIEWER_TOKEN="$(login viewer viewer123)"
DENY="$(code -X POST "$PLATFORM/api/v1/platform/agents/knowledge-qa/runs" \
  -H "Authorization: Bearer $VIEWER_TOKEN" -H 'Content-Type: application/json' \
  -d '{"input":{"knowledgeBaseId":1,"question":"q"}}')"
[ "$DENY" = "403" ] && ok "viewer 403" || bad "viewer 返回 $DENY（期望 403）"

echo
echo "=== 7) 观测 ==="
PROM="$(curl -s "$PLATFORM/actuator/prometheus")"
echo "$PROM" | grep -q 'tool_call_count_total' && ok "tool.call 指标存在" || bad "缺 tool_call_count_total"
echo "$PROM" | grep -q 'agent_run_count_total{.*knowledge-qa' && ok "knowledge-qa 指标已出现" \
  || echo "INFO  knowledge-qa 指标尚未出现（需要一次成功/失败的 Agent 运行；上面已跑过一次）"

echo
echo "=== 结果：PASS=$PASS FAIL=$FAIL ==="
[ "$FAIL" -eq 0 ] || exit 1
