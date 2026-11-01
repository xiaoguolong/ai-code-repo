#!/usr/bin/env bash
# Week 31 云端验收：SSE 首字、终态、取消、同步回归、nginx 不缓冲。
#
# 前置：platform 已发布（含 Week 31）、web 已发布、nginx 已含 SSE location。
# 只在云主机本机执行；需要 docker pg16 与 platform .env。
set -uo pipefail

BASE="http://127.0.0.1:8084"
WEB="http://127.0.0.1"
PASS=0; FAIL=0
ok() { echo "PASS $*"; PASS=$((PASS+1)); }
bad() { echo "FAIL $*"; FAIL=$((FAIL+1)); }
eq() { if [ "$2" = "$3" ]; then ok "$1"; else bad "$1 (actual=$2 expected=$3)"; fi; }
lt() { if awk "BEGIN{exit !($2 < $3)}"; then ok "$1 ($2 < $3)"; else bad "$1 (actual=$2 expected<$3)"; fi; }
gt() { if awk "BEGIN{exit !($2 > $3)}"; then ok "$1 ($2 > $3)"; else bad "$1 (actual=$2 expected>$3)"; fi; }

set -a; . /opt/ai-code/app/platform/.env; set +a
PU="$SPRING_DATASOURCE_USERNAME"; PP="$SPRING_DATASOURCE_PASSWORD"
PDB="${SPRING_DATASOURCE_URL##*/}"; PDB="${PDB%%\?*}"
PSQL() { docker exec -e PGPASSWORD="$PP" pg16 psql -h 127.0.0.1 -U "$PU" -d "$PDB" -tAc "$1"; }
JGET() { python3 -c "import json,sys;d=json.load(sys.stdin);print($1)"; }
login() {
  curl -s -H 'Content-Type: application/json' -d "{\"username\":\"$1\",\"password\":\"$2\"}" \
    "$BASE/api/v1/platform/auth/login" | JGET 'd["data"]["token"]' 2>/dev/null
}

# 量一次 SSE：输出 meta/首字毫秒、终态、token
measure_stream() { # $1 base $2 agentKey $3 payload
  curl -sN --max-time 120 -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
    -H 'Accept: text/event-stream, application/json' -d "$3" \
    "$1/api/v1/platform/agents/$2/runs/stream" \
  | python3 -c '
import sys,time,json
start=time.time(); event=""; out={"meta_ms":None,"first_delta_ms":None,"status":None,"code":None,"tokens":0}
for line in sys.stdin:
    line=line.rstrip("\n")
    if line.startswith("event:"):
        event=line.split(":",1)[1].strip()
    elif line.startswith("data:"):
        data=line.split(":",1)[1].strip()
        if event=="meta" and out["meta_ms"] is None:
            out["meta_ms"]=int((time.time()-start)*1000)
        elif event=="delta" and out["first_delta_ms"] is None:
            out["first_delta_ms"]=int((time.time()-start)*1000)
        elif event=="final":
            env=json.loads(data); out["code"]=env.get("code"); rec=env.get("data") or {}
            out["status"]=rec.get("status"); out["tokens"]=(rec.get("usage") or {}).get("totalTokens",0)
        elif event=="error":
            out["error"]=data
print(json.dumps(out))
'
}

echo "### 1. 服务与登录"
HEALTH=$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$BASE/actuator/health")
eq "platform health" "$HEALTH" "200"
ADMIN=$(login admin admin123)
eq "admin 登录" "$([ -n "$ADMIN" ] && echo yes || echo no)" "yes"
eq "流式端点无 token 401" "$(curl -s -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -d '{"input":{}}' "$BASE/api/v1/platform/agents/knowledge-qa/runs/stream")" "401"
eq "不支持流式的 Agent 400" "$(curl -s -o /dev/null -w '%{http_code}' -X POST -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' -H 'Accept: text/event-stream, application/json' \
  -d '{"input":{"patientId":"P001"}}' "$BASE/api/v1/platform/agents/medical-assistant/runs/stream")" "400"

