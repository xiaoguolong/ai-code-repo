#!/usr/bin/env bash
# 收紧 sshd：允许密码登录（给 Xshell），但 root 仍只允许密钥
set -uo pipefail
cat > /etc/ssh/sshd_config.d/00-allow-password.conf <<'EOF'
# GUI 客户端（Xshell）走密码登录；root 仍然只允许密钥
PasswordAuthentication yes
PermitRootLogin prohibit-password
KbdInteractiveAuthentication no
EOF
chmod 644 /etc/ssh/sshd_config.d/00-allow-password.conf
sshd -t && echo "config OK"
systemctl reload ssh
echo "--- effective ---"
sshd -T 2>/dev/null | grep -iE '^(passwordauthentication|permitrootlogin|pubkeyauthentication)'
echo "--- active ---"
systemctl is-active ssh
