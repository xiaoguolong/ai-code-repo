#!/usr/bin/env bash
# =============================================================================
# Week 32 前端静态包发布（只换 /opt/ai-code/web，不改 nginx 配置）
# 前置：/tmp/web.tar.gz（apps/web/dist 的内容，根目录即 index.html）
# 回滚：把 /opt/ai-code/web.prev 换回来（见脚本末尾提示）
# =============================================================================
set -euo pipefail
WEB_ROOT=/opt/ai-code/web
PKG=/tmp/web.tar.gz
[ -s "$PKG" ] || { echo "ERROR: $PKG 缺失"; exit 1; }

echo "### 1. 备份"
rm -rf "${WEB_ROOT}.prev"
if [ -d "$WEB_ROOT" ]; then
  mv "$WEB_ROOT" "${WEB_ROOT}.prev"
fi
mkdir -p "$WEB_ROOT"
tar -xzf "$PKG" -C "$WEB_ROOT"
chown -R www-data:www-data "$WEB_ROOT"
find "$WEB_ROOT" -type d -exec chmod 755 {} \;
find "$WEB_ROOT" -type f -exec chmod 644 {} \;

echo "### 2. 校验产物"
ls "$WEB_ROOT" | head -5
test -s "$WEB_ROOT/index.html" || { echo "ERROR: index.html 缺失"; exit 1; }
grep -o 'EnterpriseKnowledgeView-[A-Za-z0-9_-]*\.js' "$WEB_ROOT"/assets/*.js 2>/dev/null | head -1 || true
echo "index: $(stat -c '%s bytes' "$WEB_ROOT/index.html")"
echo "### 3. 本机校验"
curl -s -o /dev/null -w 'ent_page=%{http_code}\n' http://127.0.0.1/ent/knowledge
echo "WEB DEPLOY DONE（回滚：rm -rf $WEB_ROOT && mv ${WEB_ROOT}.prev $WEB_ROOT）"
