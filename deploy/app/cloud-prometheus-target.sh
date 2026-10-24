#!/usr/bin/env bash
# =============================================================================
# 把 Prometheus 抓取目标从「反向隧道」切到「云端本机应用」（Week 22）
# -----------------------------------------------------------------------------
# 背景：第 20 周的拓扑是「应用在本地 + Prometheus 在云端 + ssh -R 隧道」，
#       目标写的是 127.0.0.1:18084/18080；应用上云后同机抓 8084/8080，隧道可以退役。
#
# 用法：sudo bash cloud-prometheus-target.sh [--revert]
# 安全：改前自动备份 prometheus.yml；先用 promtool 校验，校验不过不重启。
# =============================================================================
set -euo pipefail

CFG=/opt/ai-code/monitoring/prometheus/prometheus.yml
MODE="${1:-switch}"

[ -f "$CFG" ] || { echo "ERROR: $CFG 不存在"; exit 1; }

BACKUP="$CFG.bak.$(date +%Y%m%d%H%M%S)"
cp -p "$CFG" "$BACKUP"
echo "已备份：$BACKUP"

case "$MODE" in
  --revert)
    sed -i 's/127\.0\.0\.1:8084"/127.0.0.1:18084"/; s/127\.0\.0\.1:8080"/127.0.0.1:18080"/' "$CFG"
    echo "已回退到隧道目标（18084/18080）"
    ;;
  *)
    sed -i 's/127\.0\.0\.1:18084"/127.0.0.1:8084"/; s/127\.0\.0\.1:18080"/127.0.0.1:8080"/' "$CFG"
    echo "已切换到本机应用（8084/8080）"
    ;;
esac

echo "=== promtool 校验 ==="
if docker exec prometheus promtool check config /etc/prometheus/prometheus.yml; then
  echo "配置校验通过"
else
  echo "ERROR: 配置校验失败，回滚"
  cp -p "$BACKUP" "$CFG"
  exit 1
fi

docker restart prometheus >/dev/null
echo "prometheus 已重启，等待 12s 让目标刷新..."
sleep 12

echo "=== 目标健康状态 ==="
curl -s 'http://127.0.0.1:19090/api/v1/targets?state=active' | python3 - <<'PY'
import sys, json
data = json.load(sys.stdin)["data"]["activeTargets"]
for t in data:
    print(f"{t['labels'].get('job'):<20} {t['scrapeUrl']:<40} {t['health']}")
PY
