#!/usr/bin/env bash
# Week 26 · P0-1：把 pg16 容器切到官方 pgvector 镜像（保留同一个数据卷）
# 说明：
#   · 官方 postgres:16 镜像不含 vector 扩展，且容器内没有编译工具链；
#   · 直接换成 pgvector/pgvector:pg16（基于同一 PostgreSQL 16 主版本，数据目录兼容）；
#   · 容器名/网络/端口/重启策略/环境变量与旧容器保持一致，旧镜像保留用于回滚。
# 密钥处理：从旧容器 Config.Env 提取到 /root 下 600 权限的 env 文件，脚本不打印任何值。
set -uo pipefail
umask 077

IMAGE_NEW="pgvector/pgvector:pg16"
IMAGE_OLD="postgres:16"
ENVFILE="/root/week26-pg16.env"
LOG="/opt/ai-code/backup/week26-pgvector-$(date +%Y%m%d-%H%M%S).log"

echo "IMAGE_NEW=$IMAGE_NEW"
docker image inspect "$IMAGE_NEW" >/dev/null 2>&1 || { echo "FAIL: 新镜像不存在"; exit 1; }

# ── 1. 记录旧容器形态（便于回滚与审计）──────────────────────────────────────
docker inspect pg16 > "/root/week26-pg16-inspect-before.json"
echo "OLD image: $(docker inspect pg16 --format '{{.Config.Image}}')"
echo "OLD volume: $(docker inspect pg16 --format '{{range .Mounts}}{{.Name}}{{end}}')"
docker inspect pg16 --format '{{range .Config.Env}}{{println .}}{{end}}' \
  | grep -E '^(POSTGRES_USER|POSTGRES_PASSWORD|POSTGRES_DB|TZ|PGTZ)=' > "$ENVFILE"
chmod 600 "$ENVFILE"
echo "env keys captured: $(cut -d= -f1 "$ENVFILE" | paste -sd, -)"

# ── 2. 停旧容器 → 保留数据卷 → 起新容器 ─────────────────────────────────────
docker stop pg16 >/dev/null 2>&1
docker rm -f pg16 >/dev/null 2>&1
if ! docker run -d --name pg16 --restart unless-stopped \
      --network ai-net -p 127.0.0.1:5432:5432 \
      -v data_pg_data:/var/lib/postgresql/data \
      --env-file "$ENVFILE" "$IMAGE_NEW" >>"$LOG" 2>&1; then
  echo "FAIL: 新容器启动失败，回滚到 ${IMAGE_OLD}"
  docker rm -f pg16 >/dev/null 2>&1
  docker run -d --name pg16 --restart unless-stopped --network ai-net \
      -p 127.0.0.1:5432:5432 -v data_pg_data:/var/lib/postgresql/data \
      --env-file "$ENVFILE" "$IMAGE_OLD" >>"$LOG" 2>&1
  exit 1
fi
echo "new container started: $(docker inspect pg16 --format '{{.Config.Image}}')"

# ── 3. 等待就绪（60s）────────────────────────────────────────────────────────
DBUSER=$(grep '^POSTGRES_USER=' "$ENVFILE" | cut -d= -f2-)
ready=0
for i in $(seq 1 60); do
  if docker exec pg16 pg_isready -U "$DBUSER" -h 127.0.0.1 >/dev/null 2>&1; then ready=1; break; fi
  sleep 1
done
if [ "$ready" != "1" ]; then
  echo "FAIL: 新容器 60s 内未就绪，回滚"
  docker logs --tail 40 pg16 2>&1 | sed 's/PASSWORD=[^ ]*/PASSWORD=<redacted>/g'
  docker rm -f pg16 >/dev/null 2>&1
  docker run -d --name pg16 --restart unless-stopped --network ai-net \
      -p 127.0.0.1:5432:5432 -v data_pg_data:/var/lib/postgresql/data \
      --env-file "$ENVFILE" "$IMAGE_OLD" >>"$LOG" 2>&1
  exit 1
fi
echo "pg ready after ${i}s"

# ── 4. 版本与既有数据校验 ───────────────────────────────────────────────────
echo "-- version --"
docker exec pg16 psql -U "$DBUSER" -d postgres -tAc "select version();"
set -a; . /opt/ai-code/app/enterprise/.env; set +a
EU="$SPRING_DATASOURCE_USERNAME"; EP="$SPRING_DATASOURCE_PASSWORD"
EDB="${SPRING_DATASOURCE_URL##*/}"; EDB="${EDB%%\?*}"
set -a; . /opt/ai-code/app/platform/.env; set +a
PU="$SPRING_DATASOURCE_USERNAME"; PP="$SPRING_DATASOURCE_PASSWORD"
PDB="${SPRING_DATASOURCE_URL##*/}"; PDB="${PDB%%\?*}"
echo "-- databases --"
docker exec pg16 psql -U "$DBUSER" -d postgres -tAc "select datname from pg_database where datistemplate=false order by 1;"
echo "-- platform rows before/after restart --"
docker exec -e PGPASSWORD="$PP" pg16 psql -h 127.0.0.1 -U "$PU" -d "$PDB" -tAc \
  "select 'memory='||count(*) from platform_memory; select 'approval='||count(*) from platform_approval;"
echo "-- enterprise rows --"
docker exec -e PGPASSWORD="$EP" pg16 psql -h 127.0.0.1 -U "$EU" -d "$EDB" -tAc \
  "select 'app_user='||count(*) from app_user; select 'knowledge_base='||count(*) from knowledge_base;"

# ── 5. 启用扩展（企业库必需；平台库不需要）─────────────────────────────────
echo "-- vector available --"
docker exec pg16 psql -U "$DBUSER" -d "$EDB" -tAc "select name,default_version from pg_available_extensions where name='vector';"
echo "-- create extension --"
docker exec pg16 psql -U "$DBUSER" -d "$EDB" -c "CREATE EXTENSION IF NOT EXISTS vector;"
docker exec pg16 psql -U "$DBUSER" -d "$EDB" -tAc "select extname||' '||extversion from pg_extension where extname='vector';"
echo "PGVECTOR SWITCH DONE (log: $LOG)"
