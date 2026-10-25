#!/usr/bin/env bash
# Week 29 前端部署：静态包解压 + nginx 同域配置 + trust-forwarded-for。
# 前置：/tmp/web.tar.gz（dist 内容，已上传）、/tmp/aicode.conf（nginx 站点配置）。
set -euo pipefail
umask 022

WEB_ROOT="/opt/ai-code/web"
BACKUP_DIR="/opt/ai-code/backup/week29-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BACKUP_DIR"

echo "### 1. 备份现有站点与 platform .env"
if [ -d "$WEB_ROOT" ]; then
  cp -a "$WEB_ROOT" "$BACKUP_DIR/web"
fi
cp -a /opt/ai-code/app/platform/.env "$BACKUP_DIR/platform.env"
echo "BACKUP=$BACKUP_DIR"

echo "### 2. 解压静态包（保留上一版 web.prev）"
rm -rf "${WEB_ROOT}.prev"
if [ -d "$WEB_ROOT" ]; then
  mv "$WEB_ROOT" "${WEB_ROOT}.prev"
fi
mkdir -p "$WEB_ROOT"
tar -xzf /tmp/web.tar.gz -C "$WEB_ROOT"
chown -R www-data:www-data "$WEB_ROOT"
find "$WEB_ROOT" -type d -exec chmod 755 {} \;
find "$WEB_ROOT" -type f -exec chmod 644 {} \;
echo "index: $(stat -c '%s bytes' "$WEB_ROOT/index.html")"

echo "### 3. 安装 nginx 站点（禁用 default）"
install -m 644 /tmp/aicode.conf /etc/nginx/sites-available/aicode
ln -sfn /etc/nginx/sites-available/aicode /etc/nginx/sites-enabled/aicode
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl reload nginx
systemctl is-active nginx

echo "### 4. 打开 trust-forwarded-for（幂等）"
if grep -q '^PLATFORM_RATE_LIMIT_TRUST_FORWARDED_FOR=' /opt/ai-code/app/platform/.env; then
  sed -i 's#^PLATFORM_RATE_LIMIT_TRUST_FORWARDED_FOR=.*#PLATFORM_RATE_LIMIT_TRUST_FORWARDED_FOR=true#' /opt/ai-code/app/platform/.env
else
  printf '%s\n' 'PLATFORM_RATE_LIMIT_TRUST_FORWARDED_FOR=true' >> /opt/ai-code/app/platform/.env
fi
chown aicode:aicode /opt/ai-code/app/platform/.env
chmod 600 /opt/ai-code/app/platform/.env
grep '^PLATFORM_RATE_LIMIT_TRUST_FORWARDED_FOR=' /opt/ai-code/app/platform/.env

echo "### 5. 重启平台并等待健康"
systemctl restart ai-code-platform.service
for i in $(seq 1 45); do
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 http://127.0.0.1:8084/actuator/health || true)
  [ "$code" = "200" ] && { echo "platform health=200 (${i}x2s)"; break; }
  sleep 2
done
[ "${code:-}" = "200" ] || { echo "ERROR: platform health failed"; exit 1; }

echo "### 6. 本机同域验收"
echo "index_http=$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1/)"
echo "api_login_http=$(curl -s -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}' http://127.0.0.1/api/v1/platform/auth/login)"
echo "index_title=$(grep -o '<title>[^<]*' /opt/ai-code/web/index.html | head -1)"
echo "WEB DEPLOY DONE"
