# 云上中间件环境搭建与本地联通（2026-10-18）

> 本文记录一次完整的「腾讯云装中间件 + 本地通过隧道联通」实践，目的是**换云/换机器时能照着重做**。
> 文中不含任何真实凭据：IP 用 `<PUBLIC_IP>` 占位，密码只写「在哪」不写「是什么」。

---

## 1. 目标与边界

| 项 | 决策 | 理由 |
|----|------|------|
| 应用跑在哪 | **本地 IDEA** | 还在开发期，改代码即生效；没有前端，不需要对外提供 UI |
| 中间件跑在哪 | **腾讯云** | 长期在线、不占本机内存、可以随时重启本机 |
| 两者怎么连 | **SSH 隧道**（`ssh -N -L`） | 数据库/Redis 不暴露公网，云端仍只绑 `127.0.0.1` |
| 隧道怎么常驻 | **Windows 计划任务**（登录自启） | 不用每次手动开 |

这条边界决定了一个重要事实：**云端不存业务代码，本地不存业务数据**。将来要上线，只需把「应用上云」这一步补上，中间件层不用动。

---

## 2. 机器与版本

### 2.1 服务器

| 项 | 值 |
|----|----|
| 云商/地域 | 腾讯云（中国大陆） |
| 规格 | **4 核 8 GB** |
| 系统盘 | **50 GB 通用型 SSD** |
| 带宽 | 3 Mbps（按带宽计费；入网固定 10 Mbps，拉镜像不受 3 Mbps 限制） |
| 操作系统 | **Ubuntu Server 22.04.5 LTS** |
| 内核 / OpenSSH | 5.15.0 / OpenSSH_8.9p1 Ubuntu-3ubuntu0.16 |
| 公网 IP | `<PUBLIC_IP>`（写进 `server.conf`，不入库） |
| Docker | **29.9.1**（含 buildx） |
| Docker Compose | **5.6.0**（v2 插件） |

### 2.2 组件栈

| 组件 | 版本 | 容器名 | 端口（宿主） |
|------|------|--------|--------------|
| PostgreSQL（业务 + SkyWalking 存储） | 16.15 | `pg16` | `127.0.0.1:5432` |
| Redis（业务缓存/会话/记忆） | 7.2.16 | `redis72` | `127.0.0.1:6379` |
| SkyWalking OAP | 10.2.0 | `skywalking-oap` | `0.0.0.0:11800`(gRPC) / `12800`(HTTP) |
| SkyWalking UI | 10.2.0 | `skywalking-ui` | `0.0.0.0:8088` |
| Langfuse Web | 4.63.0 | `langfuse-langfuse-web-1` | `0.0.0.0:3000` |
| Langfuse Worker | 4 | `langfuse-langfuse-worker-1` | `127.0.0.1:3030` |
| ClickHouse（Langfuse 存储） | 25.12 | `langfuse-clickhouse-1` | `127.0.0.1:8123 / 9000` |
| MinIO（Langfuse 对象存储） | chainguard | `langfuse-minio-1` | `127.0.0.1:9090 / 9091` |
| PostgreSQL（Langfuse 元数据） | 17 | `langfuse-postgres-1` | `127.0.0.1:15432` |
| Redis（Langfuse 队列） | 7 | `langfuse-redis-1` | 不发布 |

**资源实测**：10 容器合计占用约 **3.9 GB**（内存 7.6 GB，剩 3.7 GB）；磁盘 17 GB / 50 GB；swap 4 GB。

---

## 3. 为什么是这套组合（关键取舍）

| 决策 | 备选 | 结论 |
|------|------|------|
| 4C8G 而不是 2C4G | 2C4G | **Langfuse(1.75G) + SkyWalking(1.03G) 无法并存于 4G**；8G 才能两套观测同时在线 |
| SkyWalking 存储用 PostgreSQL | BanyanDB / H2 | 10.x 已移除 H2；BanyanDB 再吃一份内存。复用已有 PG 的独立库 `skywalking` 最省 |
| Langfuse 自带全套依赖 | 复用业务 PG/Redis | **隔离**：升级 Langfuse 不碰业务数据；代价是两套 PG/Redis（各几十 MB，可接受） |
| 隧道而不是公网暴露 DB | 绑 `0.0.0.0` + 安全组限源 IP | 隧道零暴露面，且不用给 5432/6379 开规则 |
| 计划任务而不是 `ssh -f` | 手动脚本 | 开机自动、断线可一键重启、有日志 |

---

