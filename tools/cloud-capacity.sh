#!/usr/bin/env bash
echo "=== 云端资源余量（Week 20 要再加 Prometheus + Grafana）==="
free -m | awk '/Mem:/{print "  mem total/available: "$2" / "$7" MB"}'
free -m | awk '/Swap:/{print "  swap used/total   : "$3" / "$2" MB"}'
df -h / | awk 'NR==2{print "  disk avail        : "$4" (used "$5")"}'
echo "  containers        : $(docker ps -q | wc -l)"
echo
echo "=== 现有镜像总体积 ==="
docker images --format '{{.Size}}' | awk '{k=$1; sub(/GB/,"",k); if ($1 ~ /GB/) s+=k*1024; else {sub(/MB/,"",k); s+=k}} END{printf "  total images: %.0f MB\n", s}'
echo
echo "=== 应用指标端点探测（从服务器能否抓到本地应用的 /actuator/prometheus）==="
echo -n "  localhost:8084 : "
curl -s -o /dev/null -w "%{http_code}\n" --max-time 5 http://127.0.0.1:8084/actuator/prometheus || echo "不可达（应用不在云端，符合预期）"
