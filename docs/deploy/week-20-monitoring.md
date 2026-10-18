# 部署：Week 20 完整监控体系（Prometheus + Grafana + 可选 Alertmanager）

> 目标环境：腾讯云 Ubuntu 22.04.5 / 4C8G，与 SkyWalking 10.2.0、Langfuse 4.63.0 同机。
> 应用仍跑**本地 IDEA / 脚本**，指标通过**反向 SSH 隧道**进入云端 Prometheus。
> 本文不含任何真实密码；Grafana 密码在服务器 `/opt/ai-code/monitoring/.env`（600），
> 仓库只有 `.env.example`。

---

## 1. 架构与端口（先看这一节）

| 组件 | 版本 | 监听 | 访问方式 |
|------|------|------|----------|
| Prometheus | 2.55.1 | `127.0.0.1:19090` | `tools\tunnel.ps1` 本地 `19090` |
| Grafana | 11.3.1 | `127.0.0.1:3001` | 本地 `3001` |
| Alertmanager（可选） | 0.27.0 | `127.0.0.1:9093` | 本地 `9093`（需自行加转发） |

> **为什么不是 9090**：云端 9090 / 9091 已被 Langfuse 的 MinIO（S3 API + 控制台）占用。
> 真机首次部署时 Prometheus 因 `bind: address already in use` 反复重启，`curl 9090`
> 返回的是 S3 的 `AccessDenied` XML —— 「端口看起来通，其实是别的服务」。
> 最终统一用 **19090**。

**网络模式**：Prometheus / Grafana 用 `network_mode: host`。原因：反向隧道只绑云端回环，
桥接容器访问不到宿主回环；host 网络可直接抓 `127.0.0.1:18084`，且**不需要改 sshd
`GatewayPorts`**（那会把端口暴露到 LAN）。代价：不再使用 `ports` 映射，
安全边界改为「只监听 127.0.0.1 + 安全组不开 19090/3001/9093」。

```text
本地 Windows                               云端 Ubuntu
platform 8084 ─┐                       ┌─ Prometheus 127.0.0.1:19090
gateway  8080 ─┴─ ssh -R 18084/18080 ──┘     ├ scrape → 127.0.0.1:18084 / 18080
                                            └ rules → Alertmanager（可选）
                     ssh -L 19090/3001 ──── Grafana 127.0.0.1:3001（浏览器）
```

---

## 2. 前置条件

1. 云端已有 Docker 29.x + Compose v2（`provision.sh` 已装）。
2. 三个镜像已拉取（国内直连 Docker Hub 不可达，用三级回退脚本）：
   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\remote.ps1 -Script .\tools\pull-images.sh
   # 期望最后：OK(tc) prom/prometheus:v2.55.1 / grafana/grafana:11.3.1 / prom/alertmanager:v0.27.0
   ```
3. 安全组**不需要**新增端口。**永远不要**对公网开放 19090 / 3001 / 9093 / 18084 / 18080。

---

## 3. 上传部署物

```powershell
# 1) 本地打包（Windows 自带 tar）
tar -czf target-acceptance\monitoring.tgz -C deploy\monitoring .

# 2) 上传
powershell -NoProfile -ExecutionPolicy Bypass -File .\remote.ps1 -Put ".\target-acceptance\monitoring.tgz /tmp/monitoring.tgz"

# 3) 解包到 /opt/ai-code/monitoring
powershell -NoProfile -ExecutionPolicy Bypass -File .\remote.ps1 -Cmd 'sudo mkdir -p /opt/ai-code/monitoring && sudo tar -xzf /tmp/monitoring.tgz -C /opt/ai-code/monitoring && sudo chown -R ubuntu:ubuntu /opt/ai-code/monitoring'
```

> `-Cmd` 里避免内嵌双引号/`$(...)`：Windows PowerShell 5.1 会把它们拆坏
> （Week 19 已踩过：`grafana: command not found`、`syntax error near unexpected token`）。
> 复杂命令一律写成本地 `.sh` 用 `-Script` 执行。

---

## 4. 生成 .env 并启动

```bash
# 在服务器执行（或写成脚本用 -Script 跑）
cd /opt/ai-code/monitoring
if [ ! -f .env ]; then
  P=$(openssl rand -hex 12)
  {
    echo GRAFANA_ADMIN_USER=admin
    echo GRAFANA_ADMIN_PASSWORD=$P
    echo PROMETHEUS_RETENTION=15d
    echo PROMETHEUS_RETENTION_SIZE=2GB
  } > .env
  chmod 600 .env
