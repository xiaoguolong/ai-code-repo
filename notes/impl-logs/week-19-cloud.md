# Week 19 实现日志（补充批次）— 云上中间件环境与本地联通

- 日期：2026-10-18
- 批次：Week 19 / 环境批次（不新增业务代码，只换部署底座）
- 对应部署文档：`docs/deploy/week-19-cloud-env.md`
- 对应部署物：`deploy/data/`、`deploy/skywalking/docker-compose.yml`（改网络）
- 对应工具：`remote.ps1`、`provision.sh`、`tools/`（正式 18 个）、`tools/diagnostics/`（归档 14 个）
- 前序批次：`notes/impl-logs/week-19.md`（应用侧接入）

---

## 1. 本批次目标

1. 把 Week 18（Langfuse）+ Week 19（SkyWalking）两套观测后端从 **VMware 3.8G 节点**搬到
   **腾讯云 4C8G**，解决「两套后端无法同时在线」这一硬约束。
2. 建立**可复现的部署路径**：一条幂等脚本能从裸机装到服务全绿，而不是靠手敲命令。
3. 建立**本地 → 云端的联通方式**：应用仍跑在本地 IDEA，中间件在云端，靠 SSH 隧道连。
4. 把这一路的**坑与决策**沉淀成文档 + 脚本，使「换云」只需改 4 处配置。

## 2. 边界

- **Always**：密钥只在服务器端 `openssl rand` 生成、600 权限、不回显；只读探针先于写操作；
  每一步都有可执行验收；凭据类文件一律进 `.gitignore`
- **Never**：把应用部署到云端（暂无前端，本地开发更快）；把 PG/Redis 绑 `0.0.0.0`；
  改 Week 19 已交付的业务代码；把真实密码写进文档/提交

> 本批次的定位：**不改一行业务逻辑**，只换运行环境。因此没有 RED/GREEN 的单元测试循环，
> 取而代之的是「**先写探针 → 确认是红的 → 再实施**」的验证驱动（见 §5）。

## 3. 增量架构（环境视角）

```
本地 Windows（开发机）
├── IDEA ── 应用 :8084 / 网关 :8080
│      │  JDBC / Lettuce / OTLP / gRPC（全部走 127.0.0.1）
│      ▼
├── ssh -N -L 隧道（Windows 计划任务 ai-code-cloud-tunnel 登录自启）
│      5432→PG  6379→Redis  3000→Langfuse  8088→SW UI  11800→OAP
│      ▼
└── 公网 22（安全组仅放行本机 IP）

腾讯云 4C8G / Ubuntu 22.04.5（<PUBLIC_IP>）
├── pg16              PostgreSQL 16.15   127.0.0.1:5432   库：aidemo + skywalking
├── redis72           Redis 7.2.16       127.0.0.1:6379
├── skywalking-oap    SW 10.2.0          0.0.0.0:11800/12800
├── skywalking-ui     SW 10.2.0          0.0.0.0:8088
└── langfuse 六容器    Langfuse 4.63.0    0.0.0.0:3000（其余仅回环）
      web / worker / clickhouse 25.12 / minio / postgres 17 / redis 7
```

关键点：**应用侧配置的地址始终是 `127.0.0.1`**，所以「中间件在本地」还是「在云端」，对应用完全透明 ——
这也是后来写共享运行配置、不用改任何业务配置的原因。

## 4. 新增 / 改动文件清单

### 4.1 部署物（服务器与仓库共用）

| 文件 | 说明 |
|------|------|
| `deploy/data/docker-compose.yml` | **新增** PG16 + Redis7.2；端口仅绑回环；定义 `ai-net` 供 SkyWalking 直连 |
| `deploy/data/.env.example` | **新增** 密码模板 |
| `deploy/skywalking/docker-compose.yml` | **改动** OAP 加入外部网络 `ai-net`，JDBC 用服务名 `postgres:5432`（不再依赖宿主 IP） |

### 4.2 运维工具（`tools/`，正式 18 个）