## 4. 目录与文件布局

### 4.1 服务器 `/opt/ai-code/`

```
/opt/ai-code/
├── data/                 PG16 + Redis7.2（业务库 aidemo + skywalking 库）
│   ├── docker-compose.yml
│   └── .env              600 root —— POSTGRES_PASSWORD / REDIS_PASSWORD
├── skywalking/           OAP + UI
│   ├── docker-compose.yml
│   └── .env              600 root —— SW_JDBC_URL / 数据源账号
├── langfuse/             六容器
│   ├── docker-compose.yml
│   └── .env              600 root —— NEXTAUTH/ENCRYPTION_KEY/SALT/各组件密码/初始化 key
└── app/                  应用配置（本机应用通过隧道时使用）
    ├── platform/.env     600 root —— 业务密钥 + 数据源 + Langfuse key
    └── gateway/.env      600 root
```

### 4.2 本地仓库（新增文件，均不含凭据）

| 文件 | 作用 |
|------|------|
| `server.conf.example` | 连接配置模板（**可提交**）；真实 `server.conf` 被忽略 |
| `provision.sh` | 幂等初始化：时区、swap、Docker（走腾讯镜像）、日志轮转、目录 |
| `remote.ps1` | 远程执行器：`-Test / -Provision / -Cmd / -Script / -Put / -Get / -NoSudo`；输出自动脱敏 |
| `connect-cloud.cmd` | 双击即开 SSH 会话（读 `server.conf` + 私钥） |
| `tools/tunnel.ps1` | 手动开关隧道 + **协议级**探活（`-Status` / `-Stop`） |
| `tools/tunnel-daemon.ps1` | 隧道守护：幂等启动、日志、3 次重试、启动后探活 |
| `tools/register-tunnel-task.ps1` | 注册/卸载 Windows 计划任务 `ai-code-cloud-tunnel` |
| `tools/pull-images.sh` | 三级回退拉镜像并 retag |
| `tools/cloud-bootstrap.sh` | 一键起三套栈（含 .env 生成 + 密码一致性自校验） |
| `tools/write-app-env.sh` | 合并生成应用 `.env`（云端凭据覆盖旧值） |
| `tools/export-middleware-env.sh` | 导出全部中间件账号到 `.env.middleware`（本机用） |
| `tools/verify-local-app.ps1` | 本地应用全链路验收（7 项，带登录重试） |
| `tools/verify-chain.sh` / `check-oap.sh` / `check-aidemo.sh` | 验证数据是否真落到云端 |
| `deploy/data/docker-compose.yml` | 数据层栈（新增，供云上与本地复用） |
| `tools/diagnostics/` | 搭建当天的一次性排查脚本（探测镜像源、重置 Langfuse、查公钥归属等），附 README 说明每个脚本当时的用途与结论 |

`.gitignore` 已新增：`server.conf`、`.env.middleware`、`.ssh-local/`、`id_ed25519*`、`id_rsa*`、`*.ppk`、`.env.bak*`、`.env.*.bak*`。

---

## 5. 从零重建步骤（照抄即可）

### ① 买机器

- 镜像：**Ubuntu Server 22.04 LTS**（不要 CentOS 7，已 EOL；不要 Windows，跑不了 Linux 容器）
- 规格：**4C8G / 50G SSD**，带宽 3 Mbps
- 登录方式：选「密钥对」并**立刻下载 `.pem`**（腾讯云不能二次下载）

### ② 安全组（协议全部 TCP）

| 端口 | 用途 | 来源 |
|------|------|------|
| 22 | SSH | 你的公网 IP `/32` |
| 3000 | Langfuse UI | 你的 IP |
| 8088 | SkyWalking UI | 你的 IP |
| 11800 | OAP gRPC（本地应用挂 Agent 时） | 你的 IP |
| 8084 / 8080 | 平台 / 网关（本地跑时**不需要**开） | — |

**永远不要开**：5432、6379、12800、15432、8123、9090。

### ③ 本地准备连接配置

```powershell
Copy-Item server.conf.example server.conf
# 填 HOST=<公网IP>、USER=ubuntu、AUTH_MODE=key、KEY_PATH=.ssh-local\xxx.pem
```

> ⚠️ 绑密钥对后临时登录用户是 **`ubuntu`**（不是 root；root 的公钥登录被禁用）。这个字母写错会同时报「密码被拒」和「密钥未注册」，极难排查。

