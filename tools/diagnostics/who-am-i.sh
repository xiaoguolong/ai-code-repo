#!/usr/bin/env bash
# 打印服务器视角看到的客户端来源地址（安全组实际匹配的就是它）
echo "SSH_CLIENT    : ${SSH_CLIENT:-n/a}"
echo "SSH_CONNECTION: ${SSH_CONNECTION:-n/a}"
echo -n "who am i      : "
who am i 2>/dev/null | sed -E 's/.*\(([^)]*)\).*/\1/' || echo n/a
echo -n "当前登录会话  : "
who 2>/dev/null | awk '{print $1, $5}' | tr '\n' ' '
echo
echo "--- 服务器自身公网 IP ---"
curl -s --max-time 8 https://ipinfo.io/ip || echo n/a
