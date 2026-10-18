#!/usr/bin/env bash
# 诊断并修复 Langfuse 的 DATABASE_URL / POSTGRES_PASSWORD 不一致
cd /opt/ai-code/langfuse || exit 1
set -a; . ./.env; set +a

# 从 DATABASE_URL 里提取密码（不打印）
URL_PASS=$(printf '%s' "$DATABASE_URL" | sed -E 's#^postgresql://[^:]+:([^@]+)@.*#\1#')

echo "POSTGRES_PASSWORD length : ${#POSTGRES_PASSWORD}"
echo "DATABASE_URL  pass length: ${#URL_PASS}"
if [ "$POSTGRES_PASSWORD" = "$URL_PASS" ]; then
  echo "MATCH: 两者一致"
  echo "=> 不一致应不是主因；可能是 pg 卷用旧密码初始化过"
else
  echo "MISMATCH: 两者不一致 —— 这就是 P1000 的根因"
fi

echo
echo "=== 检查容器实际拿到的密码是否与 postgres 初始化密码一致 ==="
docker inspect langfuse-postgres-1 --format '{{range .Config.Env}}{{println .}}{{end}}' | grep -c '^POSTGRES_PASSWORD=' || true

echo
echo "=== 重置 langfuse（全新，无数据可丢）==="
docker compose down -v >/dev/null 2>&1
rm -f .env
echo "volumes and .env removed"