### ④ 初始化服务器

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\remote.ps1 -Test
powershell -NoProfile -ExecutionPolicy Bypass -File .\remote.ps1 -Provision
```

`provision.sh` 会做：时区 → 基础工具 → **swap 4G** → **Docker（腾讯 apt 镜像）** → daemon.json（日志轮转 20m×3）→ 目录。

### ⑤ 解决镜像拉取（关键）

国内直连 `registry-1.docker.io` 会超时。实测数据：

| 方式 | 耗时 |
|------|------|
| `docker pull alpine`（走 docker.io 名字） | **121 s**（先等官方超时再回落 mirror） |
| `docker pull mirror.ccs.tencentyun.com/library/alpine`（显式 mirror 名） | **3 s** |

所以策略是「**显式用 mirror 主机名拉，再 retag 回标准名**」，由 `tools/pull-images.sh` 三级回退：

1. `mirror.ccs.tencentyun.com`（腾讯云内网，最快，**仅腾讯云内可用**）
2. `swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/...`（[渡渡鸟镜像同步站](https://docker.aityp.com/)给出的地址，公网可用）
3. 官方 registry（兜底）

> Docker Hub 之外的镜像要注意：`cgr.dev/chainguard/minio` 不在同步站覆盖范围，但**腾讯云内网 mirror 能拉**，所以 Langfuse compose 保持原样。

### ⑥ 起三套栈

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\remote.ps1 -Script .\tools\cloud-bootstrap.sh
```

顺序：`data`（PG+Redis）→ 等 healthy → **建 `skywalking` 库** → `skywalking` → `langfuse`。
`.env` 全部由 `openssl rand` 生成，权限 600，**密码一致性有自校验**。

### ⑦ 验证

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\remote.ps1 -Script .\tools\cloud-accept.sh
```

期望：

```
SkyWalking UI  :8088     200
Langfuse web   :3000     {"status":"OK","version":"4.63.0"}
OAP GraphQL               {"data":{"version":"10.2.0-..."}}
PostgreSQL               aidemo / skywalking
skywalking 库表数量       135
Redis                    PONG (redis_version:7.2.16)
```

### ⑧ 本地联通（隧道）

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\tunnel.ps1          # 手动
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\register-tunnel-task.ps1  # 注册开机自启
```

隧道映射（**不包含 8084** —— 那是本地应用自己要占的端口）：

| 本地 | 云端 | 用途 |
|------|------|------|
| 5432 | PostgreSQL | DBeaver / 应用 |
| 6379 | Redis | 缓存/会话 |
| 3000 | Langfuse | OTLP + UI |
| 8088 | SkyWalking UI | 拓扑/链路 |
| 11800 | OAP gRPC | 本地 Agent 上报 |

### ⑨ 生成应用配置

```powershell
# 1) 本机 .env 传到服务器临时位置
.\remote.ps1 -Put ".\apps\spring-ai-alibaba-agent\.env /opt/ai-code/app/incoming.env"
# 2) 服务器端合并（云端凭据覆盖、CRLF 清理、重复键检查）
.\remote.ps1 -Script .\tools\write-app-env.sh
# 3) 取回（root:600 需先做临时可读副本）
.\remote.ps1 -Cmd "cp /opt/ai-code/app/platform/.env /tmp/p.env && chown ubuntu:ubuntu /tmp/p.env"
.\remote.ps1 -Get "/tmp/p.env apps/spring-ai-alibaba-agent/.env"
.\remote.ps1 -Cmd "rm -f /tmp/p.env"
```

关键：应用侧地址一律 **`127.0.0.1`**（走隧道），不要写云端公网 IP。

### ⑩ 本地应用启动（IDEA）

新建一条**共享运行配置**（放 `.idea/runConfigurations/`，不要改 `workspace.xml`）：

```
Working directory: $PROJECT_DIR$/apps/spring-ai-alibaba-agent
VM options:
  -javaagent:E:\skywalking-agent\skywalking-agent.jar
  -Dskywalking.agent.service_name=ai-code-platform
  -Dskywalking.agent.instance_name=platform-local
  -Dskywalking.collector.backend_service=127.0.0.1:11800
```

