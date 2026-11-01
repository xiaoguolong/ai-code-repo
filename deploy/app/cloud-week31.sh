#!/usr/bin/env bash
# Week 31 发布：platform jar + web 静态包 + nginx 流式配置。
#
# 前置（由 tools/deploy-app.ps1 或 scp 上传）：
#   /tmp/app.jar.new      平台新 jar
#   /tmp/web.tar.gz       前端 dist 内容
#   /tmp/aicode.conf      nginx 站点配置（含 SSE location）
#
# 回滚：jar 用 /opt/ai-code/app/platform/app.jar.prev（remote-deploy.sh 自动），
#       静态用 /opt/ai-code/web.prev；也可只关流式：PLATFORM_STREAM_ENABLED=false。
set -euo pipefail
umask 022

WEB_ROOT="/opt/ai-code/web"
APP_DIR="/opt/ai-code/app/platform"
BACKUP_DIR="/opt/ai-code/backup/week31-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BACKUP_DIR"

echo "### 1. 备份（静态 + jar + .env）"
if [ -d "$WEB_ROOT" ]; then
  cp -a "$WEB_ROOT" "$BACKUP_DIR/web"
fi
if [ -f "$APP_DIR/app.jar" ]; then
  cp -p "$APP_DIR/app.jar" "$BACKUP_DIR/app.jar"
fi
cp -a "$APP_DIR/.env" "$BACKUP_DIR/platform.env"
echo "BACKUP=$BACKUP_DIR"

echo "### 2. 切换 jar（remote-deploy.sh 内置健康检查与失败回滚）"
[ -s /tmp/app.jar.new ] || { echo "ERROR: /tmp/app.jar.new 不存在或为空"; exit 1; }
install -m 640 -o aicode -g aicode /tmp/app.jar.new "$APP_DIR/app.jar.new"
bash "$(dirname "$0")/remote-deploy.sh" platform

echo "### 3. 静态包（保留上一版 web.prev）"
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

echo "### 4. nginx（SSE location + 静态）"
install -m 644 /tmp/aicode.conf /etc/nginx/sites-available/aicode
ln -sfn /etc/nginx/sites-available/aicode /etc/nginx/sites-enabled/aicode
nginx -t
systemctl reload nginx
systemctl is-active nginx

echo "### 5. 本机入口检查"
echo "index_http=$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1/)"
echo "health=$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 http://127.0.0.1:8084/actuator/health)"
echo "stream_401=$(curl -s -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -d '{"input":{"knowledgeBaseId":1,"question":"ping"}}' \
  http://127.0.0.1/api/v1/platform/agents/knowledge-qa/runs/stream)"

echo "WEEK31 DEPLOY DONE"
