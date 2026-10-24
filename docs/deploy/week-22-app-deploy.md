# Week 22 部署手册 — 应用上云（jar + systemd 过渡形态）

> 状态：**资产已就绪，云端尚未执行**。本批次按约定只写文件、不碰服务器。
> 执行前必须先确认第 2 节的三件事；一步步照做即可，出问题按第 7 节回滚。

## 1. 形态与边界

| 项 | 本形态 | 说明 |
|----|--------|------|
| 交付物 | `app.jar` + systemd 单元 | 不是镜像；镜像化留到需要多环境/自动扩缩时 |
| 进程位置 | 云主机（与 PG/Redis/OAP/Langfuse/Prometheus 同机） | 全部中间件只监听 `127.0.0.1`，同机部署零网络改动 |
| 网络暴露 | **只绑回环**；安全组不开 8084/8080 | 对外一律经 nginx（`deploy/app/nginx/aicode.conf.example`） |
| 配置 | 服务端 `/opt/ai-code/app/<target>/.env`（600） | 由 `tools/write-app-env.sh` 生成，**不随 jar 上传** |
| 数据库迁移 | 启动时 Flyway 自动执行 | 平台用 `flyway_schema_history_platform`，与其它应用隔离 |
| 观测 | SkyWalking agent + OTLP + `/actuator/prometheus` | Prometheus 抓取目标从 `127.0.0.1:18084/18080` 改为 `8084/8080`，**反向隧道可以退役** |

**为什么要这个过渡形态**：第 20 周的指标抓取依赖反向 SSH 隧道（本地应用 + 云端 Prometheus），
隧道一断就 target down；应用上云后同机抓取，这条外部依赖直接消失。

## 2. 执行前必须确认（Ask first）

| # | 事项 | 默认建议 | 不确认的后果 |
|---|------|----------|--------------|
| 1 | 允许 `apt install openjdk-17-jre-headless` | 装 | 没有 JDK 17 无法启动 |
| 2 | 应用运行用户 | 新建 `aicode`（与 `.env` 属主一致） | 用 root 跑或 `.env` 读不到（600 + 属主不符） |
| 3 | 安全组保持关闭 8084/8080 | 关闭 | 免鉴权指标端点与登录接口直接暴露公网 |

## 3. 目录与文件

```text
# 仓库内（本批次新增）
deploy/app/systemd/ai-code-platform.service
deploy/app/systemd/ai-code-gateway.service
deploy/app/remote-deploy.sh                 ← 服务端执行：备份 → 切换 → 重启 → 健康检查 → 失败回滚
deploy/app/nginx/aicode.conf.example        ← 前端到位后启用
tools/deploy-app.ps1                        ← 本机一键：构建 → 上传 → 远端切换 → 验收提示

# 云端（执行后）
/opt/ai-code/app/platform/app.jar           ← 当前版本
/opt/ai-code/app/platform/app.jar.prev      ← 上一版（回滚用）
/opt/ai-code/app/platform/.env              ← 600，运行用户可读
/opt/ai-code/app/gateway/...
/opt/ai-code/agent/skywalking-agent.jar     ← tar 上传解压，别用 scp -r（第 19 周踩过目录损坏）
```

## 4. 内存预算（4C8G 上已跑 12 个容器）

| 进程 | 堆 | 上限估算（堆+元空间+栈+直接内存） |
|------|----|----------------------------------|
| 平台 8084 | `-Xmx768m` | 约 1.1–1.3 G |
| 网关 8080 | `-Xmx512m` | 约 0.7–0.9 G |

**先只上平台**，跑 `tools/cloud-capacity.sh` 看余量；**余量 < 800 MB 就暂不上网关**，
或先下调 Prometheus 抓取频率/保留量。JVM 默认按物理内存 1/4 取堆（≈2 G/进程），
不显式给参数会把整机打到 swap——这是本形态最容易犯的错。

## 5. 步骤

### 5.1 一次性准备（服务器）

```bash
# 1) JDK 17（Ubuntu 22.04）
sudo apt-get update && sudo apt-get install -y openjdk-17-jre-headless
java -version          # 必须 17

# 2) 运行用户
sudo useradd -r -s /usr/sbin/nologin aicode || true
sudo mkdir -p /opt/ai-code/app/platform /opt/ai-code/app/gateway /opt/ai-code/agent

# 3) SkyWalking agent（本地打包上传，避免 scp -r）
#    本地：tar czf skywalking-agent.tar.gz -C E:\skywalking-agent .
#    上传：remote.ps1 -Put "<tar> /opt/ai-code/agent/skywalking-agent.tar.gz"
cd /opt/ai-code/agent && sudo tar xzf skywalking-agent.tar.gz && sudo rm -f skywalking-agent.tar.gz

# 4) systemd 单元（从仓库上传后安装）
sudo cp /opt/ai-code/app/ai-code-platform.service /etc/systemd/system/
sudo cp /opt/ai-code/app/ai-code-gateway.service  /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable ai-code-platform.service     # gateway 同理，确认内存后再 enable
```

### 5.2 配置（不改代码）

```bash
# 用现成脚本生成 .env（它负责 CRLF、优先级、唯一性检查、chmod 600）
# 需要先把本机平台 .env 上传为 /opt/ai-code/app/incoming.env
sudo bash /opt/ai-code/app/write-app-env.sh
sudo chown -R aicode:aicode /opt/ai-code/app
```

