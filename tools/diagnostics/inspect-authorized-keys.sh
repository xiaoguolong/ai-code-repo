#!/usr/bin/env bash
# 检查 ubuntu 的 authorized_keys 每行格式（只显示类型/注释/长度，不显示密钥本体）
AK="$HOME/.ssh/authorized_keys"
echo "=== 文件属性 ==="
ls -l "$AK"
echo "home: $(ls -ld "$HOME" | awk '{print $1, $3, $4}')"
echo "ssh dir: $(ls -ld "$HOME/.ssh" | awk '{print $1, $3, $4}')"

echo
echo "=== 逐行分析（NR / 长度 / 字段1 / 字段3注释 / 是否含CR）==="
awk '{
  hascr = (index($0, "\r") > 0) ? "YES" : "no";
  printf "line %d: len=%d type=%s comment=%s CR=%s\n", NR, length($0), $1, $3, hascr
}' "$AK"

echo
echo "=== 是否有多余空行/重复 ==="
grep -c '^$' "$AK" | sed 's/^/empty lines: /'
sort "$AK" | uniq -d | wc -l | sed 's/^/duplicate lines: /'

echo
echo "=== sshd 允许的公钥算法（截断显示）==="
sudo -n sshd -T 2>/dev/null | grep -i '^pubkeyacceptedalgorithms' | cut -c1-160
