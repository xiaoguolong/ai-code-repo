#!/usr/bin/env bash
# Week 27 云端验收主流程（真实 PostgreSQL + 真实 enterprise + 真实模型）。
# 只读为主；唯一的数据改动是「把一条待审的 created_at 临时回填 30h」用于验证提醒视图，
# 原值写入 /tmp/w27-approval.txt，由 cloud-week27-reminder-scheduler-check.sh 复原。
set -uo pipefail

BASE="http://127.0.0.1:8084"
ENT="http://127.0.0.1:8081"
PASS=0; FAIL=0
ok()  { echo "PASS $*"; PASS=$((PASS+1)); }
bad() { echo "FAIL $*"; FAIL=$((FAIL+1)); }
eq()  { if [ "$2" = "$3" ]; then ok "$1"; else bad "$1 (actual=$2 expected=$3)"; fi; }
ge()  { if [ "$2" -ge "$3" ] 2>/dev/null; then ok "$1"; else bad "$1 (actual=$2 min=$3)"; fi; }

# ── DB 连接信息（不打印）─────────────────────────────────────────────────────
set -a; . /opt/ai-code/app/platform/.env; set +a
PU="$SPRING_DATASOURCE_USERNAME"; PP="$SPRING_DATASOURCE_PASSWORD"; PDB="${SPRING_DATASOURCE_URL##*/}"; PDB="${PDB%%\?*}"
PT="$PLATFORM_KNOWLEDGE_INTERNAL_TOKEN"
set -a; . /opt/ai-code/app/enterprise/.env; set +a
PSQL() { docker exec -e PGPASSWORD="$PP" pg16 psql -h 127.0.0.1 -U "$PU" -d "$PDB" -tAc "$1"; }
JGET() { local expr="$1"; shift; python3 -c "import json,sys;d=json.load(sys.stdin);print($expr)" "$@"; }

echo "### 1. 基础健康"
eq "platform health 200" "$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$BASE/actuator/health")" "200"
eq "enterprise auth/me 401" "$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$ENT/api/v1/auth/me")" "401"
eq "gateway health 200" "$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://127.0.0.1:8080/actuator/health)" "200"

echo "### 2. V10 对象"
eq "flyway v10" "$(PSQL "select version from flyway_schema_history_platform where version='10' and success;")" "10"
eq "sync_status 列" "$(PSQL "select count(*) from information_schema.columns where table_name='platform_knowledge_binding' and column_name='sync_status';")" "1"
eq "last_synced_at 列" "$(PSQL "select count(*) from information_schema.columns where table_name='platform_knowledge_binding' and column_name='last_synced_at';")" "1"
eq "提醒表" "$(PSQL "select count(*) from information_schema.tables where table_name='platform_approval_reminder';")" "1"

echo "### 3. 登录"
ADMIN_TOKEN=$(curl -s -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}' "$BASE/api/v1/platform/auth/login" | JGET 'd["data"]["token"]' 2>/dev/null)
OPER_TOKEN=$(curl -s -H 'Content-Type: application/json' -d '{"username":"operator","password":"operator123"}' "$BASE/api/v1/platform/auth/login" | JGET 'd["data"]["token"]' 2>/dev/null)
if [ -n "${ADMIN_TOKEN:-}" ]; then ok "admin token"; else bad "admin token"; fi
if [ -n "${OPER_TOKEN:-}" ]; then ok "operator token"; else bad "operator token"; fi

echo "### 4. 手动对账（UNKNOWN -> ACTIVE）"
RECON=$(curl -s -X POST -H "Authorization: Bearer $ADMIN_TOKEN" "$BASE/api/v1/platform/knowledge-bindings/reconcile")
echo "reconcile: $(echo "$RECON" | head -c 400)"
R_GROUPS=$(echo "$RECON" | JGET 'd["data"]["groups"]' 2>/dev/null)
R_CHECKED=$(echo "$RECON" | JGET 'd["data"]["checked"]' 2>/dev/null)
R_UPDATED=$(echo "$RECON" | JGET 'd["data"]["updated"]' 2>/dev/null)
R_MISSING=$(echo "$RECON" | JGET 'd["data"]["missing"]' 2>/dev/null)
R_UNCHANGED=$(echo "$RECON" | JGET 'd["data"]["unchanged"]' 2>/dev/null)
R_FAILED=$(echo "$RECON" | JGET 'd["data"]["failed"]' 2>/dev/null)
eq "reconcile failed=0" "${R_FAILED:-x}" "0"
ge "reconcile checked>=1" "${R_CHECKED:-0}" 1
eq "reconcile 计数自洽" "$(( ${R_UPDATED:-0} + ${R_MISSING:-0} + ${R_UNCHANGED:-0} + ${R_FAILED:-0} ))" "$R_CHECKED"
ge "reconcile groups>=1" "${R_GROUPS:-0}" 1

BIND=$(curl -s -H "Authorization: Bearer $OPER_TOKEN" "$BASE/api/v1/platform/knowledge-bindings")
echo "bindings: $(echo "$BIND" | head -c 400)"
B_STATUS=$(echo "$BIND" | JGET 'd["data"]["items"][0]["syncStatus"]' 2>/dev/null)
B_SYNCED=$(echo "$BIND" | JGET 'd["data"]["items"][0]["lastSyncedAt"]' 2>/dev/null)
eq "binding ACTIVE" "$B_STATUS" "ACTIVE"
if [ -n "${B_SYNCED:-}" ] && [ "$B_SYNCED" != "None" ]; then ok "binding lastSyncedAt 非空"; else bad "binding lastSyncedAt 非空"; fi

