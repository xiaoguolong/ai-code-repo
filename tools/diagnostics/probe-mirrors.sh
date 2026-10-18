#!/usr/bin/env bash
# 探测服务器可达的 Docker 镜像站与当前状态（只读）
echo "--- tencent mirror ---"
curl -s -o /dev/null -w "mirrors.cloud.tencent.com  : %{http_code} (%{time_total}s)\n" --max-time 12 \
  https://mirrors.cloud.tencent.com/docker-ce/linux/ubuntu/dists/jammy/Release || echo "tencent: FAILED"

echo "--- aliyun mirror ---"
curl -s -o /dev/null -w "mirrors.aliyun.com         : %{http_code} (%{time_total}s)\n" --max-time 12 \
  https://mirrors.aliyun.com/docker-ce/linux/ubuntu/dists/jammy/Release || echo "aliyun: FAILED"

echo "--- huawei mirror ---"
curl -s -o /dev/null -w "mirrors.huaweicloud.com    : %{http_code} (%{time_total}s)\n" --max-time 12 \
  https://mirrors.huaweicloud.com/docker-ce/linux/ubuntu/dists/jammy/Release || echo "huawei: FAILED"

echo "--- docker official (expected to fail) ---"
curl -s -o /dev/null -w "download.docker.com        : %{http_code}\n" --max-time 8 \
  https://download.docker.com/linux/ubuntu/dists/jammy/Release || echo "download.docker.com        : FAILED"

echo "--- apt repo docker.io ---"
apt-cache policy docker.io 2>/dev/null | head -3

echo "--- state ---"
free -m | awk '/Swap:/{print "swap   : "$2" MB"}'
df -h / | awk 'NR==2{print "disk   : "$2" total, "$4" avail"}'
if command -v docker >/dev/null 2>&1; then
  echo "docker : $(docker --version)"
else
  echo "docker : not installed"
fi
