#!/usr/bin/env bash
# 云端内存调优配套：临时关闭 platform 的 Langfuse 导出（因为 web/worker 已停）
# 保留 .env 备份；恢复 Langfuse 时把 LANGFUSE_ENABLED 改回 true 并重启 platform。
set -uo pipefail

ENV_FILE="/opt/ai-code/app/platform/.env"
STAMP="$(date +%Y%m%d-%H%M%S)"
cp "$ENV_FILE" "${ENV_FILE}.bak.${STAMP}"

if grep -q '^LANGFUSE_ENABLED=' "$ENV_FILE"; then
  sed -i 's/^LANGFUSE_ENABLED=.*/LANGFUSE_ENABLED=false/' "$ENV_FILE"
else
  printf '\nLANGFUSE_ENABLED=false\n' >> "$ENV_FILE"
fi
echo -n "LANGFUSE_ENABLED="; grep '^LANGFUSE_ENABLED=' "$ENV_FILE"

systemctl restart ai-code-platform
for _ in $(seq 1 30); do
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 http://127.0.0.1:8084/actuator/health 2>/dev/null || echo 000)"
  [ "$code" = "200" ] && break
  sleep 2
done
echo "platform health=$code"
sleep 3
echo "--- recent export errors (should be empty) ---"
journalctl -u ai-code-platform --since '1 minute ago' --no-pager | grep -iE 'langfuse|Failed to export spans' | tail -5 || true
echo "env backup: ${ENV_FILE}.bak.${STAMP}"
