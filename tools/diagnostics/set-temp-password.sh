#!/usr/bin/env bash
# 临时设置一个「易手输」的密码，用于判断 Xshell 的失败是否由粘贴/输入造成
# 注意：这是临时措施；确认可用后应改回强密码（chpasswd 即可，见文末注释）。
set -uo pipefail

TEMP_PASS="CloudLab2026tx"   # 14 位，纯字母数字，无特殊符号，方便手输

echo "ubuntu:$TEMP_PASS" | chpasswd && echo "password updated"
echo "length=${#TEMP_PASS}"

# 记录到文件（600），并避免在输出里回显
printf '%s\n' "$TEMP_PASS" > /root/ubuntu-password-temp.txt
chmod 600 /root/ubuntu-password-temp.txt
cp /root/ubuntu-password-temp.txt /tmp/ubuntu-password-temp.txt
chown ubuntu:ubuntu /tmp/ubuntu-password-temp.txt
chmod 600 /tmp/ubuntu-password-temp.txt
echo "saved to /tmp/ubuntu-password-temp.txt (for retrieval)"

echo "--- sshd 现状 ---"
sshd -T 2>/dev/null | grep -iE '^(passwordauthentication|kbdinteractiveauthentication|permitrootlogin)'
systemctl is-active ssh
