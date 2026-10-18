#!/usr/bin/env bash
# 兼容 Xshell：同时允许 password 与 keyboard-interactive 两种口令认证
# 背景：部分 Xshell 版本的 "Password" 方法实际发的是 keyboard-interactive 请求，
#       若 sshd 关闭 KbdInteractiveAuthentication，表现就是「SSH服务器拒绝了密码」。
set -uo pipefail
cat > /etc/ssh/sshd_config.d/00-allow-password.conf <<'EOF'
# GUI 客户端（Xshell / SecureCRT 等）口令登录
PasswordAuthentication yes
KbdInteractiveAuthentication yes
UsePAM yes
# root 仍然只允许密钥
PermitRootLogin prohibit-password
EOF
chmod 644 /etc/ssh/sshd_config.d/00-allow-password.conf
sshd -t && echo "config OK"
systemctl reload ssh
echo "--- effective ---"
sshd -T 2>/dev/null | grep -iE '^(passwordauthentication|kbdinteractiveauthentication|permitrootlogin|usepam)'
systemctl is-active ssh
