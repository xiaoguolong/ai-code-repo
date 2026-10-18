#!/usr/bin/env bash
# 预拉本栈全部镜像，三级回退：
#   1) 腾讯云内网 mirror（mirror.ccs.tencentyun.com）—— 同机房，最快
#   2) 华为 SWR 上的「渡渡鸟镜像同步站」（docker.aityp.com 给出的地址）—— 跨云，但同步全
#   3) 官方 registry（国内基本不通，仅兜底）
# 拉完统一打回 Docker Hub 标准 tag，compose 文件不需要改。
set -uo pipefail

TC="mirror.ccs.tencentyun.com"
DDN="swr.cn-north-4.myhuaweicloud.com/ddn-k8s"

pull_one() {
  local canon="$1" path="$2"
  if docker image inspect "$canon" >/dev/null 2>&1; then
    echo "SKIP   $canon"
    return 0
  fi
  local start=$(date +%s)

  # 1) Tencent internal mirror
  if timeout 300 docker pull "$TC/$path" >/tmp/p.log 2>&1; then
    docker tag "$TC/$path" "$canon"; docker rmi "$TC/$path" >/dev/null 2>&1 || true
    echo "OK(tc)  $canon  ($(( $(date +%s) - start ))s)"
    return 0
  fi
  echo "  tc failed, trying ddn..."

  # 2) ddn (aityp) mirror on Huawei SWR
  if timeout 600 docker pull "$DDN/docker.io/$path" >/tmp/p.log 2>&1; then
    docker tag "$DDN/docker.io/$path" "$canon"; docker rmi "$DDN/docker.io/$path" >/dev/null 2>&1 || true
    echo "OK(ddn) $canon  ($(( $(date +%s) - start ))s)"
    return 0
  fi
  echo "  ddn failed, trying official..."

  # 3) official
  if timeout 300 docker pull "$canon" >/tmp/p.log 2>&1; then
    echo "OK(official) $canon  ($(( $(date +%s) - start ))s)"
    return 0
  fi

  echo "FAIL   $canon"
  tail -3 /tmp/p.log | sed 's/^/       /'
  return 1
}

echo "=== stage: data layer ==="
f=0
pull_one "postgres:16" "library/postgres:16" || f=$((f+1))
pull_one "postgres:17" "library/postgres:17" || f=$((f+1))
pull_one "redis:7.2"   "library/redis:7.2"   || f=$((f+1))
pull_one "redis:7"     "library/redis:7"     || f=$((f+1))

echo "=== stage: skywalking ==="
pull_one "apache/skywalking-oap-server:10.2.0" "apache/skywalking-oap-server:10.2.0" || f=$((f+1))
pull_one "apache/skywalking-ui:10.2.0"         "apache/skywalking-ui:10.2.0"         || f=$((f+1))

echo "=== stage: langfuse ==="
pull_one "langfuse/langfuse:4"                 "langfuse/langfuse:4"                 || f=$((f+1))
pull_one "langfuse/langfuse-worker:4"          "langfuse/langfuse-worker:4"          || f=$((f+1))
pull_one "clickhouse/clickhouse-server:25.12"  "clickhouse/clickhouse-server:25.12"  || f=$((f+1))
# cgr.dev/chainguard/minio 不在同步站覆盖范围（只同步 docker.io/ghcr/ghcr/quay/gcr/k8s 等），
# 换成 Docker Hub 官方 minio/minio，功能等价（compose 里已改为可覆盖变量）
pull_one "minio/minio:latest"                  "minio/minio:latest"                  || f=$((f+1))

echo "=== stage: monitoring (Week 20) ==="
pull_one "prom/prometheus:v2.55.1"   "prom/prometheus:v2.55.1"   || f=$((f+1))
pull_one "grafana/grafana:11.3.1"    "grafana/grafana:11.3.1"    || f=$((f+1))
pull_one "prom/alertmanager:v0.27.0" "prom/alertmanager:v0.27.0" || f=$((f+1))

echo
echo "=== images ==="
docker images --format '{{.Repository}}:{{.Tag}}  {{.Size}}' | sort
echo
df -h / | awk 'NR==2{print "disk: used "$3", avail "$4}'
echo "failures: $f"
exit $f
