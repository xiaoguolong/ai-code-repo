#!/usr/bin/env bash
# 测试 Docker Hub 及各镜像加速器可达性（只读，不改配置）
echo "--- docker info (mirrors) ---"
docker info 2>/dev/null | grep -iA3 "Registry Mirrors" || echo "(none)"

echo
echo "--- pull test: alpine:3.20 (about 3 MB) ---"
START=$(date +%s)
if timeout 90 docker pull alpine:3.20 >/tmp/pull.log 2>&1; then
  END=$(date +%s)
  echo "OK  docker.io direct pull succeeded in $((END-START))s"
  docker rmi alpine:3.20 >/dev/null 2>&1 || true
else
  echo "FAIL docker.io direct pull (see below)"
  tail -5 /tmp/pull.log
fi

echo
echo "--- registry endpoints reachability ---"
for u in https://registry-1.docker.io/v2/ https://docker.m.daocloud.io/v2/ https://docker.1panel.live/v2/ https://mirror.ccs.tencentyun.com/v2/; do
  code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 "$u" || echo "000")
  printf "%-42s %s\n" "$u" "$code"
done

echo
echo "--- disk/mem ---"
df -h / | awk 'NR==2{print "disk: "$4" avail"}'
free -m | awk '/Mem:/{print "mem : "$7" MB available"}'
