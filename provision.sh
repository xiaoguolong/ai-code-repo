#!/usr/bin/env bash
# ============================================================================
# Week 19/20 cloud bootstrap (idempotent)
#   - timezone / base tools
#   - swap (default 4G)
#   - Docker CE + compose plugin (via Tencent mirror; download.docker.com is
#     unreachable from mainland China VMs)
#   - docker log rotation (critical on a 50G root disk)
#   - deploy directories
# Output is ASCII-only on purpose: Chinese breaks through the PowerShell pipe.
# ============================================================================
set -euo pipefail

SWAP_GB="${SWAP_GB:-4}"
TZ_NAME="${TZ_NAME:-Asia/Shanghai}"
DEPLOY_DIR="${DEPLOY_DIR:-/opt/ai-code}"
DOCKER_MIRROR="${DOCKER_MIRROR:-}"
DOCKER_APT_MIRROR="${DOCKER_APT_MIRROR:-https://mirrors.cloud.tencent.com/docker-ce/linux/ubuntu}"

log() { printf '\n== %s ==\n' "$*"; }

log "timezone -> ${TZ_NAME}"
timedatectl set-timezone "$TZ_NAME" 2>/dev/null || ln -sf "/usr/share/zoneinfo/${TZ_NAME}" /etc/localtime
echo "now: $(date '+%Y-%m-%d %H:%M:%S %Z')"

log "base tools"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq >/dev/null 2>&1 || true
apt-get install -y -qq ca-certificates curl gnupg git jq htop unzip tree lsof net-tools >/dev/null 2>&1 || true
echo "ok"

log "swap ${SWAP_GB}G"
CUR_SWAP=$(free -m | awk '/Swap:/{print $2}')
if [ "${CUR_SWAP:-0}" -gt 0 ]; then
  echo "swap already present: ${CUR_SWAP} MB"
else
  rm -f /swapfile
  if ! fallocate -l "${SWAP_GB}G" /swapfile 2>/dev/null; then
    dd if=/dev/zero of=/swapfile bs=1M count=$((SWAP_GB*1024)) status=none
  fi
  chmod 600 /swapfile
  mkswap /swapfile >/dev/null
  swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
  echo "swap enabled: $(free -m | awk '/Swap:/{print $2}') MB"
fi
sysctl -w vm.swappiness=10 >/dev/null 2>&1 || true
grep -q '^vm.swappiness' /etc/sysctl.conf || echo 'vm.swappiness=10' >> /etc/sysctl.conf

log "docker"
if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  echo "already installed: $(docker --version)"
else
  CODENAME=$(. /etc/os-release; echo "${VERSION_CODENAME:-jammy}")
  ARCH=$(dpkg --print-architecture)
  echo "repo: ${DOCKER_APT_MIRROR} (${CODENAME}/${ARCH})"
  install -m 0755 -d /etc/apt/keyrings
  if curl -fsSL --max-time 20 "${DOCKER_APT_MIRROR}/gpg" -o /etc/apt/keyrings/docker.asc; then
    chmod a+r /etc/apt/keyrings/docker.asc
    echo "deb [arch=${ARCH} signed-by=/etc/apt/keyrings/docker.asc] ${DOCKER_APT_MIRROR} ${CODENAME} stable" \
      > /etc/apt/sources.list.d/docker.list
    apt-get update -qq >/dev/null 2>&1 || true
    if apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin >/dev/null 2>&1; then
      echo "installed from docker-ce mirror"
    else
      echo "docker-ce mirror failed, falling back to distro package"
      apt-get install -y -qq docker.io >/dev/null 2>&1 || true
      apt-get install -y -qq docker-compose-v2 >/dev/null 2>&1 || true
    fi
  else
    echo "cannot fetch gpg key, falling back to distro package"
    apt-get install -y -qq docker.io >/dev/null 2>&1 || true
    apt-get install -y -qq docker-compose-v2 >/dev/null 2>&1 || true
  fi
fi
systemctl enable --now docker >/dev/null 2>&1 || true

log "docker daemon config"
mkdir -p /etc/docker
if [ -n "${DOCKER_MIRROR}" ]; then
  cat > /etc/docker/daemon.json <<EOF
{
  "registry-mirrors": ["${DOCKER_MIRROR}"],
  "log-driver": "json-file",
  "log-opts": { "max-size": "20m", "max-file": "3" }
}
EOF
  echo "registry mirror: ${DOCKER_MIRROR}"
else
  cat > /etc/docker/daemon.json <<'EOF'
{
  "log-driver": "json-file",
  "log-opts": { "max-size": "20m", "max-file": "3" }
}
EOF
  echo "no registry mirror configured (log rotation enabled)"
fi
systemctl restart docker >/dev/null 2>&1 || true

log "directories"
mkdir -p "${DEPLOY_DIR}"/skywalking "${DEPLOY_DIR}"/langfuse "${DEPLOY_DIR}"/data
usermod -aG docker "${SUDO_USER:-ubuntu}" 2>/dev/null || true

log "result"
echo "docker : $(docker --version 2>/dev/null || echo n/a)"
echo "compose: $(docker compose version --short 2>/dev/null || echo n/a)"
free -m | awk '/Mem:/{print "mem    : "$2" MB total, "$7" MB available"}'
free -m | awk '/Swap:/{print "swap   : "$2" MB"}'
df -h / | awk 'NR==2{print "disk   : "$2" total, "$4" avail"}'
echo "deploy : ${DEPLOY_DIR}"