验收：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\verify-local-app.ps1
```

期望 7/7 PASS（health / 登录 / trace-context / skywalking-status / agent 目录 / 执行记录 / auth-me）。

再查数据是否真进云：

```powershell
.\remote.ps1 -Script .\tools\check-oap.sh
# 期望：listServices 出现 ai-code-platform；segment 行数 > 0；实例 platform-local
```

---

## 6. 今天踩的坑（按发现顺序，每条都可复现）

| # | 现象 | 根因 | 修法 |
|---|------|------|------|
| 1 | Xshell 报「SSH服务器拒绝了密码」，改用公钥又报「密钥未在远程主机上注册」 | **用户名写成 `ubantu`**（多一个 a）。服务器没有该用户，两种认证都失败，报错却完全不同 | 用 `sshd` 认证日志确认：`Invalid user ubantu` |
| 2 | 公钥明明装好了却不生效 | `remote.ps1` 用 `sudo` 执行 → `~` 解析成 `/root`，公钥写进了 **root 的** authorized_keys | 执行器加 `-NoSudo`；脚本里打印 `whoami` 与 `$HOME` 自证 |
| 3 | `server.conf` 明明填了 HOST，脚本报「未填」 | PowerShell 5.1 `Get-Content` **默认按 ANSI/GBK** 读，无 BOM 的 UTF-8 中文会把换行"吃掉"，键整条丢失 | 所有读配置的地方显式 `-Encoding UTF8` |
| 4 | 应用连库报 `password authentication failed`，密码看着没错 | Windows 上传的 `.env` 带 **CRLF**，值尾部多一个 `\r` | 生成脚本 `tr -d '\r'`，并加 CRLF 检查 |
| 5 | 应用配置里数据源账号变成 `postgres` | 生成脚本 `source` 了 Langfuse 的 `.env`，它的 `POSTGRES_USER=postgres` **覆盖**了业务库的 `aidemo` | source 前先把 data 栈的值固化到 `DB_USER/DB_PASS/REDIS_PASS` |
| 6 | `docker pull` 全部超时 | `registry-1.docker.io` 被墙 | 三级回退镜像；**显式 mirror 名比 docker.io 名字快 40 倍** |
| 7 | Docker 装不上（`Connection reset`） | `download.docker.com` 不可达 | 改用 `mirrors.cloud.tencent.com/docker-ce` 的 apt 源 |
| 8 | Langfuse 反复重启，`P1000 Authentication failed` | ①`DATABASE_URL` 与 `POSTGRES_PASSWORD` 用了两次不同随机值 ②旧的 pg 数据卷没被 `down -v` 清掉，仍在用旧密码 | 同一变量生成两处 + 生成后自校验；`down -v` 后再显式 `docker volume rm` |
| 9 | `.env` 被 shell source 时报 `Code: command not found` | `LANGFUSE_INIT_ORG_NAME=AI Code Repo` **值含空格未加引号** | 改成无空格命名（`AI-Code-Repo`）；官方也不建议加引号 |
| 10 | 改了 `workspace.xml` 的 VM options，IDEA 重启后又变回去 | IDEA 运行时以内存为准，退出时会把旧值写回 | 用 `.idea/runConfigurations/*.xml`（共享配置）而不是 `workspace.xml` |
| 11 | SkyWalking 收不到任何数据，但 `bridgeAvailable=true` | Agent 的 `-Dskywalking.collector.backend_service` 还指向**已关机的旧 VM**；Agent 连不上只静默丢包 | 改成 `127.0.0.1:11800`；查进程命令行确认参数 |
| 12 | 隧道开着时本地应用起不来 | 隧道把 **8084** 也转发了，占用本地端口 | 隧道只转中间件端口，不转应用端口 |
| 13 | 差点把含真实密钥的 `.env.bak-*` 提交进 git | `.gitignore` 的 `.env` 不匹配 `.env.bak-vm`；且我用 `if (git check-ignore ...)` 判**输出**而非**退出码**，误判为"已忽略" | 增加 `.env.bak*` / `.env.*.bak*`；判忽略用 `$LASTEXITCODE` |
| 14 | 远程脚本被截断，报 `unexpected EOF` / `<` 运算符保留字 | 双引号 here-string 里 `\$(...)` 不能转义 `$`，PowerShell 把 bash 的 `$( )` 当子表达式执行 | bash 脚本一律用**单引号 here-string**，变量用占位符 + `.Replace()` |
| 15 | 含中文的 `.ps1` 解析报错 | PowerShell 5.1 读无 BOM 的 UTF-8 会错位 | 写文件时统一 `UTF8Encoding($true)`（带 BOM） |

---

## 7. 安全基线（当前状态）

| 项 | 值 |
|----|----|
| SSH 用户 | `ubuntu`（`sudo -n` 免密提权） |
| 公钥 | 3 把：腾讯云控制台密钥对、ed25519（本地生成）、RSA（Xshell 导入） |
| `PermitRootLogin` | `prohibit-password`（root **不能** SSH 登录，只能 sudo） |
| `/root/.ssh/authorized_keys` | **已清空**（防止误装公钥留下后门） |
| `PasswordAuthentication` | `yes`（保留兜底，24 位随机强密码） |
| 数据库/Redis | 只绑 `127.0.0.1`，公网不可达 |
| 容器日志 | `json-file`，20 MB × 3，防 50G 盘被写满 |
| 凭据落盘 | 服务器 `/opt/ai-code/*/.env`（600 root）；本机 `.ssh-local/`、`.env.middleware`、`server.conf`（均已 gitignore） |

**建议下一步**：密钥稳定后可考虑把 `PasswordAuthentication` 关掉；或至少装 fail2ban。

---

## 8. 换云 / 换机器的迁移清单

### 8.1 必须改的（4 处）

| 位置 | 改什么 |
|------|--------|
| `server.conf` | `HOST`（新公网 IP）、`KEY_PATH`（新私钥文件名） |
| 安全组 | 重新按 §5② 放行（来源填**新的**本机公网 IP） |
| 镜像源 | 腾讯内网 mirror 只在腾讯云有效；换云后改用该云的 mirror，或直接用渡渡鸟（`swr.cn-north-4.myhuaweicloud.com/ddn-k8s/...`，公网通用） |
| Docker apt 源 | 同理：换成新云的 `docker-ce` 镜像地址（或退回发行版 `docker.io`，版本会旧一些） |

### 8.2 不用改的

- 三份 `docker-compose.yml`（纯镜像 + 环境变量，与云商无关）
- 应用侧 `.env`（一律 `127.0.0.1`，走隧道）
- 隧道脚本、计划任务、`remote.ps1`、`provision.sh`
- 版本号（全部锁定在 compose 与脚本里）

### 8.3 数据迁移（如果要把现有数据搬到新机器）

```bash
# 旧机器导出（业务库）
docker exec pg16 pg_dump -U aidemo -d aidemo -Fc > /tmp/aidemo.dump

