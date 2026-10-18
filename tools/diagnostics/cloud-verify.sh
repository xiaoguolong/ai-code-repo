#!/usr/bin/env bash
# 三栈健康验证：容器状态 + 端口探测 + 关键日志
echo "=== wait 60s for OAP migration / Langfuse migrations ==="
sleep 60

echo
echo "=== containers ==="
docker ps --format 'table {{.Names}}\t{{.Status}}'

echo
echo "=== http probes ==="
probe() { printf "%-46s %s\n" "$1" "$(curl -s -o /dev/null -w '%{http_code}' --max-time 8 "$2" || echo FAIL)"; }
probe "skywalking UI  http://localhost:8088/"        "http://localhost:8088/"
probe "skywalking OAP http://localhost:12800/"       "http://localhost:12800/"
probe "langfuse web  http://localhost:3000/api/public/health" "http://localhost:3000/api/public/health"

echo
echo "=== langfuse health detail ==="
curl -s --max-time 8 http://localhost:3000/api/public/health | head -c 400
echo

echo
echo "=== langfuse data layer (via its own network) ==="
docker exec langfuse-langfuse-web-1 sh -lc 'echo web-ok' 2>/dev/null || echo "web exec failed"

echo
echo "=== OAP errors (last 15 lines mentioning ERROR/Exception) ==="
docker logs skywalking-oap 2>&1 | grep -iE "error|exception" | tail -8 || echo "(no errors)"
echo
echo "=== OAP listening/DB init hint ==="
docker logs skywalking-oap 2>&1 | grep -iE "started|postgresql|jdbc" | tail -6 || true

echo
echo "=== langfuse web errors ==="
docker logs langfuse-langfuse-web-1 2>&1 | grep -iE "error|exception" | tail -6 || echo "(no errors)"

echo
echo "=== resources ==="
free -m | awk '/Mem:/{print "mem : "$2" MB total, "$7" MB available"}'
df -h / | awk 'NR==2{print "disk: "$3" used, "$4" avail"}'
docker stats --no-stream --format '{{.Name}}: {{.MemUsage}}' | head -12
