#!/usr/bin/env bash
# =============================================================================
# Week 23 批次 2：设置 enterprise 运维令牌（不打印令牌）
# -----------------------------------------------------------------------------
# 在云主机执行：bash /tmp/cloud-week23-b2-token.sh
# 行为：在 /opt/ai-code/app/enterprise/.env 中写入/替换 ENTERPRISE_ADMIN_TOKEN；
#       保留文件属主与权限；只打印 token_set/length。
# =============================================================================
set -uo pipefail

ENV_FILE="/opt/ai-code/app/enterprise/.env"
if [ ! -f "$ENV_FILE" ]; then
  echo "FAIL 找不到 $ENV_FILE"
  exit 1
fi

TOKEN="$(openssl rand -hex 32)"
if sudo grep -q '^ENTERPRISE_ADMIN_TOKEN=' "$ENV_FILE"; then
  sudo sed -i "s|^ENTERPRISE_ADMIN_TOKEN=.*|ENTERPRISE_ADMIN_TOKEN=${TOKEN}|" "$ENV_FILE"
else
  printf '\n# Week 23 批次 2：SSO 映射运维令牌（A1）\nENTERPRISE_ADMIN_TOKEN=%s\n' "$TOKEN" \
    | sudo tee -a "$ENV_FILE" >/dev/null
fi

LEN="$(sudo grep '^ENTERPRISE_ADMIN_TOKEN=' "$ENV_FILE" | awk -F= '{print length($2)}')"
echo "token_set=true length=$LEN"
echo "env_file=$ENV_FILE"