# 传文件（本地中转或对象存储）
# 新机器导入
docker exec -i pg16 pg_restore -U aidemo -d aidemo --clean --if-exists < /tmp/aidemo.dump
```

SkyWalking 的历史观测数据**不建议迁**（按天分表、量大、价值低），新环境从零开始即可。
Langfuse 的历史 trace 存在 ClickHouse，如需保留，迁 volume 比 dump 更简单：

```bash
docker run --rm -v langfuse_clickhouse_data:/data -v $(pwd):/backup alpine \
  tar czf /backup/clickhouse.tgz -C /data .
```

---

## 9. 验收清单（一页速查）

```powershell
# 1) 隧道
.\tools\tunnel.ps1 -Status
#   期望：5432 PG handshake / 6379 NOAUTH / 3000 200 / 8088 200 / 11800 LISTEN

# 2) 云端服务
.\remote.ps1 -Script .\tools\cloud-accept.sh
#   期望：10 容器 Up；OAP 版本 10.2.0；Langfuse 4.63.0 OK；skywalking 库 135 表

# 3) 本地应用
.\tools\verify-local-app.ps1
#   期望：PASS=7 FAIL=0

# 4) 数据确实进云
.\remote.ps1 -Script .\tools\check-oap.sh
#   期望：ai-code-platform 出现；segment > 0；实例 platform-local

# 5) 数据库种子
.\remote.ps1 -Script .\tools\check-aidemo.sh
#   期望：8 表 / platform_user 3 行 / Flyway V1-V3 全 OK
```

---

## 10. 已知未完成项

| 项 | 状态 | 说明 |
|----|------|------|
| Langfuse trace 验证 | ⏳ | 需跑一次真实模型调用（约 3.5k tokens）才会产生 trace 与 `execution_record` |
| `execution_record` 落库验证 | ⏳ | 同上 |
| 应用上云 | ❌ 不需要 | 暂无前端，本地开发更方便 |
| SkyWalking 自检 `backend` 字段为空 | 小瑕疵 | 只读环境变量 `SW_AGENT_COLLECTOR_BACKEND_SERVICES`，不影响实际上报；可在 `.env` 补一行 |
| Prometheus / Grafana | 未开始 | 后续周次 |
| 隧道断线自愈 | 部分 | `ServerAliveInterval=30` 能恢复多数断连；彻底断开需 `Start-ScheduledTask` |