ENT_UID=$(PSQL "select enterprise_user_id from platform_knowledge_binding order by created_at limit 1;")
EB=$(curl -s -H "X-Internal-Token: $PT" "$ENT/api/v1/internal/knowledge/bases?enterpriseUserId=$ENT_UID")
if echo "$EB" | python3 -c 'import json,sys;d=json.load(sys.stdin);sys.exit(0 if d["code"]=="SUCCESS" and d["data"]["bases"] else 1)'; then
  ok "enterprise 按企业用户列库"
else
  bad "enterprise 按企业用户列库"
fi

echo "### 5. 构造超时待审（提醒视图）"
RUN=$(curl -s -X POST -H "Authorization: Bearer $OPER_TOKEN" -H 'Content-Type: application/json' \
  -d '{"input":{"patientId":"P002","task":"为患者生成慢病风险分析报告"}}' \
  "$BASE/api/v1/platform/agents/patient-analysis/runs")
RUN_STATUS=$(echo "$RUN" | JGET 'd["data"]["status"]' 2>/dev/null)
AID=$(echo "$RUN" | JGET 'json.loads(d["data"]["outputJson"]).get("approvalId","")' 2>/dev/null)
eq "HIGH 运行进入待审" "$RUN_STATUS" "PENDING_APPROVAL"
if [ -n "${AID:-}" ]; then
  ok "approvalId 非空"
  ORIG=$(PSQL "select created_at from platform_approval where approval_id='$AID';")
  echo "$AID|$ORIG" > /tmp/w27-approval.txt
  PSQL "update platform_approval set created_at = now() - interval '30 hours' where approval_id='$AID';" >/dev/null
  echo "backdated approval: $AID (original=$ORIG)"
else
  bad "approvalId 非空"
fi

REM=$(curl -s -H "Authorization: Bearer $OPER_TOKEN" "$BASE/api/v1/platform/approvals/reminders?limit=20")
echo "reminders: $(echo "$REM" | head -c 400)"
R_THRESH=$(echo "$REM" | JGET 'd["data"]["thresholdHours"]' 2>/dev/null)
R_FOUND=$(echo "$REM" | JGET 'sum(1 for i in d["data"]["items"] if i["approvalId"]==sys.argv[1])' "$AID" 2>/dev/null)
R_OVERDUE=$(echo "$REM" | JGET 'max([i["overdueHours"] for i in d["data"]["items"] if i["approvalId"]==sys.argv[1]] or [0])' "$AID" 2>/dev/null)
eq "reminders threshold=24" "${R_THRESH:-x}" "24"
eq "提醒视图含该待审" "${R_FOUND:-0}" "1"
ge "overdueHours>=29" "${R_OVERDUE:-0}" 29
DET=$(curl -s -H "Authorization: Bearer $OPER_TOKEN" "$BASE/api/v1/platform/approvals/$AID")
eq "提醒后仍 PENDING" "$(echo "$DET" | JGET 'd["data"]["status"]' 2>/dev/null)" "PENDING"

echo "### 6. 知识库问答（真实检索 + 模型 + 引用）"
QA=$(curl -s -X POST -H "Authorization: Bearer $OPER_TOKEN" -H 'Content-Type: application/json' \
  -d '{"input":{"knowledgeBaseId":1,"question":"糖尿病患者的随访频率是多少？"}}' \
  "$BASE/api/v1/platform/agents/knowledge-qa/runs")
QA_STATUS=$(echo "$QA" | JGET 'd["data"]["status"]' 2>/dev/null)
QA_JSON=$(echo "$QA" | JGET 'json.dumps(json.loads(d["data"]["outputJson"]))' 2>/dev/null)
QA_HITS=$(echo "$QA_JSON" | JGET 'd["hitCount"]' 2>/dev/null)
QA_CITES=$(echo "$QA_JSON" | JGET 'len(d["citations"] or [])' 2>/dev/null)
echo "qa: status=$QA_STATUS hitCount=$QA_HITS citations=$QA_CITES"
eq "knowledge-qa COMPLETED" "$QA_STATUS" "COMPLETED"
ge "hitCount>=1" "${QA_HITS:-0}" 1
ge "citations>=1" "${QA_CITES:-0}" 1

echo "### 7. 指标出口"
PROM=""
for i in 1 2 3; do
  code=$(curl -s -o /tmp/w27-accept-prom.txt -w '%{http_code}' --max-time 30 "$BASE/actuator/prometheus" || true)
  if [ "$code" = "200" ] && [[ "$(cat /tmp/w27-accept-prom.txt)" == *"knowledge_hit_count_total"* ]]; then
    PROM=$(cat /tmp/w27-accept-prom.txt); break
  fi
  echo "retry metrics scrape ($i) code=$code"; sleep 2
done
for m in knowledge_search_duration_seconds_count knowledge_hit_count_total knowledge_reconcile_count_total knowledge_binding_missing_count; do
  if [[ "$PROM" == *"$m{"* || "$PROM" == *"$m "* ]]; then ok "指标 $m"; else bad "指标 $m"; fi
done

echo "RESULT PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ]
