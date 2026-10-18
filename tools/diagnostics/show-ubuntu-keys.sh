#!/usr/bin/env bash
# 显示 ubuntu 的 authorized_keys 现状（类型/注释/指纹），不输出密钥本体
AK="$HOME/.ssh/authorized_keys"
echo "whoami: $(whoami)  HOME=$HOME"
ls -l "$AK" 2>/dev/null || echo "(文件不存在)"
echo
echo "逐行:"
awk '{printf "  line %d: type=%s comment=%s\n", NR, $1, $3}' "$AK" 2>/dev/null
echo
echo "指纹:"
ssh-keygen -lf "$AK" 2>/dev/null | sed 's/^/  /'
