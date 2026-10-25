#!/usr/bin/env bash
# Week 26 · 发布前配置对齐（幂等）
#   1) 两侧内部令牌：只生成一次，之后复用；脚本只打印指纹，不打印令牌
#   2) enterprise：恢复 Flyway 两个位置（补跑 V6）+ 临时 out-of-order（V7 已先行应用）
#   3) enterprise：RAG 向量存储切回 pgvector
#   4) platform：知识库开关/地址/令牌
set -euo pipefail
umask 077

PE=/opt/ai-code/app/platform/.env
EE=/opt/ai-code/app/enterprise/.env
TS=$(date +%Y%m%d-%H%M%S)
cp -p "$EE" "/root/week26-enterprise.env.$TS.bak"
cp -p "$PE" "/root/week26-platform.env.$TS.bak"
echo "env backups: /root/week26-{enterprise,platform}.env.$TS.bak"

get() { grep -E "^$2=" "$1" | tail -1 | cut -d= -f2- || true; }
set_kv() {
  local f=$1 k=$2 v=$3
  if grep -qE "^$k=" "$f"; then
    sed -i "s|^$k=.*|$k=$v|" "$f"
  else
    printf '%s=%s\n' "$k" "$v" >> "$f"
  fi
}

ENT_TOK=$(get "$EE" ENTERPRISE_INTERNAL_TOKEN)
PLT_TOK=$(get "$PE" PLATFORM_KNOWLEDGE_INTERNAL_TOKEN)
if [ -z "$ENT_TOK" ] && [ -z "$PLT_TOK" ]; then
  TOK=$(openssl rand -hex 32)
  echo "internal token: 新生成（两侧写入同一值）"
elif [ -n "$ENT_TOK" ]; then
  TOK=$ENT_TOK
  echo "internal token: 复用 enterprise 已有值"
else
  TOK=$PLT_TOK
  echo "internal token: 复用 platform 已有值"
fi

set_kv "$EE" ENTERPRISE_INTERNAL_TOKEN "$TOK"
set_kv "$PE" PLATFORM_KNOWLEDGE_INTERNAL_TOKEN "$TOK"
set_kv "$PE" PLATFORM_KNOWLEDGE_BASE_URL "http://127.0.0.1:8081"
set_kv "$PE" PLATFORM_KNOWLEDGE_ENABLED "true"

set_kv "$EE" SPRING_FLYWAY_LOCATIONS "classpath:db/migration,classpath:db/postgresql"
set_kv "$EE" SPRING_FLYWAY_OUT_OF_ORDER "true"
set_kv "$EE" RAG_VECTOR_STORE "pgvector"

chmod 600 "$EE" "$PE"
a=$(get "$EE" ENTERPRISE_INTERNAL_TOKEN); b=$(get "$PE" PLATFORM_KNOWLEDGE_INTERNAL_TOKEN)
if [ -n "$a" ] && [ "$a" = "$b" ]; then
  echo "token aligned: yes (sha256=$(printf '%s' "$a" | sha256sum | cut -c1-12))"
else
  echo "FAIL: 两侧令牌不一致"; exit 1
fi

echo "-- enterprise 关键项 --"
grep -E '^(SPRING_FLYWAY_LOCATIONS|SPRING_FLYWAY_OUT_OF_ORDER|RAG_VECTOR_STORE)=' "$EE" | sed 's/=.*/= <set>/'
echo "-- platform 关键项 --"
grep -E '^(PLATFORM_KNOWLEDGE_ENABLED|PLATFORM_KNOWLEDGE_BASE_URL)=' "$PE"
echo "ENV ALIGN DONE"