echo "### 2. 知识库问答 SSE（直连 8084）"
KB=$(PSQL "select knowledge_base_id from platform_knowledge_binding where enabled and sync_status <> 'MISSING' order by knowledge_base_id limit 1;" | tr -d '[:space:]')
if [ -z "$KB" ]; then
  bad "没有可用的知识库绑定（跳过 SSE 主链路）"
else
  R=$(measure_stream "$BASE" knowledge-qa "{\"input\":{\"knowledgeBaseId\":$KB,\"question\":\"糖尿病患者的随访频率是多少？\"}}")
  echo "knowledge_qa=$R"
  META=$(echo "$R" | JGET 'd["meta_ms"]')
  FIRST=$(echo "$R" | JGET 'd["first_delta_ms"]')
  STATUS=$(echo "$R" | JGET 'd["status"]')
  TOKENS=$(echo "$R" | JGET 'd["tokens"]')
  eq "knowledge-qa final COMPLETED" "$STATUS" "COMPLETED"
  gt "knowledge-qa token>0" "$TOKENS" "0"
  lt "knowledge-qa meta<300ms" "$META" "300"
  lt "knowledge-qa 首字<2000ms" "$FIRST" "2000"

  echo "### 3. 走 nginx（80）不缓冲"
  RW=$(measure_stream "$WEB" knowledge-qa "{\"input\":{\"knowledgeBaseId\":$KB,\"question\":\"糖尿病患者的随访频率是多少？\"}}")
  echo "via_nginx=$RW"
  FIRSTW=$(echo "$RW" | JGET 'd["first_delta_ms"]')
  lt "nginx 首字<2500ms（未被缓冲）" "$FIRSTW" "2500"
fi

echo "### 4. 患者分析 SSE + 待审"
RP=$(measure_stream "$BASE" patient-analysis '{"input":{"patientId":"P002"}}')
echo "patient_analysis=$RP"
PSTATUS=$(echo "$RP" | JGET 'd["status"]')
PFIRST=$(echo "$RP" | JGET 'd["first_delta_ms"]')
eq "patient-analysis final PENDING_APPROVAL" "$PSTATUS" "PENDING_APPROVAL"
lt "patient-analysis 首字<5000ms" "$PFIRST" "5000"

echo "### 5. 停止生成 → CANCELLED"
# 用患者分析（真实 30s 量级）而不是知识库问答（1–2s 就结束，2s 超时可能来不及取消）
TMP=$(mktemp)
timeout 3 curl -sN -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream, application/json' \
  -d '{"input":{"patientId":"P002"}}' \
  "$BASE/api/v1/platform/agents/patient-analysis/runs/stream" > "$TMP" || true
EID=$(grep -o '"executionId":"[^"]*"' "$TMP" | head -1 | cut -d'"' -f4)
ESTATUS=""
for _ in $(seq 1 30); do
  ESTATUS=$(PSQL "select status from execution_record where execution_id='$EID';" | tr -d '[:space:]')
  [ "$ESTATUS" = "CANCELLED" ] && break
  sleep 1
done
eq "断连后终态 CANCELLED" "$ESTATUS" "CANCELLED"
rm -f "$TMP"

echo "### 6. 同步端点回归"
SYNC=$(curl -s -o /dev/null -w '%{http_code}' -X POST -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' -d '{"input":{"patientId":"P001"}}' \
  "$BASE/api/v1/platform/agents/medical-assistant/runs")
eq "同步 medical-assistant 200" "$SYNC" "200"

echo "### 7. 指标与遗留 RUNNING"
PROM=$(curl -s "$BASE/actuator/prometheus")
COUNT=$(echo "$PROM" | grep -c 'agent_stream_first_delta_seconds_count' || true)
if [ "${COUNT:-0}" -ge 1 ]; then ok "首字指标已导出 (series=$COUNT)"; else bad "首字指标未导出"; fi
RUNNING=$(PSQL "select count(*) from execution_record where status='RUNNING' and started_at > now() - interval '10 minutes';" | tr -d '[:space:]')
eq "近 10 分钟无 RUNNING 僵尸" "$RUNNING" "0"

echo "RESULT PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ]
