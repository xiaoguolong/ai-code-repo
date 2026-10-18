#!/usr/bin/env bash
# 查清两套 PostgreSQL 分别有哪些角色、谁是超级用户
set -uo pipefail
set -a; . /opt/ai-code/data/.env; set +a

echo "=== 业务库 pg16（127.0.0.1:5432）==="
echo -n "当前连接用户: "
docker exec pg16 psql -U "$POSTGRES_USER" -d postgres -tAc "select current_user"
echo "角色一览:"
docker exec pg16 psql -U "$POSTGRES_USER" -d postgres -tAc \
  "select rolname || '  superuser=' || rolsuper || '  cancreatedb=' || rolcreatedb || '  login=' || rolcanlogin from pg_roles where rolname not like 'pg_%' order by rolsuper desc, rolname" \
  | sed 's/^/  /'
echo
echo "数据库与属主:"
docker exec pg16 psql -U "$POSTGRES_USER" -d postgres -tAc \
  "select datname || '  owner=' || pg_get_userbyid(datdba) from pg_database where datname not in ('template0','template1') order by datname" \
  | sed 's/^/  /'

echo
echo "=== Langfuse 自带 PostgreSQL（127.0.0.1:15432）==="
set -a; . /opt/ai-code/langfuse/.env; set +a
LFU="$POSTGRES_USER"
echo -n "超级用户: "
docker exec langfuse-postgres-1 psql -U "$LFU" -d postgres -tAc \
  "select string_agg(rolname, ', ') from pg_roles where rolsuper and rolname not like 'pg_%'"
echo -n "当前 .env 里配置的用户: "; echo "$LFU"
