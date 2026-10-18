#!/usr/bin/env bash
# 云上中间件总验收：容器 / HTTP / 数据库 / Redis / 端口
echo "=== containers ==="
docker ps --format 'table {{.Names}}\t{{.Status}}'

echo
echo "=== http ==="
probe() { printf "%-42s %s\n" "$1" "$(curl -s -o /dev/null -w '%{http_code}' --max-time 8 "$2")"; }
probe "SkyWalking UI  :8088"            "http://localhost:8088/"
probe "Langfuse web  :3000"             "http://localhost:3000/api/public/health"
probe "Langfuse API  health body"       "http://localhost:3000/api/public/health"
echo -n "langfuse health body: "; curl -s --max-time 8 http://localhost:3000/api/public/health | head -c 200; echo

echo
echo "=== OAP GraphQL (version query) ==="
curl -s --max-time 10 -X POST http://localhost:12800/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"query{version}"}' | head -c 200; echo

echo
echo "=== PostgreSQL databases ==="
set -a; . /opt/ai-code/data/.env; set +a
docker exec pg16 psql -U "$POSTGRES_USER" -d postgres -tAc \
  "select datname from pg_database where datname in ('aidemo','skywalking','langfuse') order by 1"

echo
echo "=== SkyWalking tables in 'skywalking' db ==="
docker exec pg16 psql -U "$POSTGRES_USER" -d skywalking -tAc \
  "select count(*) from information_schema.tables where table_schema='public'" | sed 's/^/tables: /'
docker exec pg16 psql -U "$POSTGRES_USER" -d skywalking -tAc \
  "select table_name from information_schema.tables where table_schema='public' and table_name like 'segment%' limit 3"

echo
echo "=== Redis auth ==="
docker exec redis72 redis-cli -a "$REDIS_PASSWORD" ping 2>/dev/null | sed 's/^/redis: /'
docker exec redis72 redis-cli -a "$REDIS_PASSWORD" info server 2>/dev/null | grep -E 'redis_version' | sed 's/^/       /'

echo
echo "=== listening ports ==="
ss -lntp 2>/dev/null | awk 'NR==1 || /:(22|3000|5432|6379|8088|11800|12800) /' | head -12

echo
echo "=== resources ==="
free -m | awk '/Mem:/{print "mem : "$2" MB total, "$7" MB available"}'
free -m | awk '/Swap:/{print "swap: "$2" MB"}'
df -h / | awk 'NR==2{print "disk: "$3" used, "$4" avail"}'
