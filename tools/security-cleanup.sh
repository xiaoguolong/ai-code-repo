#!/usr/bin/env bash
# 安全收尾
#   1) 把临时弱密码换成 24 位随机强密码（保留密码登录作为兜底，因为密钥已在 Xshell 配好）
#   2) 清掉 /root/.ssh/authorized_keys 里误装的公钥（root 只允许 sudo 使用，不允许 SSH 直连）
set -uo pipefail

echo "=== 1) 轮换 ubuntu 密码 ==="
NEWPASS=$(openssl rand -hex 12)
echo "ubuntu:$NEWPASS" | chpasswd && echo "rotated (len=${#NEWPASS})"
printf '%s\n' "$NEWPASS" > /root/ubuntu-password.txt
chmod 600 /root/ubuntu-password.txt
cp /root/ubuntu-password.txt /tmp/ubuntu-password.txt
chown ubuntu:ubuntu /tmp/ubuntu-password.txt
chmod 600 /tmp/ubuntu-password.txt

echo
echo "=== 2) 清理 root 名下误装的公钥 ==="
RK=/root/.ssh/authorized_keys
if [ -f "$RK" ]; then
  echo "清理前条目数: $(wc -l < "$RK")"
  : > "$RK"
  chmod 600 "$RK"
  echo "清理后条目数: $(wc -l < "$RK")（root 不再接受任何 SSH 公钥）"
else
  echo "/root/.ssh/authorized_keys 不存在，跳过"
fi

echo
echo "=== 3) 当前生效的认证策略 ==="
sshd -T 2>/dev/null | grep -iE '^(passwordauthentication|kbdinteractiveauthentication|permitrootlogin|pubkeyauthentication)' | sed 's/^/  /'

echo
echo "=== 4) ubuntu 名下的公钥（应仍是 3 把）==="
ssh-keygen -lf /home/ubuntu/.ssh/authorized_keys 2>/dev/null | sed 's/^/  /'
