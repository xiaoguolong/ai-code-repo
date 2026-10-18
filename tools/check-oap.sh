#!/usr/bin/env bash
# 从服务器侧直接问 OAP（避免依赖本机 12800 转发）
echo "=== OAP 服务列表 ==="
curl -s --max-time 10 -X POST http://127.0.0.1:12800/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"query{listServices(layer:\"GENERAL\"){id name}}"}' | head -c 400
echo
echo "=== 今日 segment 表行数 ==="
D=$(date -u +%Y%m%d)
docker exec pg16 psql -U aidemo -d skywalking -tAc "select count(*) from segment_$D" 2>/dev/null | sed 's/^/  /'
echo "=== 今日上报实例 ==="
docker exec pg16 psql -U aidemo -d skywalking -tAc "select instance_traffic_name from instance_traffic_$D" 2>/dev/null | sed 's/^/  /'
echo "=== OAP 最近日志中的 agent 连接 ==="
docker logs --tail 200 skywalking-oap 2>&1 | grep -aiE 'agent|grpc|connect' | tail -5
