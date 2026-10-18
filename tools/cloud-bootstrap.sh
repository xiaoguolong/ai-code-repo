#!/usr/bin/env bash
# 云上三栈部署：data（PG+Redis）→ skywalking → langfuse
# 幂等：已存在的 .env 不会被覆盖；容器用 up -d 收敛。
# 所有密钥在服务器端生成并写入 600 权限的 .env —— 不回显、不入库、不经过聊天。
set -uo pipefail

BASE=/opt/ai-code
DATA=$BASE/data
SW=$BASE/skywalking
LF=$BASE/langfuse
mkdir -p "$DATA" "$SW" "$LF"

log() { printf '\n== %s ==\n' "$*"; }
rand_hex() { openssl rand -hex "${1:-16}"; }

# 公网地址：优先腾讯云 metadata，其次通用回显服务
PUBLIC_HOST=$(curl -s --max-time 5 http://metadata.tencentyun.com/latest/meta-data/public-ipv4 2>/dev/null | head -1)
[ -z "${PUBLIC_HOST:-}" ] && PUBLIC_HOST=$(curl -s --max-time 8 https://api.ipify.org 2>/dev/null | head -1)
echo "public host detected: ${PUBLIC_HOST:0:6}***"

# ── 1) data/.env ────────────────────────────────────────────────────────────
log "data .env"
if [ ! -f "$DATA/.env" ]; then
  PG_PASS=$(rand_hex 16)
  REDIS_PASS=$(rand_hex 16)
  cat > "$DATA/.env" <<EOF
POSTGRES_USER=aidemo
POSTGRES_PASSWORD=$PG_PASS
POSTGRES_DB=aidemo
REDIS_PASSWORD=$REDIS_PASS
EOF
  chmod 600 "$DATA/.env"
  echo "generated"
else
  echo "exists, keep as-is"
fi
set -a; . "$DATA/.env"; set +a

log "start data stack"
cd "$DATA" && docker compose up -d 2>&1 | tail -5

log "wait for postgres"
for i in $(seq 1 30); do
  if docker compose -f "$DATA/docker-compose.yml" exec -T postgres pg_isready -U "$POSTGRES_USER" >/dev/null 2>&1; then
    echo "postgres ready after ${i}0s"; break
  fi
  sleep 3
done

log "create database skywalking if absent"
if docker compose -f "$DATA/docker-compose.yml" exec -T postgres \
     psql -U "$POSTGRES_USER" -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='skywalking'" | grep -q 1; then
  echo "database skywalking already exists"
else
  docker compose -f "$DATA/docker-compose.yml" exec -T postgres \
    psql -U "$POSTGRES_USER" -d postgres -c "CREATE DATABASE skywalking OWNER $POSTGRES_USER" && echo "created"
fi

# ── 2) skywalking/.env ──────────────────────────────────────────────────────
log "skywalking .env"
if [ ! -f "$SW/.env" ]; then
  cat > "$SW/.env" <<EOF
SW_JDBC_URL=jdbc:postgresql://postgres:5432/skywalking
SW_DATA_SOURCE_USER=$POSTGRES_USER
SW_DATA_SOURCE_PASSWORD=$POSTGRES_PASSWORD
SW_UI_PORT=8088
OAP_JAVA_OPTS=-Xms256M -Xmx1024M
UI_JAVA_OPTS=-Xms64M -Xmx256M
EOF
  chmod 600 "$SW/.env"
  echo "generated"
else
  echo "exists, keep as-is"
fi

log "start skywalking stack"
cd "$SW" && docker compose up -d 2>&1 | tail -5

# ── 3) langfuse/.env ────────────────────────────────────────────────────────
log "langfuse .env"
if [ ! -f "$LF/.env" ]; then
  # 注意 1：DATABASE_URL 里的密码必须与 POSTGRES_PASSWORD 完全相同，否则容器起来也连不上库。
  # 注意 2：LANGFUSE_INIT_* 的值**不要含空格**——.env 被 shell source 时会被拆词
  #        （实测报 "Code: command not found"），且官方明确不要加引号（#3398）。
  LF_PG_PASS=$(rand_hex 16)
  LF_INIT_PUB=$(rand_hex 16)
  LF_INIT_SEC=$(rand_hex 16)
  LF_ADMIN_PASS=$(rand_hex 12)
  cat > "$LF/.env" <<EOF
NEXTAUTH_URL=http://$PUBLIC_HOST:3000
NEXTAUTH_SECRET=$(rand_hex 32)
ENCRYPTION_KEY=$(rand_hex 32)
SALT=$(rand_hex 32)
POSTGRES_USER=postgres
POSTGRES_PASSWORD=$LF_PG_PASS
POSTGRES_DB=postgres
POSTGRES_VERSION=17
DATABASE_URL=postgresql://postgres:$LF_PG_PASS@postgres:5432/postgres
CLICKHOUSE_USER=clickhouse
CLICKHOUSE_PASSWORD=$(rand_hex 16)
REDIS_AUTH=$(rand_hex 16)
MINIO_ROOT_USER=minio
MINIO_ROOT_PASSWORD=$(rand_hex 16)
LANGFUSE_INIT_ORG_ID=ai-code-repo
LANGFUSE_INIT_ORG_NAME=AI-Code-Repo
LANGFUSE_INIT_PROJECT_ID=ai-code-repo
LANGFUSE_INIT_PROJECT_NAME=AI-Code-Repo-Platform
LANGFUSE_INIT_PROJECT_PUBLIC_KEY=pk-lf-$LF_INIT_PUB
LANGFUSE_INIT_PROJECT_SECRET_KEY=sk-lf-$LF_INIT_SEC
LANGFUSE_INIT_USER_EMAIL=admin@ai-code.local
LANGFUSE_INIT_USER_NAME=Platform-Admin
LANGFUSE_INIT_USER_PASSWORD=$LF_ADMIN_PASS
TELEMETRY_ENABLED=false
LANGFUSE_S3_MEDIA_UPLOAD_ENDPOINT_EXTERNAL=http://$PUBLIC_HOST:9090
EOF
  chmod 600 "$LF/.env"
  # 自校验：两个密码必须一致（只报结果，不打印值）
  URL_PASS=$(sed -n 's#^DATABASE_URL=postgresql://[^:]*:\([^@]*\)@.*#\1#p' "$LF/.env")
  if [ "$URL_PASS" = "$LF_PG_PASS" ] && [ ${#LF_PG_PASS} -eq 32 ]; then
    echo "generated (password consistency: OK, len ${#LF_PG_PASS})"
  else
    echo "ERROR: password mismatch between POSTGRES_PASSWORD and DATABASE_URL"
  fi
else
  echo "exists, keep as-is"
fi

log "start langfuse stack"
cd "$LF" && docker compose up -d 2>&1 | tail -8

log "containers"
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'

log "resources"
free -m | awk '/Mem:/{print "mem : "$2" MB total, "$7" MB available"}'
free -m | awk '/Swap:/{print "swap: "$2" MB"}'
df -h / | awk 'NR==2{print "disk: "$3" used, "$4" avail"}'