fi
docker compose up -d
```

也可以走一键脚本（已含 monitoring 段，幂等）：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\remote.ps1 -Script .\tools\cloud-bootstrap.sh
```

期望：

```
Container prometheus  Started
Container grafana     Started
```

资源实测（新增 2 容器后）：内存可用约 **3.1 GB**、磁盘 19 G / 50 G。

---

## 5. 建立指标反向隧道（本地执行）

```powershell
# 云端 Prometheus 抓本地指标：ssh -R 127.0.0.1:18084 / 18080
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\metrics-tunnel.ps1

# 看状态 / 停止
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\metrics-tunnel.ps1 -Status
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\metrics-tunnel.ps1 -Stop
```

前提：本地平台 8084 / 网关 8080 已启动。脚本会预检云端 18084/18080 是否已被旧隧道占用
（`ExitOnForwardFailure=no` + 启动后自查，避免「以为开了、其实半开」）。

UI 访问走另一条隧道（本地 `-L`）：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\tunnel.ps1
# 浏览器：http://127.0.0.1:19090（Prometheus） / http://127.0.0.1:3001（Grafana）
```

---

## 6. 验收（照抄即可）

### 6.1 云端一键验收

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\remote.ps1 -Script .\tools\cloud-accept-monitoring.sh
```

期望：

```
PASS  Prometheus /-/ready 200
PASS  target ai-code-platform = up
PASS  target ai-code-gateway = up
PASS  target prometheus = up
PASS  已加载 12 条 AiCode* 告警规则
PASS  指标 agent_run_count_total 有数据
PASS  指标 llm_tokens_total 有数据
PASS  指标 tool_call_count_total 有数据
PASS  Grafana /api/health 200
PASS  仪表盘已 provision：aicode-platform-overview / aicode-gateway-overview
PASS  数据源 aicode-prometheus 已注册
PASS=11  FAIL=0
```

### 6.2 真实数据核对（跑一次 Agent 后）

```powershell
# 本地：登录并执行（真实 DeepSeek）
$login = Invoke-RestMethod -Uri http://127.0.0.1:8084/api/v1/platform/auth/login -Method Post `
  -ContentType 'application/json' -Body '{"username":"admin","password":"admin123"}'
$h = @{ Authorization = "Bearer $($login.data.token)"; 'X-Trace-Id' = 'week20-e2e' }
Invoke-RestMethod -Uri http://127.0.0.1:8084/api/v1/platform/agents/medical-assistant/runs `
  -Method Post -Headers $h -ContentType 'application/json' `
  -Body '{"input":{"patientId":"P001","task":"一句话总结健康风险"}}'
```

然后在云端（或通过 19090 转发）查询：

| 查询 | 期望 |
|------|------|
| `up{job="ai-code-platform"}` | `1` |
| `agent_run_count_total` | `>= 1`，标签含 `agent_key` / `execution_status` / `application` |
| `sum by (token_type) (llm_tokens_total)` | prompt / completion 两条 |
| `llm_cost_usd_total` | `> 0`（配了单价） |
| `tool_call_count_total` | 患者/指标工具各一条 |
| `gateway_session_login_count_total` | 登录过即有 |
| `gateway_catalog_lookup_count_total` | `cache_hit=true/false` 各一条 |
| `histogram_quantile(0.95, sum by (le) (rate(agent_run_duration_seconds_bucket[5m])))` | 有值（**需要直方图桶**） |

### 6.3 Grafana

1. 浏览器打开 `http://127.0.0.1:3001`，用 `.env` 里的 admin 密码登录；
2. 左侧 **Dashboards → AI Code**，应看到两张仪表盘；
3. Platform 面板：Agent 速率、失败率、P95、LLM 调用、Token、24h 成本、Tool、HTTP、JVM；
4. Gateway 面板：入站 RPS/5xx/P95、登录成功率、缓存命中率、上游错误、Feign 出站、JVM；
5. 顶部链接可直接跳 SkyWalking / Langfuse（URL 变量可在 Dashboard settings 里改）。

---

## 7. 运维手册

### 7.1 改告警规则（不重启）

```bash
cd /opt/ai-code/monitoring
vi prometheus/alerts.yml
curl -X POST http://127.0.0.1:19090/-/reload
curl -s http://127.0.0.1:19090/api/v1/rules | grep -o '"name":"AiCode[^"]*"'
```

