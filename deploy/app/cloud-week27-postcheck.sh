#!/usr/bin/env bash
# Week 27 云端终检（只读）：验证发布后状态、验收证据与配置已恢复。
# 注意：本脚本是 2026-10-25 批次的**一次性终检**，依赖 cloud-accept-week27.sh 留下的
#      /tmp/w27-approval.txt 与该批次备份目录；换批次请改 BACKUP 与 approval 文件。
set -uo pipefail

BASE="http://127.0.0.1:8084"
ENT="http://127.0.0.1:8081"
BACKUP="/opt/ai-code/backup/week27-20261025-145732"
PASS=0; FAIL=0
ok()  { echo "PASS $*"; PASS=$((PASS+1)); }
bad() { echo "FAIL $*"; FAIL=$((FAIL+1)); }
eq()  { if [ "$2" = "$3" ]; then ok "$1"; else bad "$1 (actual=$2 expected=$3)"; fi; }
ge()  { if [ "$2" -ge "$3" ] 2>/dev/null; then ok "$1"; else bad "$1 (actual=$2 min=$3)"; fi; }

set -a; . /opt/ai-code/app/platform/.env; set +a
PU="$SPRING_DATASOURCE_USERNAME"; PP="$SPRING_DATASOURCE_PASSWORD"; PDB="${SPRING_DATASOURCE_URL##*/}"; PDB="${PDB%%\?*}"
PSQL() { docker exec -e PGPASSWORD="$PP" pg16 psql -h 127.0.0.1 -U "$PU" -d "$PDB" -tAc "$1"; }

echo "### 1. 服务"
eq "platform health 200" "$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$BASE/actuator/health")" "200"
eq "enterprise auth/me 401" "$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$ENT/api/v1/auth/me")" "401"
eq "gateway health 200" "$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://127.0.0.1:8080/actuator/health)" "200"
for t in platform enterprise gateway; do eq "systemd $t active" "$(systemctl is-active ai-code-$t)" "active"; done

echo "### 2. V10"
eq "flyway v10" "$(PSQL "select version from flyway_schema_history_platform where version='10' and success;")" "10"
eq "sync_status 列" "$(PSQL "select count(*) from information_schema.columns where table_name='platform_knowledge_binding' and column_name='sync_status';")" "1"
eq "last_synced_at 列" "$(PSQL "select count(*) from information_schema.columns where table_name='platform_knowledge_binding' and column_name='last_synced_at';")" "1"
eq "提醒表" "$(PSQL "select count(*) from information_schema.tables where table_name='platform_approval_reminder';")" "1"
eq "提醒表外键" "$(PSQL "select count(*) from information_schema.table_constraints where table_name='platform_approval_reminder' and constraint_type='FOREIGN KEY';")" "1"

echo "### 3. 对账状态"
eq "binding ACTIVE" "$(PSQL "select sync_status from platform_knowledge_binding limit 1;")" "ACTIVE"
if [ -n "$(PSQL "select last_synced_at from platform_knowledge_binding limit 1;")" ]; then ok "lastSyncedAt 非空"; else bad "lastSyncedAt 非空"; fi

echo "### 4. 提醒证据（历史，已恢复 created_at）"
IFS='|' read -r AID ORIG < /tmp/w27-approval.txt
eq "reminder_count=1" "$(PSQL "select reminder_count from platform_approval_reminder where approval_id='$AID';")" "1"
eq "审批仍 PENDING" "$(PSQL "select status from platform_approval where approval_id='$AID';")" "PENDING"
ge "审计 APPROVAL_REMIND" "$(PSQL "select count(*) from audit_log where action='APPROVAL_REMIND' and detail like '%'||'$AID'||'%';")" 1
eq "created_at 已恢复" "$(PSQL "select created_at::text from platform_approval where approval_id='$AID';")" "$ORIG"

echo "### 5. 指标（进程内计数，最终重启后已重新生成；提醒计数器见重启前证据日志）"
PROM=""
for i in 1 2 3; do
  code=$(curl -s -o /tmp/w27-prom.txt -w '%{http_code}' --max-time 30 "$BASE/actuator/prometheus" || true)
  if [ "$code" = "200" ] && grep -q '^knowledge_hit_count_total' /tmp/w27-prom.txt; then
    PROM=$(cat /tmp/w27-prom.txt); break
  fi
  echo "retry metrics scrape ($i) code=$code"; sleep 2
done
[ -n "$PROM" ] || bad "metrics scrape"
for m in knowledge_search_duration_seconds_count knowledge_hit_count_total knowledge_reconcile_count_total knowledge_binding_missing_count; do
  if [[ "$PROM" == *"$m{"* || "$PROM" == *"$m "* ]]; then ok "指标 $m"; else bad "指标 $m"; fi
done
ge "命中数累计>=1" "$(awk '/^knowledge_hit_count_total/{print int($NF)}' /tmp/w27-prom.txt | head -1)" 1
ge "对账计数>=1" "$(awk '/^knowledge_reconcile_count_total\{/{s+=int($NF)} END{print s}' /tmp/w27-prom.txt)" 1

echo "### 6. 知识库问答证据"
ge "knowledge-qa COMPLETED 执行记录" "$(PSQL "select count(*) from execution_record where agent_key='knowledge-qa' and status='COMPLETED';")" 1
ge "执行记录含 citations" "$(PSQL "select count(*) from execution_record where agent_key='knowledge-qa' and output_json like '%citations%';")" 1

echo "### 7. 配置与资源"
if cmp -s /opt/ai-code/app/platform/.env "$BACKUP/platform.env"; then ok ".env 与备份一致"; else bad ".env 与备份一致"; fi
if grep -q '^PLATFORM_APPROVAL_REMINDER_CRON=' /opt/ai-code/app/platform/.env; then bad "临时 cron 已移除"; else ok "临时 cron 已移除"; fi
AVAIL=$(free -m | awk '/^Mem:/{print $7}')
eq "可用内存>500MB" "$([ "${AVAIL:-0}" -gt 500 ] && echo yes || echo no)" "yes"
eq "根盘使用<90%" "$([ "$(df / | awk 'NR==2{gsub("%","",$5);print $5}')" -lt 90 ] && echo yes || echo no)" "yes"
echo "RESULT PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ]
