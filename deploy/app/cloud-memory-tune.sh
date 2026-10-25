#!/usr/bin/env bash
# =============================================================================
# 云端内存调优（2026-10-25）：停 Langfuse 前台/worker，降 SkyWalking OAP/UI 堆
# -----------------------------------------------------------------------------
# 原则：业务（platform/gateway/enterprise）、pg16、redis72 完全不动。
# Langfuse 数据卷保留，随时 `docker compose start langfuse-web langfuse-worker` 恢复。
# 失败自动回滚 OAP/UI 到原堆参数。
# =============================================================================
set -uo pipefail

LANGFUSE_DIR="/opt/ai-code/langfuse"
SKYWALKING_DIR="/opt/ai-code/skywalking"
STAMP="$(date +%Y%m%d-%H%M%S)"

echo "=== 0) 调整前 ==="
free -m
docker stats --no-stream --format '{{.Name}}|{{.MemUsage}}|{{.MemPerc}}'

echo
echo "=== 1) 停 Langfuse web/worker（保留数据）==="
cd "$LANGFUSE_DIR"
docker compose stop langfuse-web langfuse-worker
echo "langfuse 状态："
docker compose ps langfuse-web langfuse-worker

echo
echo "=== 2) 降 SkyWalking OAP/UI 堆 ==="
cd "$SKYWALKING_DIR"
cp .env ".env.bak.${STAMP}"
sed -i 's/^OAP_JAVA_OPTS=.*/OAP_JAVA_OPTS=-Xms256M -Xmx768M/' .env
sed -i 's/^UI_JAVA_OPTS=.*/UI_JAVA_OPTS=-Xms64M -Xmx192M/' .env
echo -n "OAP_JAVA_OPTS="; grep '^OAP_JAVA_OPTS=' .env
echo -n "UI_JAVA_OPTS="; grep '^UI_JAVA_OPTS=' .env
docker compose up -d oap ui

echo
echo "=== 3) 等待 OAP/UI ==="
OAP_OK=0
for _ in $(seq 1 30); do
  OAP_STATUS="$(docker inspect -f '{{.State.Status}}' skywalking-oap 2>/dev/null || echo missing)"
  UI_STATUS="$(docker inspect -f '{{.State.Status}}' skywalking-ui 2>/dev/null || echo missing)"
  UI_CODE="$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 http://127.0.0.1:8088/ 2>/dev/null || echo 000)"
  if [ "$OAP_STATUS" = "running" ] && [ "$UI_STATUS" = "running" ] && [ "$UI_CODE" = "200" ]; then
    OAP_OK=1
    break
  fi
  sleep 2
done
echo "oap=$OAP_STATUS ui=$UI_STATUS ui_http=$UI_CODE"
if [ "$OAP_OK" -ne 1 ]; then
  echo "FAIL OAP/UI 未在预期时间就绪，回滚原堆参数"
  cp ".env.bak.${STAMP}" .env
  docker compose up -d oap ui
  exit 1
fi

echo
echo "=== 4) 调整后 ==="
free -m
docker stats --no-stream --format '{{.Name}}|{{.MemUsage}}|{{.MemPerc}}'

echo
echo "=== 5) 业务健康 ==="
for svc in ai-code-platform ai-code-gateway ai-code-enterprise; do
  printf '%-22s %s\n' "$svc" "$(systemctl is-active "$svc" 2>/dev/null || echo inactive)"
done
printf 'platform health      %s\n' "$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://127.0.0.1:8084/actuator/health || echo ERR)"
printf 'gateway health       %s\n' "$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://127.0.0.1:8080/actuator/health || echo ERR)"
printf 'enterprise /auth/me  %s (401 正常)\n' "$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://127.0.0.1:8081/api/v1/auth/me || echo ERR)"
echo "回滚备份：$SKYWALKING_DIR/.env.bak.${STAMP}"
