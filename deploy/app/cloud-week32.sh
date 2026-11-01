#!/usr/bin/env bash
# =============================================================================
# Week 32 企业服务部署（在云主机执行，由 remote.ps1 -Script 调用）
# -----------------------------------------------------------------------------
# 前置：新 jar 已上传到 /tmp/enterprise.jar.new；/tmp/remote-deploy.sh 已存在。
# 语义：校验 jar → 备份现版本 → 切换重启 → 等 /api/v1/auth/me 返回 401
#       → 打印 Flyway V8 迁移日志（V8 = ingest_job / ingest_job_step）。
# 回滚：sudo bash /tmp/remote-deploy.sh enterprise --rollback
# =============================================================================
set -uo pipefail
NEW_JAR=/tmp/enterprise.jar.new
APP_DIR=/opt/ai-code/app/enterprise
[ -s "$NEW_JAR" ] || { echo "ERROR: $NEW_JAR 缺失"; exit 1; }
[ -f /tmp/remote-deploy.sh ] || { echo "ERROR: /tmp/remote-deploy.sh 缺失"; exit 1; }

install -m 640 -o aicode -g aicode "$NEW_JAR" "$APP_DIR/app.jar.new"
sudo bash /tmp/remote-deploy.sh enterprise

code=""
for i in $(seq 1 45); do
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 http://127.0.0.1:8081/api/v1/auth/me || true)
  [ "$code" = "401" ] && break
  sleep 2
done
echo "ent_me_status=$code"
echo "--- flyway (last 8) ---"
journalctl -u ai-code-enterprise --since '5 min ago' --no-pager | grep -iE "flyway|Migrating|Successfully applied|V8" | tail -8
