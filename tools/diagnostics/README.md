# tools/diagnostics —— 一次性排查与修复脚本（归档）

这里的脚本**不是日常运维工具**，而是搭建当天为了定位问题现场写的。
保留它们的原因：下次遇到同样症状时，可以直接对照着跑，而不是重新推演。

| 脚本 | 当时用来排查什么 | 结论 |
|------|------------------|------|
| `probe-mirrors.sh` | 服务器能通哪些 Docker apt 镜像站 | `download.docker.com` 被重置；`mirrors.cloud.tencent.com` 0.09s 可用 |
| `probe-registry.sh` | Docker Hub 与各 registry 加速器可达性 | `registry-1.docker.io` 超时；腾讯内网 mirror / 华为 SWR / 1panel 均可用 |
| `probe-pull.sh` | 对比「显式 mirror 名」与「docker.io 名字」的拉取耗时 | 3 s vs 121 s，差 40 倍 → 决定预拉 + retag 策略 |
| `fix-langfuse.sh` | Langfuse `P1000` 认证失败 | 发现旧数据卷未被清理；确认密码不一致 |
| `reset-langfuse.sh` | 彻底重置 Langfuse（清卷 + 重建） | 解决 P1000 的最终手段 |
| `set-temp-password.sh` | 判断 Xshell 失败是否由密码输入引起 | 临时设为易输入密码；后续已轮换为强密码 |
| `allow-kbdinteractive.sh` | 兼容 Xshell 的 keyboard-interactive 认证 | 已被 `tools/harden-ssh.sh` 取代 |
| `show-ubuntu-keys.sh` | 查看 `ubuntu` 名下已安装哪些公钥 | 确认公钥是否装对了用户 |
| `inspect-authorized-keys.sh` | 检查 `authorized_keys` 行格式（CR、重复、注释） | 发现脚本以 root 运行，`~` 指向 `/root` |
| `install-ubuntu-keys.sh` | 以 `ubuntu` 身份安装公钥（配 `-NoSudo`） | 修复「公钥装错用户」问题 |
| `who-am-i.sh` / `peer-ip.sh` | 查服务器视角的客户端 IP（安全组要填的值） | 得到本机公网 IP |
| `auth-log.sh` | 看 sshd 认证日志 | **决定性证据**：`Invalid user ubantu`（用户名拼错） |
| `cloud-verify.sh` | 早期版本的服务健康检查 | 已由 `tools/cloud-accept.sh` 取代 |

> 这些脚本多为**只读探测**，`reset-langfuse.sh` 与 `set-temp-password.sh` 会改动服务器状态，请勿随手执行。
