#!/usr/bin/env bash
# =============================================================================
# enterprise 的云上 .env 生成（Week 22 批次 2，为 SSO 真机验收）
# -----------------------------------------------------------------------------
# 输入：/opt/ai-code/app/incoming-enterprise.env（本地 enterprise .env 上传）
# 输出：/opt/ai-code/app/enterprise/.env（600，aicode）
#
# 与平台的区别（都是环境事实，不是偏好）：
#   1) 数据源指向**独立库 `enterprise`**：云上 aidemo 已有 platform 表且没有 enterprise 历史表，
#      直接共用会触发 Flyway「V1 被 baseline 跳过 → V2 缺 app_user 失败」；
#   2) 云端 PG **没有 pgvector 扩展**：flyway locations 限定 classpath:db/migration 跳过 V6，
#      向量库切 memory。要真跑 RAG 必须先装扩展并补 V6；
#   3) SSO 指向同机平台 8084（两个进程都在云上，走回环，无需隧道）。
# =============================================================================
set -euo pipefail

BASE=/opt/ai-code
APP=$BASE/app
INCOMING=$APP/incoming-enterprise.env
OUT=$APP/enterprise/.env

[ -f "$INCOMING" ] || { echo "ERROR: $INCOMING missing（先上传本地 enterprise .env）"; exit 1; }

set -a; . "$BASE/data/.env"; set +a
DB_USER="$POSTGRES_USER"
DB_PASS="$POSTGRES_PASSWORD"
REDIS_PASS="$REDIS_PASSWORD"

# 这些键由云上环境决定，一律覆盖（避免沿用旧 VM 地址/密码/向量库配置）
# 注意：这里按**前缀**过滤（末尾不能要求 `=`，否则 `SPRING_DATASOURCE_` 永远匹配不到
#      `SPRING_DATASOURCE_URL=`，会写出重复键——本脚本首版就踩过，重复键虽然后值生效，但会掩盖配置漂移）。
MANAGED='^(SPRING_DATASOURCE_|SPRING_REDIS_|SPRING_FLYWAY_LOCATIONS|RAG_VECTOR_STORE|SSO_|FILE_STORAGE_DIR|AUTH_PASSWORD_SALT)'
# 注意：SSO_ 也覆盖，避免本地 .env 里残留 SSO_ENABLED=false 把真机验收悄悄关掉。

mkdir -p "$APP/enterprise" "$APP/enterprise/data/files"

{
  echo "# enterprise 云上环境（由 tools/write-enterprise-env.sh 生成，无需手工维护）"
  echo "# 生成时间: $(date '+%Y-%m-%d %H:%M:%S %Z')"
  echo
  tr -d '\r' < "$INCOMING" | grep -vE "$MANAGED" | sed '/^$/N;/^\n$/D'
  cat <<EOF

# ── 云上基础设施（独立库，不与 platform 共用）──
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/enterprise
SPRING_DATASOURCE_USERNAME=$DB_USER
SPRING_DATASOURCE_PASSWORD=$DB_PASS
SPRING_REDIS_HOST=127.0.0.1
SPRING_REDIS_PORT=6379
SPRING_REDIS_PASSWORD=$REDIS_PASS

# ── 该 PG 无 pgvector：只跑 db/migration（V1–V5 + V7），跳过 V6；向量库用内存 ──
SPRING_FLYWAY_LOCATIONS=classpath:db/migration
RAG_VECTOR_STORE=memory

# ── 统一身份（Week 21/22）：委托同机平台校验，登录即建链/开户 ──
SSO_ENABLED=true
SSO_PLATFORM_BASE_URL=http://127.0.0.1:8084
SSO_PLATFORM_TIMEOUT_MS=3000
SSO_AUTO_PROVISION=true

# ── 本地文件存储（上传的文档落这里）──
FILE_STORAGE_DIR=/opt/ai-code/app/enterprise/data/files
EOF
} > "$OUT"

chmod 600 "$OUT"
chown aicode:aicode "$OUT"
chown -R aicode:aicode "$APP/enterprise/data" || true

echo "=== 键名（不显示值）==="
grep -oE '^[A-Za-z_][A-Za-z0-9_]*=' "$OUT" | tr -d '=' | tr '\n' ' '
echo
echo "=== 重复键检查（应为空）==="
grep -oE '^[A-Za-z_][A-Za-z0-9_]*=' "$OUT" | sort | uniq -d | sed 's/^/DUPLICATE: /'
echo "=== CRLF 检查 ==="
if grep -q $'\r' "$OUT" 2>/dev/null; then echo "WARNING: still contains CR"; else echo "LF only: OK"; fi

rm -f "$INCOMING"
echo "incoming-enterprise.env 已删除"