### 7.2 改抓取目标

编辑 `prometheus/prometheus.yml` → `curl -X POST .../-/reload`。
应用上云后把 targets 从 `127.0.0.1:18084/18080` 改成真实地址即可。

### 7.3 数据保留

compose 默认 `--storage.tsdb.retention.time=15d --storage.tsdb.retention.size=2GB`，
任一超限即删除最旧数据块；改 `.env` 的 `PROMETHEUS_RETENTION*` 后 `docker compose up -d`。

### 7.4 启用告警通知

```bash
docker compose --profile alerts up -d
# 编辑 alertmanager/alertmanager.yml 的 receiver（webhook/邮件），然后：
docker compose restart alertmanager
```

---

## 8. 故障排查（按真机踩过的顺序）

| # | 现象 | 根因 | 修法 |
|---|------|------|------|
| 1 | Prometheus 反复重启，日志 `bind: address already in use` | 9090/9091 被 Langfuse MinIO 占用 | 改 `--web.listen-address=127.0.0.1:19090`，同步改抓取自身 target、Grafana 数据源、`monitoring.prometheus.base-url`、隧道端口 |
| 2 | `curl 9090` 返回 `<Error><Code>AccessDenied</Code>` | 打到 MinIO，不是 Prometheus | 同上；用 `ss -lntp` 确认端口归属 |
| 3 | target `ai-code-platform` down | 本地应用没起 / 反向隧道没开 / 隧道半开 | `tools\metrics-tunnel.ps1 -Status`；云端 `ss -lnt | grep 18084`；本地 `curl 8084/actuator/prometheus` |
| 4 | Grafana 面板全空 | Micrometer 标签与 PromQL 不一致 / 直方图桶没开 | 先 `curl 19090/api/v1/query?query=agent_run_count_total` 确认有数据；P95 空则检查 `management.metrics.distribution.percentiles-histogram.*` |
| 5 | 仪表盘没自动出现 | provisioning 路径/权限不对 | `docker logs grafana | grep -i provision`；确认 `/var/lib/grafana/dashboards` 有 JSON、`/etc/grafana/provisioning` 挂载正确 |
| 6 | 告警规则数少于 12 | `/api/v1/rules` 只返回已评估组；或 grep 正则漏了带数字的名字 | 用 `"name":"AiCode[^"]*"`；`/api/v1/rules` 里看 `health` 与 `lastError` |
| 7 | 网关登录 500 / Redis 连接异常 | `.env` 里没有 `SPRING_REDIS_PASSWORD`，或隧道未开 | 检查 `apps/agent-gateway/.env` 与 `tunnel.ps1` 的 6379 |
| 8 | PowerShell 报 `UnexpectedToken` / 中文乱码 | `.ps1` 被写成无 BOM 的 UTF-8 | 用 `UTF8Encoding($true)` 重写；bash 脚本保持无 BOM |
| 9 | `remote.ps1 -Cmd` 里命令被拆坏 | PS 5.1 对内嵌引号/`$()` 的丢失 | 复杂命令写 `.sh` 文件，用 `-Script` 执行 |

---

## 9. 换机器 / 迁移清单

| 项 | 要不要改 |
|----|----------|
| `deploy/monitoring/*` | 不改（纯镜像 + 配置） |
| `server.conf` | 改 HOST / KEY_PATH |
| 安全组 | 不新增端口（19090/3001/9093 都不开） |
| `.env` | 重新生成 Grafana 密码 |
| 镜像源 | 换云后改 `tools/pull-images.sh` 的 mirror 主机 |
| 本地隧道 | 不用改（`tunnel.ps1` / `metrics-tunnel.ps1` 读 `server.conf`） |

---

## 10. 安全基线

| 项 | 状态 |
|----|------|
| Prometheus / Grafana / Alertmanager 监听 | 仅 `127.0.0.1` |
| 安全组 | 不开 19090 / 3001 / 9093 / 18084 / 18080 |
| Grafana 匿名访问 | 关闭；管理员密码仅存服务器 `.env`（600） |
| 指标端点 | 无鉴权的 `/actuator/prometheus` 只被隧道访问，公网不可达 |
| 仓库密钥 | 无；只有 `.env.example` 占位符 |
| 告警通知 | 模板不含 webhook/密钥，由团队自行填写 |
