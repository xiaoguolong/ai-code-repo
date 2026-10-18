#!/usr/bin/env bash
# 把本地的两把公钥装到**当前用户**（ubuntu）的 authorized_keys
# 必须以 ubuntu 身份运行（不带 sudo），否则 ~ 会解析成 /root —— 这个坑踩过。
set -uo pipefail

echo "当前用户: $(whoami)    HOME=$HOME"
AK="$HOME/.ssh/authorized_keys"
mkdir -p "$HOME/.ssh"
chmod 700 "$HOME/.ssh"
touch "$AK"
chmod 600 "$AK"

add() {
  if grep -qF "$1" "$AK"; then
    echo "  already present: $(echo "$1" | awk '{print $1, $3}')"
  else
    printf '%s\n' "$1" >> "$AK"
    echo "  added: $(echo "$1" | awk '{print $1, $3}')"
  fi
}

add '__ED25519__'
add '__RSA__'

echo
echo "条目数: $(wc -l < "$AK")"
echo "指纹:"
ssh-keygen -lf "$AK" 2>/dev/null | sed 's/^/  /'
echo
ls -l "$AK"
