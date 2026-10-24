#!/usr/bin/env bash
# 把云上中间件的真实凭据写入应用 .env（在服务器端就地合并，密钥不回显）
# 输入：/opt/ai-code/app/incoming.env（从本地 scp 上来的平台 .env，含 LLM/OCR 等业务密钥）
# 输出：/opt/ai-code/app/platform/.env、/opt/ai-code/app/gateway/.env（600，root）
set -uo pipefail

BASE=/opt/ai-code
APP=$BASE/app
INCOMING=$APP/incoming.env
mkdir -p "$APP/platform" "$APP/gateway"

[ -f "$INCOMING" ] || { echo "ERROR: $INCOMING missing (请先 -Put 上传平台 .env)"; exit 1; }

set -a; . "$BASE/data/.env"; set +a

# ⚠️ 必须先固化 data 栈的变量再 source langfuse：
#    Langfuse 的 .env 里同样有 POSTGRES_USER / POSTGRES_PASSWORD（值是 postgres / 另一套密码），
#    source 会把 data 的值覆盖掉，结果应用配置写成 postgres 账号 → pg16 里根本没有这个用户 →
#    "password authentication failed"。本项目实测踩过。
DB_USER="$POSTGRES_USER"
DB_PASS="$POSTGRES_PASSWORD"
REDIS_PASS="$REDIS_PASSWORD"

set -a; . "$BASE/langfuse/.env"; set +a

PG_URL="jdbc:postgresql://127.0.0.1:5432/aidemo"
LF_HOST="http://127.0.0.1:3000"

# 这些键由云上环境决定，一律以服务器端值为准（避免沿用旧 VM 的地址/密码）
MANAGED='^(SPRING_DATASOURCE_URL|SPRING_DATASOURCE_USERNAME|SPRING_DATASOURCE_PASSWORD|SPRING_REDIS_HOST|SPRING_REDIS_PORT|SPRING_REDIS_PASSWORD|LANGFUSE_HOST|LANGFUSE_PUBLIC_KEY|LANGFUSE_SECRET_KEY|LANGFUSE_PROJECT_ID|LANGFUSE_ENABLED|LANGFUSE_ENVIRONMENT|LANGFUSE_RELEASE|OTEL_EXPORT_ENABLED|SW_AGENT_NAME|SW_AGENT_INSTANCE_NAME|SKYWALKING_ENABLED)='

# 上传的文件来自 Windows，必然带 CRLF；不 strip 掉的话每个值尾部都有 \r，
# 表现为「密码明明正确却认证失败」「JDBC URL 非法」这类灵异问题（本项目已踩过一次）。
echo "=== platform .env ==="
{
  echo "# 云上环境（由 tools/write-app-env.sh 生成，无需手工维护）"
  echo "# 生成时间: $(date '+%Y-%m-%d %H:%M:%S %Z')"
  echo
  tr -d '\r' < "$INCOMING" | grep -vE "$MANAGED" | sed '/^$/N;/^\n$/D'
  cat <<EOF

# ── 云上基础设施（覆盖旧 VM 配置）──
SPRING_DATASOURCE_URL=$PG_URL
SPRING_DATASOURCE_USERNAME=$DB_USER
SPRING_DATASOURCE_PASSWORD=$DB_PASS
SPRING_REDIS_HOST=127.0.0.1
SPRING_REDIS_PORT=6379
SPRING_REDIS_PASSWORD=$REDIS_PASS

# ── Langfuse（云上新实例，key 与旧 VM 不同）──
LANGFUSE_ENABLED=true
LANGFUSE_HOST=$LF_HOST
LANGFUSE_PUBLIC_KEY=$LANGFUSE_INIT_PROJECT_PUBLIC_KEY
LANGFUSE_SECRET_KEY=$LANGFUSE_INIT_PROJECT_SECRET_KEY
LANGFUSE_PROJECT_ID=$LANGFUSE_INIT_PROJECT_ID
LANGFUSE_ENVIRONMENT=cloud
LANGFUSE_RELEASE=${LANGFUSE_RELEASE:-week22}
OTEL_EXPORT_ENABLED=false

# ── SkyWalking（Agent 通过 -javaagent 挂载；这里只给自检展示与手动埋点开关）──
SW_AGENT_NAME=ai-code-platform
SW_AGENT_INSTANCE_NAME=platform-cloud
SKYWALKING_ENABLED=true
EOF
} > "$APP/platform/.env"
chmod 600 "$APP/platform/.env"

echo "=== gateway .env ==="
cat > "$APP/gateway/.env" <<EOF
# 云上网关（由 tools/write-app-env.sh 生成）
GATEWAY_ROUTE_PLATFORM_URI=http://127.0.0.1:8084
SPRING_REDIS_HOST=127.0.0.1
SPRING_REDIS_PORT=6379
SPRING_REDIS_PASSWORD=$REDIS_PASS
GATEWAY_SESSION_TOKEN_NAME=Authorization
GATEWAY_TOKEN_TTL_SECONDS=1800
GATEWAY_CATALOG_CACHE_TTL_SECONDS=30
GATEWAY_IDEMPOTENCY_TTL_SECONDS=300
SW_AGENT_NAME=ai-code-gateway
SW_AGENT_INSTANCE_NAME=gateway-cloud
EOF
chmod 600 "$APP/gateway/.env"

echo "=== 结果：只列键名，不显示值 ==="
for f in "$APP/platform/.env" "$APP/gateway/.env"; do
  echo "--- $f ($(stat -c%s "$f") bytes, $(stat -c%a "$f")) ---"
  grep -oE '^[A-Za-z_][A-Za-z0-9_]*=' "$f" | tr -d '=' | tr '\n' ' '
  echo
done

echo
echo "=== 连通性自检（用写好的凭据实测）==="
set -a; . "$APP/platform/.env"; set +a

# 直接从 data/.env 取权威值做校验（避免 platform/.env 里任何格式问题干扰判断）
set -a; . "$BASE/data/.env"; set +a
docker run --rm --network host -e PGPASSWORD="$POSTGRES_PASSWORD" postgres:16 \
  psql -h 127.0.0.1 -p 5432 -U "$POSTGRES_USER" -d aidemo -tAc 'select 1' 2>&1 | tail -1 | sed 's/^/postgres(aidemo): /'

docker exec redis72 redis-cli -a "$REDIS_PASSWORD" ping 2>/dev/null | sed 's/^/redis: /'

curl -s -o /dev/null -w "langfuse: %{http_code}\n" --max-time 8 http://127.0.0.1:3000/api/public/health

echo
echo "=== 重复键检查（应为空）==="
grep -oE '^[A-Za-z_][A-Za-z0-9_]*=' "$APP/platform/.env" | sort | uniq -d | sed 's/^/DUPLICATE: /'
echo "(以上为空则该文件无重复键)"
echo
echo "=== CRLF 检查（应为空）==="
if grep -q $'\r' "$APP/platform/.env" 2>/dev/null; then echo "WARNING: still contains CR"; else echo "LF only: OK"; fi

rm -f "$INCOMING" && echo "incoming.env 已删除"