| 类别 | 文件 |
|------|------|
| 远程执行 | `remote.ps1`（根）`-Test/-Provision/-Cmd/-Script/-Put/-Get/-NoSudo`，输出脱敏 |
| 初始化 | `provision.sh`（根）时区/swap/Docker/日志轮转/目录 |
| 登录 | `connect-cloud.cmd`（根）、`tools/ssh-console.ps1` |
| 隧道 | `tools/tunnel.ps1`（协议级探活）、`tools/tunnel-daemon.ps1`（幂等守护）、`tools/register-tunnel-task.ps1`（计划任务） |
| 镜像 | `tools/pull-images.sh`（三级回退 + retag） |
| 部署 | `tools/cloud-bootstrap.sh`（三栈 + .env 生成 + 密码自校验） |
| 配置 | `tools/write-app-env.sh`（合并生成应用 .env）、`tools/export-middleware-env.sh`（导出账号汇总） |
| 验收 | `tools/verify-local-app.ps1`、`tools/verify-chain.sh`、`tools/cloud-accept.sh`、`tools/cloud-status.sh`、`tools/check-oap.sh`、`tools/check-aidemo.sh`、`tools/pg-roles.sh` |
| 安全 | `tools/harden-ssh.sh`、`tools/enable-password-login.sh`、`tools/security-cleanup.sh` |

### 4.3 归档（`tools/diagnostics/`，14 个 + README）

搭建当天定位问题用的原始脚本，README 记录「当时排查什么 → 结论」。其中 `auth-log.sh` 是
发现「用户名拼成 ubantu」的决定性工具。

### 4.4 仓库配置

| 文件 | 改动 |
|------|------|
| `.gitignore` | 新增 `server.conf`、`.env.middleware`、`.ssh-local/`、`id_ed25519*`、`id_rsa*`、`*.ppk`、`.env.bak*`、`.env.*.bak*` |
| `.gitattributes` | **新增** 按类型锁行尾：`*.sh/*.yml/*.env*` 强制 LF，`*.ps1/*.cmd` 保持 CRLF |

## 5. RED —— 先写探针，确认是红的

这次没有单测可写，但**先看到失败**同样重要：每个验收脚本在实施前跑一遍，确认它确实报错，
否则「通过了」不能证明任何事。

| 探针 | 实施前的结果 | 意义 |
|------|--------------|------|
| `remote.ps1 -Test` | `server.conf 里的 HOST 还没填` | 配置读取链路未通，先修解析 |
| `docker pull alpine` | `dial tcp ... i/o timeout`（Docker Hub 不可达） | 必须先解决镜像源，否则后面全堵 |
| `curl download.docker.com` | `Connection reset` | Docker 装不上，换腾讯 apt 源 |
| `curl mirrors.cloud.tencent.com/docker-ce/.../Release` | `200 (0.09s)` | 确认可用镜像站 |
| `docker pull mirror.ccs.tencentyun.com/library/alpine` | `3s` 成功（对比 `docker.io` 名字 `121s`） | 定下「显式 mirror 名预拉 + retag」策略 |
| Langfuse `docker logs` | `P1000 Authentication failed` | 配置生成有 bug，先修生成逻辑 |
| `tools/verify-local-app.ps1` | `[FAIL] 登录 401` | 竞态：health 先 UP、种子数据未写完 |
| `tools/check-oap.sh`（改参数前） | `listServices: []`、`segment 行数 0` | Agent 指向已关机旧 VM，数据全丢 |
| `tools/tunnel.ps1 -Status` | `8084 platform DOWN`（且占用本地端口） | 隧道不该转发应用端口 |

## 6. GREEN —— 逐步跑通

按依赖顺序推进，每步都有可验证的产出：

1. **连接建立**：`-Test` 输出 `hostname VM-0-11-ubuntu / 4 cores / 7619 MB / Ubuntu 22.04.5`
2. **初始化**：`swap enabled: 4095 MB`、`Docker version 29.9.1`、`compose 5.6.0`
3. **镜像就位**：11 个镜像全部 `SKIP/OK`，0 failures，磁盘 32G 可用
4. **三栈启动**：10 个容器 Up
5. **服务健康**：
   ```
   SkyWalking UI :8088   200
   OAP GraphQL          {"data":{"version":"10.2.0-a0a00b9"}}
   Langfuse      :3000  {"status":"OK","version":"4.63.0"}
   PostgreSQL           aidemo / skywalking（135 张表）
   Redis                PONG, redis_version:7.2.16
   ```
6. **隧道**：五个端口协议级探活通过（PG 握手 / Redis NOAUTH / Langfuse 200 / SW UI 200 / OAP LISTEN）
7. **本地应用**：`PASS=7 FAIL=0`
8. **数据落云**：
   ```
   listServices : [{"name":"ai-code-platform"}]
   segment 行数 : 72
   上报实例     : platform-local
   ```

## 7. 过程中做的纠偏（不是 bug，是方向修正）

