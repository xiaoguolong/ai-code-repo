#!/usr/bin/env bash
# 彻底重置 Langfuse：清容器 + 清数据卷 + 校验凭据 + 重启
set -uo pipefail
cd /opt/ai-code/langfuse || exit 1

echo "=== current volumes ==="
docker volume ls --format '{{.Name}}' | grep langfuse || echo "(none)"

echo
echo "=== down -v ==="
docker compose down -v --remove-orphans 2>&1 | tail -3

echo
echo "=== force remove leftover volumes ==="
for v in langfuse_postgres_data langfuse_redis_data langfuse_clickhouse_data langfuse_clickhouse_logs langfuse_minio_data; do
  if docker volume inspect "$v" >/dev/null 2>&1; then
    docker volume rm -f "$v" >/dev/null 2>&1 && echo "removed $v"
  fi
done
docker volume ls --format '{{.Name}}' | grep langfuse || echo "(no langfuse volumes left)"

echo
echo "=== verify credentials against a fresh temp postgres (proof of correctness) ==="
set -a; . ./.env; set +a
docker run --rm --network host -e PGPASSWORD="$POSTGRES_PASSWORD" postgres:17 \
  psql -h 127.0.0.1 -p 15432 -U postgres -d postgres -tAc 'select 1' 2>&1 | tail -2 || true

echo
echo "=== up -d ==="
docker compose up -d 2>&1 | tail -6

echo
echo "=== wait 90s then status ==="
sleep 90
docker ps --filter "name=langfuse" --format 'table {{.Names}}\t{{.Status}}'
echo
curl -s -o /dev/null -w "langfuse web health: %{http_code}\n" --max-time 10 http://localhost:3000/api/public/health || true
