#!/usr/bin/env bash
# 为 ubuntu 用户开启密码登录（供 Xshell 的 Password 模式使用）
#   - 生成强随机密码（仅十六进制字符，避免 shell/终端转义问题）
#   - sshd 用 00- 前缀的 drop-in：sshd 是「先读到的值生效」，
#     Ubuntu 云镜像的 50-cloud-init.conf 里有 PasswordAuthentication no，
#     文件必须排在它前面才能覆盖（实测踩过）。
set -uo pipefail

PASS=$(openssl rand -hex 12)   # 24 位十六进制，无特殊字符

echo "=== 设置 ubuntu 密码 ==="
echo "ubuntu:$PASS" | chpasswd && echo "password set"

echo
echo "=== 写 sshd drop-in（00- 前缀，必须排在 cloud-init 的 50- 之前）==="
cat > /etc/ssh/sshd_config.d/00-allow-password.conf <<'EOF'
# 为 Xshell 等 GUI 客户端开启密码登录（密钥登录仍然可用）
PasswordAuthentication yes
KbdInteractiveAuthentication no
EOF
chmod 644 /etc/ssh/sshd_config.d/00-allow-password.conf

echo
echo "=== 语法校验 + 生效值确认 ==="
sshd -t && echo "config OK"
sshd -T 2>/dev/null | grep -iE '^(passwordauthentication|permitrootlogin|pubkeyauthentication)' | sed 's/^/  /'

echo
echo "=== reload sshd ==="
systemctl reload ssh 2>/dev/null || systemctl reload sshd 2>/dev/null || systemctl restart ssh
systemctl is-active ssh

echo
echo "=== 保存密码到文件（600，仅 root 可读；稍后我会取到本地）==="
printf '%s\n' "$PASS" > /root/ubuntu-password.txt
chmod 600 /root/ubuntu-password.txt
cp /root/ubuntu-password.txt /tmp/ubuntu-password.txt
chown ubuntu:ubuntu /tmp/ubuntu-password.txt
chmod 600 /tmp/ubuntu-password.txt
echo "saved (length ${#PASS})"