| 纠偏 | 原方案 | 改成 | 原因 |
|------|--------|------|------|
| 隧道不再转发 8084 | 六个端口 | 五个端口 | 本地应用要自己占 8084，转发会抢端口 |
| 运行配置放独立文件 | 改 `workspace.xml` | `.idea/runConfigurations/*.xml` | IDEA 运行时以内存为准，退出会覆盖外部修改 |
| 诊断脚本归档 | 全塞 `tools/` | `tools/diagnostics/` + README | 一次性脚本混在正式工具里会误导后来者 |
| 文档归 Week 19 | 原名 `week-20-cloud-env.md` | `week-19-cloud-env.md` | 这套环境承载 Week 18/19 观测后端，是 Week 19 的部署底座 |

## 8. 质量与安全门禁

| 检查 | 结果 |
|------|------|
| 凭据是否回显 | `remote.ps1` 所有输出过 `Protect-Text`，实测无泄漏 |
| 凭据是否入库 | `git check-ignore` 逐一验证（按退出码，不是输出） |
| 差点误提交 | `.env.bak-vm` 两个备份含真实密钥且未被忽略 → 已补规则 |
| SSH 策略 | `PermitRootLogin prohibit-password`、root 名下公钥已清空、口令登录保留强密码兜底 |
| 端口暴露 | 公网仅 22/3000/8088/11800；PG/Redis/ClickHouse/MinIO 均绑回环 |
| 容器日志 | `json-file` 20MB×3，防止 50G 盘被写满 |
| 行尾 | `.gitattributes` 锁死：`.sh` 必为 LF（避免 `bash^M`） |

## 9. 验证证据（真实命令与输出摘要）

```powershell
# 隧道状态（协议级，不是只看端口）
.\tools\tunnel.ps1 -Status
#   127.0.0.1:5432   PostgreSQL   OK (PostgreSQL handshake)
#   127.0.0.1:6379   Redis        OK (Redis, auth required)
#   127.0.0.1:3000   Langfuse     OK (HTTP 200)
#   127.0.0.1:8088   SkyWalking   OK (HTTP 200)
#   127.0.0.1:11800  OAP gRPC     LISTEN

# 云端服务清单
.\remote.ps1 -Script .\tools\cloud-accept.sh
#   10 容器 Up；skywalking 库 135 表；Redis 7.2.16 PONG；Langfuse 4.63.0 OK

# 本地应用全链路
.\tools\verify-local-app.ps1
#   [PASS] health UP
#   [PASS] 登录（第 1 次成功）
#   [PASS] trace-context / skywalking-status(bridgeAvailable=True)
#   [PASS] agent 目录 count=3 / 执行记录 count=0
#   [PASS] auth/me user=admin
#   汇总：PASS=7 FAIL=0

# 数据确实进了云端 OAP
.\remote.ps1 -Script .\tools\check-oap.sh
#   listServices: [{"id":"YWktY29kZS1wbGF0Zm9ybQ==.1","name":"ai-code-platform"}]
#   segment_20261018: 72
#   instance_traffic: platform-local

# 业务库种子与迁移
.\remote.ps1 -Script .\tools\check-aidemo.sh
#   8 表；platform_user: 1|admin|admin, 2|operator|operator, 3|viewer|viewer
#   flyway: V1 platform rbac OK / V2 execution record OK / V3 audit log OK
```

## 10. 真机缺陷表（15 条，全部可复现）

