#!/usr/bin/env bash
# =============================================================================
# 应用部署切换（Week 22）——在云主机上执行，由 tools/deploy-app.ps1 调用
# -----------------------------------------------------------------------------
# 为什么要有这个脚本，而不是在 Windows 上拼 ssh 命令：
#   `remote.ps1 -Cmd` 在 PowerShell 5.1 下会把内嵌引号拆坏（第 20 周实测，
#   表现为 `grafana: command not found`）。复杂逻辑一律放服务端 .sh。
#
# 用法（需 root，内部直接调 systemctl）：
#   sudo bash remote-deploy.sh platform
#   sudo bash remote-deploy.sh gateway
#   sudo bash remote-deploy.sh platform --rollback
#
# 语义：
#   1) 校验新 jar 存在且非空 → 备份当前为 app.jar.prev → 切换 → 重启
#   2) 健康检查最多 90s；失败或超时 → 自动切回 app.jar.prev 并重启
#   3) 幂等：重复执行只会再切换一次，不会破坏 prev
# =============================================================================
set -euo pipefail

TARGET="${1:-}"
MODE="${2:-deploy}"
# 健康判据按目标区分：
#   platform/gateway 有 actuator，期望 200；
#   enterprise 没引 actuator，用 /api/v1/auth/me 的 401 作为「进程已在监听」的证据。
case "$TARGET" in
  platform)   PORT=8084; HEALTH_PATH=/actuator/health; EXPECT=200 ;;
  gateway)    PORT=8080; HEALTH_PATH=/actuator/health; EXPECT=200 ;;
  enterprise) PORT=8081; HEALTH_PATH=/api/v1/auth/me;  EXPECT=401 ;;
  *) echo "usage: $0 platform|gateway|enterprise [--rollback]"; exit 2 ;;
esac
case "$MODE" in
  deploy|--rollback) ;;
  *) echo "unknown mode: $MODE"; exit 2 ;;
esac

APP_DIR="/opt/ai-code/app/$TARGET"
SERVICE="ai-code-$TARGET.service"
JAR="$APP_DIR/app.jar"
NEW="$APP_DIR/app.jar.new"
PREV="$APP_DIR/app.jar.prev"
HEALTH="http://127.0.0.1:${PORT}${HEALTH_PATH}"
RUN_USER="${APP_RUN_USER:-aicode}"

log() { echo "[$(date '+%F %T')] $*"; }

health_ok() {
  local code
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$HEALTH" || true)"
  [ "$code" = "$EXPECT" ]
}

wait_health() {
  local i
  for i in $(seq 1 45); do
    if health_ok; then return 0; fi
    sleep 2
  done
  return 1
}

restart() {
  systemctl restart "$SERVICE"
}

rollback() {
  if [ ! -f "$PREV" ]; then
    log "ERROR: $PREV 不存在，无法回滚"
    return 1
  fi
  log "回滚到 app.jar.prev"
  cp -p "$PREV" "$JAR"
  restart
  if wait_health; then
    log "回滚后健康检查通过"
    return 0
  fi
  log "ERROR: 回滚后仍不健康，请人工介入（journalctl -u $SERVICE -n 200）"
  return 1
}

if [ "$MODE" = "--rollback" ]; then
  rollback
  exit $?
fi

[ -s "$NEW" ] || { log "ERROR: $NEW 不存在或为空（先上传 jar）"; exit 1; }

if [ -f "$JAR" ]; then
  cp -p "$JAR" "$PREV"
  log "已备份当前版本到 app.jar.prev"
fi

mv "$NEW" "$JAR"
chown "$RUN_USER":"$RUN_USER" "$JAR" 2>/dev/null || log "WARN: chown $RUN_USER 失败（服务用户是否已创建？）"
chmod 640 "$JAR"

log "重启 $SERVICE（jar 时间：$(date -r "$JAR" '+%F %T' 2>/dev/null || echo unknown)）"
restart

if wait_health; then
  log "OK: $HEALTH 返回 $EXPECT，当前 jar=$(date -r "$JAR" '+%F %T' 2>/dev/null || echo unknown)"
  exit 0
fi

log "ERROR: 健康检查失败（$HEALTH），开始回滚"
rollback || true
exit 1