`.env` 里与上云相关的键（其余沿用平台原配置）：

| 键 | 上云取值 | 原因 |
|----|----------|------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://127.0.0.1:5432/aidemo` | 同机 |
| `SPRING_REDIS_HOST` | `127.0.0.1` | 同机 |
| `LANGFUSE_HOST` | `http://127.0.0.1:3000` | 同机 |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | 视是否启 collector；没有就 `OTEL_EXPORT_ENABLED=false` | 避免无效导出 |
| `SW_AGENT_NAME` / `SW_AGENT_INSTANCE_NAME` | `ai-code-platform` / `platform-cloud` | 拓扑里区分本地/云端 |
| `PLATFORM_RATE_LIMIT_TRUST_FORWARDED_FOR` | 有 nginx 后置 `true`，直连时 `false` | 防伪造头绕过限流 |

### 5.3 部署

```powershell
# 先在本地确认能连、且只打印不执行
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\deploy-app.ps1 -Target platform -DryRun

# 正式部署（会先跑全量测试；CI 已跑过可用 -SkipTests）
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\deploy-app.ps1 -Target platform
```

远端实际做的事由 `deploy/app/remote-deploy.sh` 负责：校验新 jar → 备份 `app.jar.prev` →
切换 → `systemctl restart` → 90 秒内健康检查 → 失败自动回滚。

## 6. 验收（缺一不可）

```bash
# 1) 进程与健康
systemctl status ai-code-platform --no-pager
curl -s http://127.0.0.1:8084/actuator/health          # {"status":"UP"}

# 2) 迁移（首次会 apply V4/V5；重启只 validate）
sudo journalctl -u ai-code-platform -n 200 --no-pager | grep -E 'Flyway|Migrating|Successfully'

# 3) 登录 + 一次真实患者分析（真实 LLM/DB，验证整链）
TOKEN=$(curl -s -X POST http://127.0.0.1:8084/api/v1/platform/auth/login \
  -H 'Content-Type: application/json' -d '{"username":"operator","password":"operator123"}' \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])')

curl -s -X POST http://127.0.0.1:8084/api/v1/platform/agents/patient-analysis/runs \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"input":{"patientId":"P001"}}' | head -c 400
# 期望：status=COMPLETED，outputJson 含 riskLevel / report / memoryId

# 4) 指标
curl -s http://127.0.0.1:8084/actuator/prometheus | grep -E '^(agent_run_count_total|llm_tokens_total)'

# 5) 限流（Week 22 新增，确认没上错版本）
for i in $(seq 1 6); do
  curl -s -o /dev/null -w '%{http_code} ' -X POST http://127.0.0.1:8084/api/v1/platform/auth/login \
    -H 'Content-Type: application/json' -d '{"username":"operator","password":"wrong"}'
done; echo    # 期望：前 5 个 401，第 6 个 429
```

Prometheus 切换（验收通过后再改）：

```bash
# 云端 deploy/monitoring/prometheus/prometheus.yml
#   ai-code-platform: 127.0.0.1:18084 → 127.0.0.1:8084
#   ai-code-gateway : 127.0.0.1:18080 → 127.0.0.1:8080
docker exec prometheus promtool check config /etc/prometheus/prometheus.yml
docker restart prometheus
# 确认 target up 后，tools/metrics-tunnel.ps1 可以停用（反向隧道退役）
```

## 7. 回滚

| 场景 | 命令 |
|------|------|
| 单次部署失败 | `deploy-app.ps1` 会自动回滚；也可 `-Target platform -Rollback` 手工回滚 |
| 回滚后仍不健康 | `sudo journalctl -u ai-code-platform -n 300 --no-pager`，重点看 Flyway / 数据源 / 端口占用 |
| 需要临时停服 | `sudo systemctl stop ai-code-platform`（数据不受影响） |
| 回滚指标抓取 | Prometheus target 改回 `127.0.0.1:18084` 并重启 `metrics-tunnel.ps1` |

## 8. 已知风险

- **本形态无镜像、无灰度**：切 jar 是「停 → 换 → 起」，单次约 15–25 s 不可用；演示环境可接受；
- **限流是内存版**：重启后计数清零；多实例时不共享（换 `LoginAttemptPort` 适配器解决）；
- **Flyway 在启动时跑**：库不可达会直接启动失败（这是期望行为，但要有回滚意识）；
- **enterprise 不要一起上**：它的 `.env` 还指向已下线节点，且 `flyway.table`/`baseline-version` 未修，
  直接连共用库会踩「V1 被跳过」的坑（见 Week 21 实现日志 9.6）；
- **`.env` 属主**：`write-app-env.sh` 以 root 写 600，必须 `chown aicode`，否则服务起不来且报错含糊。

## 9. 下一步（不在本批次）

1. 镜像化（多阶段 Dockerfile → 云上本地构建或 TCR 推送 → compose）；
2. CI：`mvn test` + 镜像构建 + 部署，替换 `deploy-app.ps1` 的手工上传；
3. 前端到位：启用 `nginx/aicode.conf.example`，同域收 CORS，打开 `trust-forwarded-for`；
4. 网关上云（内存允许时），Prometheus 同时抓两个目标。