| # | 现象 | 根因 | 处置 |
|---|------|------|------|
| 1 | Xshell 报「拒绝了密码」，改用公钥报「密钥未注册」 | 用户名写成 **`ubantu`**（多一个 a），服务器无此用户 | `auth-log.sh` 看到 `Invalid user ubantu`；改回 `ubuntu` |
| 2 | 公钥装好后仍 `Permission denied` | 脚本经 `sudo` 执行 → `~` = `/root`，公钥写进 root 的 authorized_keys | 执行器加 `-NoSudo`；脚本打印 `whoami/$HOME` 自证 |
| 3 | `server.conf` 填了 HOST，脚本报「未填」 | PS 5.1 `Get-Content` 默认 ANSI/GBK，无 BOM 的 UTF-8 中文吃掉换行 → 键丢失 | 所有配置读取显式 `-Encoding UTF8` |
| 4 | 应用连库 `password authentication failed`，密码看着对 | Windows 上传的 `.env` 带 CRLF，值尾多 `\r` | `tr -d '\r'` + CRLF 检查；`.gitattributes` 根治 |
| 5 | 应用数据源账号变成 `postgres` | `source langfuse/.env` 覆盖了 `POSTGRES_USER` | source 前固化 `DB_USER/DB_PASS/REDIS_PASS` |
| 6 | `docker pull` 全部超时 | `registry-1.docker.io` 被墙 | 三级回退镜像；显式 mirror 名快 40 倍 |
| 7 | Docker 安装失败 `Connection reset` | `download.docker.com` 不可达 | 换 `mirrors.cloud.tencent.com/docker-ce` |
| 8 | Langfuse 反复重启 `P1000` | ①`DATABASE_URL` 与 `POSTGRES_PASSWORD` 两次不同随机值 ②旧数据卷未清 | 同变量生成 + 自校验；`down -v` 后显式 `docker volume rm` |
| 9 | `.env` 被 source 报 `Code: command not found` | `AI Code Repo` 值含空格未引号 | 改无空格命名 |
| 10 | 改 `workspace.xml` 的 VM options 重启后失效 | IDEA 以内存为准，退出时回写旧值 | 改用 `.idea/runConfigurations/*.xml` |
| 11 | SkyWalking 收不到数据但 `bridgeAvailable=true` | Agent 的 `-D` 仍指向已关机旧 VM，连不上只静默丢包 | 改 `127.0.0.1:11800`；查进程命令行确认 |
| 12 | 隧道开启时本地应用起不来 | 隧道转发了 8084，抢占本地端口 | 隧道只转中间件端口 |
| 13 | 差点提交含真实密钥的 `.env.bak-*` | `.env` 不匹配 `.env.bak-vm`；且用 `if (git check-ignore ...)` 判输出而非退出码 | 补 `.env.bak*` 规则；判忽略用 `$LASTEXITCODE` |
| 14 | 远程脚本被截断，报 `unexpected EOF` / `<` 保留字 | 双引号 here-string 里 `\$(...)` 不转义，PowerShell 抢先执行 bash 的 `$( )` | bash 一律用单引号 here-string |
| 15 | 中文 `.ps1` 解析报错 | PS 5.1 读无 BOM UTF-8 错位 | 写文件统一带 BOM |

> 其中 #1、#3、#13 的共同点：**报错信息指向的位置是错的**。真正的判据分别是认证日志、
> 文件编码、`git check-ignore` 的退出码。这类问题不写进日志，下次一定重演。

## 11. 风险与后续输入

| 项 | 状态 | 影响 |
|----|------|------|
| Langfuse trace 未验证 | ⏳ 待跑一次真实模型调用（约 3.5k tokens） | OTLP 上报与 `execution_record` 落库两条链路仍未点亮 |
| 隧道依赖本机开机 | 已知 | 云端无法独立提供服务；应用上云后解除 |
| 单机无高可用 | 已知 | 所有中间件都在一台 4C8G 上，机器故障即全停 |
| 数据无备份策略 | ⚠️ 未做 | 建议加 cron `pg_dump` + 对象存储 |
| Langfuse 无 TTL | ⚠️ 未配 | ClickHouse 会持续增长，50G 盘需关注 |
| 口令登录仍开启 | 已知 | 安全组已限源 IP；稳定后可关 |
| SkyWalking 自检 `backend` 为空 | 小瑕疵 | 仅影响展示，可在 `.env` 补 `SW_AGENT_COLLECTOR_BACKEND_SERVICES` |

## 12. 决策记录（ADR）

| # | 决策 | 备选 | 理由 |
|---|------|------|------|
| 1 | 4C8G 而非 2C4G | 2C4G | Langfuse(1.75G)+SkyWalking(1.03G) 无法并存于 4G；8G 才能两套观测同时在线 |
| 2 | SkyWalking 存储复用 PG 独立库 | BanyanDB / H2 | 10.x 移除 H2；BanyanDB 再吃一份内存 |
| 3 | Langfuse 自带全套依赖 | 复用业务 PG/Redis | 升级 Langfuse 不碰业务数据；多几十 MB 可接受 |
| 4 | SSH 隧道而非公网暴露 DB | 绑 0.0.0.0 + 安全组限源 | 零暴露面，且不必给 5432/6379 开规则 |
| 5 | 隧道做 Windows 计划任务 | 手动脚本 / `ssh -f` | 登录自启、断线一键重启、有日志 |
| 6 | 应用留本地，不上云 | 全上云 | 暂无前端；本地改代码即生效 |
| 7 | 显式 mirror 名预拉 + retag | 配 `registry-mirrors` 后直接用 docker.io 名字 | 后者要先等官方超时（121s vs 3s） |
| 8 | 运行配置用 `.idea/runConfigurations/` | 改 `workspace.xml` | 共享配置文件不会被 IDEA 回写覆盖 |
| 9 | 一次性脚本归档 `tools/diagnostics/` | 全部平铺 `tools/` | 正式工具与临时排查分离，README 记录结论 |
| 10 | 文档归 week-19 | week-20 | 本环境承载 Week 18/19 观测后端，是 Week 19 的部署底座 |
