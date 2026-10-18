#!/usr/bin/env bash
# ============================================================================
# Week 20 云端监控栈验收：Prometheus / Grafana / 抓取目标 / 告警规则 / 仪表盘
# 在服务器上执行：.\remote.ps1 -Script .\tools\cloud-accept-monitoring.sh
# 前置：deploy/monitoring 已上传到 /opt/ai-code/monitoring 并 docker compose up -d
# ============================================================================
set -uo pipefail
BASE=/opt/ai-code/monitoring
PASS=0
FAIL=0

say() { printf '\n== %s ==\n' "$*"; }
ok()   { PASS=$((PASS+1)); printf '  PASS  %s\n' "$*"; }
bad()  { FAIL=$((FAIL+1)); printf '  FAIL  %s\n' "$*"; }

http_code() { curl -s -o /dev/null -w '%{http_code}' --max-time 8 "$1" 2>/dev/null; }
body()      { curl -s --max-time 8 "$1" 2>/dev/null; }

say "containers"
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' | grep -E 'NAMES|prometheus|grafana|alertmanager' || true

say "Prometheus readiness"
code=$(http_code http://127.0.0.1:19090/-/ready)
[ "$code" = "200" ] && ok "Prometheus /-/ready 200" || bad "Prometheus /-/ready = $code"

say "Prometheus scrape targets"
targets=$(body 'http://127.0.0.1:19090/api/v1/targets?state=active')
for job in ai-code-platform ai-code-gateway prometheus; do
  if echo "$targets" | grep -q "\"job\":\"$job\""; then
    health=$(echo "$targets" | tr ',' '\n' | grep -A20 "\"job\":\"$job\"" | grep -m1 '"health"' | sed 's/.*"health":"\([^"]*\)".*/\1/')
    [ "$health" = "up" ] && ok "target $job = up" || bad "target $job = ${health:-unknown}（检查 tools\\metrics-tunnel.ps1）"
  else
    bad "target $job 未配置"
  fi
done

say "Prometheus alert rules"
rules=$(body 'http://127.0.0.1:19090/api/v1/rules')
count=$(echo "$rules" | grep -o '"name":"AiCode[^"]*"' | sort -u | wc -l | tr -d ' ')
[ "$count" -ge 10 ] && ok "已加载 $count 条 AiCode* 告警规则" || bad "告警规则仅 $count 条（期望 >= 10）"
echo "$rules" | grep -o '"name":"AiCode[^"]*"' | sort -u | sed 's/^/        /'

say "sample metrics"
for m in agent_run_count_total llm_tokens_total tool_call_count_total; do
  v=$(body "http://127.0.0.1:19090/api/v1/query?query=$m")
  if echo "$v" | grep -q '"result":\[{'; then ok "指标 $m 有数据"; else printf '  WARN  %s 暂无数据（还没跑过 Agent 时正常）\n' "$m"; fi
done

say "Grafana"
code=$(http_code http://127.0.0.1:3001/api/health)
[ "$code" = "200" ] && ok "Grafana /api/health 200" || bad "Grafana /api/health = $code"
if [ -f "$BASE/.env" ]; then
  set -a; . "$BASE/.env"; set +a
  admin="${GRAFANA_ADMIN_USER:-admin}"
  if [ -n "${GRAFANA_ADMIN_PASSWORD:-}" ]; then
    search=$(curl -s -u "$admin:$GRAFANA_ADMIN_PASSWORD" --max-time 8 "http://127.0.0.1:3001/api/search?query=AI%20Code")
    if echo "$search" | grep -q 'aicode-platform-overview'; then
      ok "仪表盘已 provision：aicode-platform-overview / aicode-gateway-overview"
    else
      bad "Grafana 未找到已 provision 的仪表盘（检查 grafana/provisioning 挂载）"
      echo "        $search" | head -c 300; echo
    fi
    ds=$(curl -s -u "$admin:$GRAFANA_ADMIN_PASSWORD" --max-time 8 http://127.0.0.1:3001/api/datasources)
    echo "$ds" | grep -q 'aicode-prometheus' && ok "数据源 aicode-prometheus 已注册" || bad "数据源未注册"
  else
    bad "GRAFANA_ADMIN_PASSWORD 未设置"
  fi
else
  bad "缺少 $BASE/.env"
fi

say "resolution"
echo "  PASS=$PASS  FAIL=$FAIL"
exit "$FAIL"
