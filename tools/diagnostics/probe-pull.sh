#!/usr/bin/env bash
# 诊断镜像拉取：确认 mirror 生效 + 显式指定 mirror 主机名拉取
echo "--- daemon.json ---"
cat /etc/docker/daemon.json 2>/dev/null

echo
echo "--- docker info mirrors ---"
docker info 2>/dev/null | grep -A3 "Registry Mirrors"

echo
echo "--- test 1: explicit mirror host (alpine, 3MB, timeout 180s) ---"
START=$(date +%s)
if timeout 180 docker pull mirror.ccs.tencentyun.com/library/alpine:3.20 >/tmp/p1.log 2>&1; then
  echo "OK explicit mirror pull: $(( $(date +%s) - START ))s"
  docker rmi mirror.ccs.tencentyun.com/library/alpine:3.20 >/dev/null 2>&1 || true
else
  echo "FAIL explicit mirror pull"; tail -6 /tmp/p1.log
fi

echo
echo "--- test 2: via docker.io name (mirror rewrite path) ---"
START=$(date +%s)
if timeout 180 docker pull alpine:3.20 >/tmp/p2.log 2>&1; then
  echo "OK docker.io pull: $(( $(date +%s) - START ))s"
  docker rmi alpine:3.20 >/dev/null 2>&1 || true
else
  echo "FAIL docker.io pull"; tail -6 /tmp/p2.log
fi
