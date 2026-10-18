#!/usr/bin/env bash
# 汇总云上中间件的全部账号信息到 /tmp/.env.middleware（供本机下载使用）
# 不打印任何密码，只生成文件。
set -uo pipefail

DATA=/opt/ai-code/data/.env
LF=/opt/ai-code/langfuse/.env
SW=/opt/ai-code/skywalking/.env
APP=/opt/ai-code/app/platform/.env
OUT=/tmp/.env.middleware

set -a; . "$DATA"; set +a
PG_USER_="$POSTGRES_USER"; PG_PASS_="$POSTGRES_PASSWORD"; REDIS_PASS_="$REDIS_PASSWORD"
set +a

LF_PG_USER=""; LF_PG_PASS=""; LF_CH_USER=""; LF_CH_PASS=""; LF_REDIS=""; LF_MINIO_USER=""; LF_MINIO_PASS=""
LF_PUB=""; LF_SEC=""; LF_ORG=""; LF_PROJ=""; LF_EMAIL=""; LF_ADMIN_PASS=""
if [ -f "$LF" ]; then
  set -a; . "$LF"; set +a
  LF_PG_USER="$POSTGRES_USER"; LF_PG_PASS="$POSTGRES_PASSWORD"
  LF_CH_USER="$CLICKHOUSE_USER"; LF_CH_PASS="$CLICKHOUSE_PASSWORD"
  LF_REDIS="$REDIS_AUTH"
  LF_MINIO_USER="$MINIO_ROOT_USER"; LF_MINIO_PASS="$MINIO_ROOT_PASSWORD"
  LF_PUB="$LANGFUSE_INIT_PROJECT_PUBLIC_KEY"; LF_SEC="$LANGFUSE_INIT_PROJECT_SECRET_KEY"
  LF_ORG="$LANGFUSE_INIT_ORG_ID"; LF_PROJ="$LANGFUSE_INIT_PROJECT_ID"
  LF_EMAIL="$LANGFUSE_INIT_USER_EMAIL"; LF_ADMIN_PASS="$LANGFUSE_INIT_USER_PASSWORD"
fi

PUBLIC_HOST=$(curl -s --max-time 5 http://metadata.tencentyun.com/latest/meta-data/public-ipv4 2>/dev/null | head -1)
[ -z "${PUBLIC_HOST:-}" ] && PUBLIC_HOST=$(curl -s --max-time 8 https://ipinfo.io/ip 2>/dev/null | head -1)
APP_PASS=$(grep -h '^UBUNTU_PASSWORD=' /root/ubuntu-password.txt 2>/dev/null | cut -d= -f2-)
[ -z "$APP_PASS" ] && APP_PASS=$(cat /root/ubuntu-password.txt 2>/dev/null)

cat > "$OUT" <<EOF
# ============================================================================
# 云上中间件账号汇总（本机使用）
# 生成时间: $(date '+%Y-%m-%d %H:%M:%S %Z')
# 生成位置: 腾讯云 $PUBLIC_HOST  (Ubuntu 22.04.5 / 4C8G / 50G)
#
# ⚠️ 本文件含明文密码，已在 .gitignore 中忽略（.env.middleware），请勿提交/外发。
# ⚠️ 所有 *127.0.0.1* 地址都需要先开 SSH 隧道（tools/tunnel.ps1）。
# ============================================================================

# ── SSH ─────────────────────────────────────────────────────────────────────
SSH_HOST=$PUBLIC_HOST
SSH_PORT=22
SSH_USER=ubuntu
SSH_KEY=.ssh-local/xshell_rsa
SSH_PASSWORD=$APP_PASS

# ── PostgreSQL 16（业务库 aidemo + SkyWalking 存储库 skywalking）──────────────
# 本机经隧道访问；DBeaver 直接填这些值
PG_HOST=127.0.0.1
PG_PORT=5432
PG_DATABASE=aidemo
PG_SKYWALKING_DATABASE=skywalking
PG_USER=$PG_USER_
PG_PASSWORD=$PG_PASS_
PG_JDBC_URL=jdbc:postgresql://127.0.0.1:5432/aidemo

# ── Redis 7.2（业务缓存/会话/记忆）──────────────────────────────────────────
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
REDIS_PASSWORD=$REDIS_PASS_

# ── Langfuse 4.63.0（观测 UI + OTLP 接收）───────────────────────────────────
LANGFUSE_URL=http://127.0.0.1:3000
LANGFUSE_PUBLIC_URL=http://$PUBLIC_HOST:3000
LANGFUSE_ORG_ID=$LF_ORG
LANGFUSE_PROJECT_ID=$LF_PROJ
LANGFUSE_PUBLIC_KEY=$LF_PUB
LANGFUSE_SECRET_KEY=$LF_SEC
LANGFUSE_ADMIN_EMAIL=$LF_EMAIL
LANGFUSE_ADMIN_PASSWORD=$LF_ADMIN_PASS

# Langfuse 自身依赖（一般不用手连，排查时才用）
LF_POSTGRES_HOST=127.0.0.1
LF_POSTGRES_PORT=15432
LF_POSTGRES_USER=$LF_PG_USER
LF_POSTGRES_PASSWORD=$LF_PG_PASS
LF_POSTGRES_DB=postgres
LF_CLICKHOUSE_HTTP=http://127.0.0.1:8123
LF_CLICKHOUSE_USER=$LF_CH_USER
LF_CLICKHOUSE_PASSWORD=$LF_CH_PASS
LF_REDIS_AUTH=$LF_REDIS
LF_MINIO_URL=http://127.0.0.1:9090
LF_MINIO_CONSOLE=http://127.0.0.1:9091
LF_MINIO_USER=$LF_MINIO_USER
LF_MINIO_PASSWORD=$LF_MINIO_PASS

# ── SkyWalking 10.2.0 ──────────────────────────────────────────────────────
SW_UI_URL=http://127.0.0.1:8088
SW_UI_PUBLIC_URL=http://$PUBLIC_HOST:8088
SW_OAP_GRPC=127.0.0.1:11800
SW_OAP_HTTP=http://127.0.0.1:12800
# SkyWalking 本身无账号密码；数据存在上面的 PostgreSQL 的 skywalking 库

# ── 端口速查（本机隧道映射 == 云端回环）────────────────────────────────────
# 5432 PostgreSQL | 6379 Redis | 3000 Langfuse | 8088 SkyWalking UI
# 11800 OAP gRPC | 15432 Langfuse-PG | 8123 Langfuse-ClickHouse | 9090 Langfuse-MinIO
EOF

chmod 600 "$OUT"
chown ubuntu:ubuntu "$OUT" 2>/dev/null || true
echo "generated: $OUT ($(stat -c%s "$OUT") bytes)"
echo "keys: $(grep -cE '^[A-Z_]+=' "$OUT")"
