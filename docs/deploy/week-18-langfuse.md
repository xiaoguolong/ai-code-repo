# 部署：Week 18 — Langfuse v4 自托管（Docker Compose）

> 目标：在 **192.168.132.128**（可换任意 Linux 主机）用 Docker Compose 起一套 **Langfuse v4**，
> 让 8084 平台把 `agent.run / llm.chat / tool.call` 的 span 经 OTLP 推送到 Langfuse，
> 在 UI 上看到 **trace / generation / token / cost**，并把 Prompt 交给 Langfuse 管理。
>
> 交付物：`deploy/langfuse/docker-compose.yml`、`deploy/langfuse/.env.example`、本文档。
> 代码侧接入见 `docs/specs/week-18.md`、`docs/architecture/week-18-architecture.md`、`docs/api/week-18-api.md`。

---

## 1. 组件与版本水位

| 组件 | 镜像 | 作用 | 版本要求 |
|------|------|------|----------|
| langfuse-web | `docker.langfuse.com/langfuse/langfuse:4` | UI / API / OTLP 摄取入口 | **v4**（当前主线） |
| langfuse-worker | `docker.langfuse.com/langfuse/langfuse-worker:4` | 异步入库 / 评分 / 导出 | 与 web 同 tag |
| ClickHouse | `clickhouse/clickhouse-server:25.12` | 追踪与观测明细 | **≥ 25.12** |
| PostgreSQL | `postgres:17` | 用户 / 项目 / 配置等元数据 | **≥ 15** |
| Redis | `redis:7` | 队列与缓存 | **≥ 7.0** |
| MinIO | `cgr.dev/chainguard/minio` | 事件与媒体对象存储（S3 兼容） | 任意近期版本 |

为什么是 v4 而不是 v3：v3 已进入**仅安全补丁**阶段（维护至 2027-01 底），v3→v4 基础设施组件不变、
只是最低版本水位提高；新部署没有历史包袱，直接上 v4 可以避免一次升级。另一个决定性差异：
**v4 已废弃 `POST /api/public/ingestion` 的 trace/span/generation 事件（返回 400），
OTLP 是唯一受支持的追踪摄取路径**，本周实现正是走 OTLP。

> 官方明确要求用 Docker 容器化部署（仅提供 Compose / Helm / Terraform 路径），没有受支持的「Node 手起 web+worker」方式。

---

## 2. 前置条件

| 项 | 要求 | 检查命令 |
|----|------|----------|
| Docker Engine | 24+ | `docker version` |
| Docker Compose | v2（`docker compose`，非 `docker-compose`） | `docker compose version` |
| 资源 | 建议 ≥ 4 vCPU / 8 GB RAM / 20 GB 可用磁盘（六个容器） | `free -h`、`df -h` |
| 端口 3000 | 需对 8084 所在主机开放（唯一对外的 Langfuse 端口） | `ss -lntp \| grep 3000` |
| 时间同步 | 容器与宿主机 UTC（Langfuse 时间线依赖） | `timedatectl` |

**端口冲突提醒（本环境已知）**：`192.168.132.128:5432` 已被现有业务 PostgreSQL 占用，
因此 Langfuse 的 PostgreSQL 在 compose 中映射为 **宿主 `127.0.0.1:15432`**（只监听本机，容器内仍用 `postgres:5432`），
不要改回 5432。

---

## 3. 部署步骤

### 3.1 上传部署物料

```bash
# 在 192.168.132.128 上
mkdir -p /opt/langfuse && cd /opt/langfuse
# 把仓库的 deploy/langfuse/ 两个文件拷过来（scp / 粘贴均可）
ls   # docker-compose.yml  .env.example
```

### 3.2 生成并填写密钥

```bash
cp .env.example .env

# 三个必填密钥（SALT / ENCRYPTION_KEY 设定后不得再改）
openssl rand -hex 32   # NEXTAUTH_SECRET
openssl rand -hex 32   # ENCRYPTION_KEY
openssl rand -hex 32   # SALT

# 其余口令（Postgres / ClickHouse / Redis / MinIO / 初始用户）自拟，避免使用模板默认值
vi .env
```

需要改的项：

| 变量 | 说明 |
|------|------|
| `NEXTAUTH_URL` | 改成 `http://192.168.132.128:3000`（浏览器与应用访问用） |
| `NEXTAUTH_SECRET` / `ENCRYPTION_KEY` / `SALT` | 上一步生成的 32 字节 hex |
| `POSTGRES_PASSWORD` / `CLICKHOUSE_PASSWORD` / `REDIS_AUTH` / `MINIO_ROOT_PASSWORD` | 自拟强口令 |
| `LANGFUSE_INIT_PROJECT_PUBLIC_KEY` / `LANGFUSE_INIT_PROJECT_SECRET_KEY` | 自定 `pk-lf-…` / `sk-lf-…`，**要与 8084 的 `LANGFUSE_PUBLIC_KEY` / `LANGFUSE_SECRET_KEY` 完全一致** |
| `LANGFUSE_INIT_USER_EMAIL` / `LANGFUSE_INIT_USER_PASSWORD` | 首次登录 UI 用 |

> **不要给 `LANGFUSE_INIT_*` 的值加引号**（官方已知问题 #3398，双引号会导致初始化失败）。

### 3.3 启动

```bash
docker compose up -d
docker compose ps          # 期望：6 个服务 Up，其中 4 个 (healthy)
docker compose logs -f langfuse-web | head -50
```

首次启动会依次：ClickHouse 建库 → worker 跑迁移 → 按 `LANGFUSE_INIT_*` **自动创建组织 / 项目 / API Key / 初始用户**
（无需人工点 Sign up）。

### 3.4 连通性自检

```bash
# 1) Web 健康（200/OK 即服务起来）
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:3000/api/public/health

# 2) 用初始化时设定的 Key 做 Basic 认证（200 = Key 可用；401 = Key 或 base64 写错）
PK=pk-lf-<local-public-key>
SK=sk-lf-<local-secret-key>
curl -s -o /dev/null -w '%{http_code}\n' -u "$PK:$SK" http://localhost:3000/api/public/projects

# 3) 从 8084 所在主机测试网络可达（Windows PowerShell）
#    Test-NetConnection 192.168.132.128 -Port 3000
```

三项都通过再进入下一节。

### 3.5 初始化 Prompt 与模型价格（推荐，一次即可）

新实例是空的：应用会**回退到 classpath 模板**（`prompt_name` 为空），成本只能靠 Langfuse 服务端按模型定义推断。
用交付的脚本一次灌好（**建议用脚本而不是手写 curl**，因为价格单位极易填错）：

```bash
cd ~/langfuse
# 把仓库里的 Prompt 模板与脚本一起拷过来
#   deploy/langfuse/seed-langfuse.py
#   apps/spring-ai-alibaba-agent/src/main/resources/prompts/*.txt  → ./prompts/
export LF_PK=pk-lf-...  LF_SK=sk-lf-...
python3 seed-langfuse.py --prompt-dir ./prompts \
    --model-name deepseek-v4-pro --input-usd-per-1m 0.27 --output-usd-per-1m 1.10
python3 seed-langfuse.py --check          # 复核（会标出「疑似按每 1M 误填」的模型）
```

脚本做三件事并保证幂等：

1. 把 `prompts/<name>-<version>.txt` 作为 **text 型 Prompt** 上传，打 `production` 标签（应用默认拉这个标签）；
2. 按 **每 token** 口径写入模型价格（入参给「每 1M」，脚本内部除以 `1e6`）——同名旧定义先删除；
3. 复核项目 / Prompt / 模型价格，并对 `inputPrice` 大得离谱的定义给出提示。

> 为什么把换算写进脚本：Langfuse 存的是「每 token」价，把 `0.27 USD/1M` 直接填成 `0.27` 会让
> 服务端推断成本**放大 1e6 倍**（实测：一次 262 token 的调用显示上千美元）。这条坑在本周验收时真实踩到，
> 已在实现日志 10.2 ⑥-2 记录。

---

## 4. 应用侧接入（8084）

在 `apps/spring-ai-alibaba-agent/.env` 或环境变量中加入（`.env` 不入库）：

```properties
LANGFUSE_ENABLED=true
LANGFUSE_HOST=http://192.168.132.128:3000
LANGFUSE_PUBLIC_KEY=pk-lf-<local-public-key>
LANGFUSE_SECRET_KEY=sk-lf-<local-secret-key>
LANGFUSE_PROJECT_ID=ai-code-repo
LANGFUSE_ENVIRONMENT=vm-lab
LANGFUSE_RELEASE=week18
# 正文采集默认关闭；验收「Prompt/输出可见」时临时置 true（会经 PII 脱敏 + 截断）
LANGFUSE_CAPTURE_CONTENT=false
# Prompt 管理：需要用 Langfuse 托管 Prompt 时置 true（未配置/不可用时自动回退 classpath 模板）
LANGFUSE_PROMPT_ENABLED=false
```

启动并自检（顺序很重要，先确认「配置生效」，再看「数据到没到」）：

```powershell
# 1) 启动 8084（模块目录下）
.\run-maven-jdk17.ps1 spring-boot:run

# 2) 登录拿 token
$login = Invoke-RestMethod -Method Post -Uri http://localhost:8084/api/v1/platform/auth/login `
  -ContentType 'application/json' -Body '{"username":"admin","password":"<admin-password>"}'
$h = @{ satoken = $login.data.token }

# 3) Langfuse 接入自检（enabled/endpoint/captureContent/维度是否写对；不含密钥）
Invoke-RestMethod -Uri http://localhost:8084/api/v1/platform/observability/langfuse-status -Headers $h |
  ConvertTo-Json -Depth 6

# 4) 跑一次真实 Agent（会真实调用 LLM）
$run = Invoke-WebRequest -Method Post -Uri http://localhost:8084/api/v1/platform/agents/medical-assistant/runs `
  -Headers ($h + @{ 'X-Trace-Id' = 'week18-acceptance' }) -ContentType 'application/json' `
  -Body '{"input":{"patientId":"P001","task":"follow up advice"}}'
"traceparent = " + $run.Headers.traceparent      # 第 2 段即 Langfuse 的 traceId
```

---

## 5. 在 Langfuse 里确认数据

UI：`http://<langfuse-host>:3000` → 选项目 **AI Code Repo Platform** → **Tracing → Traces**。

> ⚠️ **v4 运行在 `events_only` 模式：`GET /api/public/traces`、`GET /api/public/observations` 等旧读接口不可用**，
> 会返回 `This endpoint is not available on deployments running in Langfuse v4 events_only mode`。
> 这**不代表数据没到**（本次实测就被它误导过一次）。v4 的核对方式见 5.2。

```text
trace  name=agent:medical-assistant   userId=1  sessionId=P001  tags=[MEDICAL_ASSISTANT,medical-assistant]
├── span        http post /api/v1/platform/agents/{agentKey}/runs   （root，parent=上游 traceparent 第 3 段）
├── agent       agent.run
├── generation  llm.chat   model=deepseek-v4-pro   usage={input,output,total}   cost={input,output,total}
│   └── span    chat deepseek-v4-pro        （Spring AI 自动埋点，Week 17 已有）
├── generation  llm.chat   model=deepseek-v4-pro   prompt=medical-followup@1
└── tool        tool.call  PatientLookupTool / HealthMetricTool
```

### 5.2 v4 数据面核对（事件库 / Metrics v2）

```bash
# A. 项目与 Key 是否可用（同时也验证 Basic 认证串）
curl -s -o /dev/null -w '%{http_code}\n' -u "$PK:$SK" "$BASE/api/public/projects"

# B. 事件库核对（在目标机上执行；v4 的事件明细就在这两张表）
docker compose exec -T clickhouse clickhouse-client --user clickhouse --password "$CH_PASS" -q "
SELECT name, type, trace_name, user_id, session_id, tags, environment, release
FROM events_full WHERE trace_id = '<otelTraceId>' ORDER BY start_time"

# C. 观测类型分布 / 用量 / 成本 / Prompt 关联
docker compose exec -T clickhouse clickhouse-client --user clickhouse --password "$CH_PASS" -q "
SELECT name, type, provided_model_name, usage_details, cost_details, prompt_name, prompt_version,
       length(input) AS input_len, length(output) AS output_len
FROM events_full WHERE trace_id = '<otelTraceId>' ORDER BY start_time FORMAT Vertical"

# D. Metrics v2（v4 的新指标接口，必须带 query 参数；不带会报 Invalid input: expected string）
curl -s -u "$PK:$SK" "$BASE/api/public/v2/metrics?query=%7B%22view%22%3A%22observations%22%7D" | head -c 400
```

### 5.1 模型价格（不配就没有成本，且单位是「每 token」）

成本由 Langfuse 按 `model` 匹配**模型定义**后计算；未匹配到模型定义 → **不显示成本**。

> ⚠️ **单位陷阱（实测踩过）**：Langfuse 存的 `inputPrice` / `outputPrice` 是**每个 token 的美元价**，
> 不是「每百万 token」。内置价格表也是这个口径（例：`babbage-002` 存 `4e-07`，即 0.4 USD / 1M）。
> 把「0.27 USD / 1M」直接填成 `0.27`，服务端推断的成本会**放大 1e6 倍**
> （实测：一次 265 token 的调用显示 1472.95 USD）。
>
> 换算：`price_per_token = price_per_1M / 1_000_000`，例如 0.27 USD/1M → `2.7e-7`。

```bash
# 先删同名旧定义（避免继续用错误单价），再按「每 token」创建
curl -s -u "$PK:$SK" "$BASE/api/public/models" | python3 -c "
import json,sys
print('\n'.join(m['id'] for m in json.load(sys.stdin)['data'] if m['modelName']=='deepseek-v4-pro'))" \
| while read -r id; do curl -s -u "$PK:$SK" -X DELETE "$BASE/api/public/models/$id" >/dev/null; done

curl -s -u "$PK:$SK" -X POST "$BASE/api/public/models" -H 'Content-Type: application/json' -d '{
  "modelName": "deepseek-v4-pro",
  "matchPattern": "(?i)^(deepseek-v4-pro)$",
  "unit": "TOKENS",
  "inputPrice": 2.7e-7,
  "outputPrice": 1.1e-6
}'
```

> v4 要的是**扁平**价格字段 + 必填 `unit`（`TOKENS`/`CHARACTERS`/`MILLISECONDS`/`SECONDS`/`REQUESTS`/`IMAGES`）；
> 传嵌套 `prices` 会返回 400 `Must provide either flat prices (inputPrice/outputPrice/totalPrice) OR pricingTiers`。
> 价格变更**只对之后的新 generation 生效**。
> 客户端上报的 `cost_details` 优先于服务端推断：应用侧配了 `langfuse.model-prices.*` 时，
> 即使 Langfuse 未配模型定义也能看到成本（两侧都没配 → 无成本）。

**API**：

```bash
curl -s -u "$PK:$SK" -X POST "$BASE/api/public/models" -H 'Content-Type: application/json' -d '{
  "modelName": "deepseek-v4-pro",
  "matchPattern": "(?i)^(deepseek-v4-pro)$",
  "prices": { "input": 0.27, "output": 1.10 }
}'
```

> 单位是 **USD / 1M token**，`prices` 的键必须与 usage 桶名一致（`input` / `output`）。
> 应用侧若配置了 `langfuse.model-prices.*`，会上报 `cost_details`，**客户端上报值优先于服务端推断**；
> 两侧都没配 → 无成本。价格变更**只对之后的新 generation 生效**。

---

## 6. 备选拓扑：OTel Collector 扇出（生产推荐，与本周实现二选一）

本周实现是**应用内双导出**（8084 同时推 Langfuse 与通用 collector），无需额外组件。
当 Langfuse 与业务链路需要独立治理时，可改为「应用 → Collector → 扇出」：

```yaml
# otel-collector-config.yaml（片段）
receivers:
  otlp:
    protocols:
      http:
        endpoint: 0.0.0.0:4318
processors:
  batch: {}
exporters:
  otlphttp/langfuse:
    endpoint: http://192.168.132.128:3000/api/public/otel
    headers:
      Authorization: "Basic <base64(pk:sk)>"
      x-langfuse-ingestion-version: "4"
  otlphttp/jaeger:
    endpoint: http://<jaeger-host>:4318
service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [batch]
      exporters: [otlphttp/langfuse, otlphttp/jaeger]
```

注意事项：

1. **root span 必须送达** Langfuse，用 `filter` 处理器裁剪时不要把 trace 根 span 过滤掉，否则 trace 建不出来；
2. Langfuse 不支持 gRPC，Collector 侧必须用 `otlphttp`（HTTP/protobuf）；
3. 若不想让通用后端看到 `langfuse.*` 属性，可在 Jaeger 那条 pipeline 上加 `attributes/drop_langfuse`（`delete` 前缀 `langfuse.`）。

---

## 7. 日常运维

| 操作 | 命令 |
|------|------|
| 查看状态 | `docker compose ps` |
| 查看日志 | `docker compose logs -f langfuse-web langfuse-worker` |
| 重启 | `docker compose restart langfuse-web langfuse-worker` |
| 升级 | `docker compose pull && docker compose up -d`（**先备份**，见下） |
| 停止 | `docker compose stop` |
| 下线（保留数据） | `docker compose down` |
| 彻底清理（**删数据**） | `docker compose down -v` |

**升级红线**：`SALT` 与 `ENCRYPTION_KEY` 一旦使用过就**不得修改**，否则已加密的历史数据无法解密；
升级 Langfuse 大版本前先读官方 upgrade guide（v3→v4 只提高基础设施版本水位、架构不变）。

**备份**（三个有状态组件都要）：

```bash
# Postgres 元数据
docker compose exec -T postgres pg_dump -U postgres postgres | gzip > langfuse-pg-$(date +%F).sql.gz
# ClickHouse（明细）：建议用官方 backup 或直接冷备卷
docker run --rm -v langfuse_langfuse_clickhouse_data:/data -v "$PWD":/backup alpine \
  tar czf /backup/langfuse-clickhouse-$(date +%F).tgz -C /data .
# MinIO（事件对象）
docker run --rm -v langfuse_langfuse_minio_data:/data -v "$PWD":/backup alpine \
  tar czf /backup/langfuse-minio-$(date +%F).tgz -C /data .
```

**数据保留**：可通过 `LANGFUSE_INIT_PROJECT_RETENTION`（天数，留空=永久）在初始化时设定项目保留策略；
容量规划（保留期 × 日摄入量）与采样率一起在第 20 周确定。演示环境可先不设保留、观察 ClickHouse 卷增速。

---

## 8. 故障排查

| 现象 | 根因 | 处置 |
|------|------|------|
| 应用侧导出报 `401 Unauthorized` | Basic 认证串错（public/secret 顺序反了、Key 与 `LANGFUSE_INIT_*` 不一致） | 用第 3.4 节第 2 条验证 curl 是否 200；确认 `Authorization: Basic base64(publicKey:secretKey)` |
| UI 里几分钟后才出现 trace | 缺少 `x-langfuse-ingestion-version: 4`（直连 OTel 数据会走延迟路径，最多 10 分钟） | 应用已默认带上该头（`LangfuseOtlpSpanExporter`），用 Collector 扇出时要在 exporter headers 里补 |
| trace 存在但观测树不完整 / 只有 root | 中间层做了 span 过滤，root span 未送达；或采样率 < 1 导致子 span 丢失 | 保证 root span 送达；演示环境采样率保持 1.0（`OTEL_TRACES_SAMPLER_PROBABILITY=1.0`） |
| generation 没有 token / cost | usage 缺失（上游未返回）或未配模型价格 | 查 span 的 `langfuse.observation.usage_details`；按 5.1 配置模型价格 |
| 观测类型不是 generation | 未写 `langfuse.observation.type`，Langfuse 用 model 兜底失败 | 应用已显式写 type；自研埋点务必显式给 |
| `docker compose up` 报 ClickHouse/Postgres 版本不支持 | 版本低于 v4 水位（CH<25.12 / PG<15） | 用本目录 compose 固定版本 |
| Web 容器重启循环，日志提示初始化失败 | `LANGFUSE_INIT_*` 值被加了引号，或只配了项目没配组织 | 去掉引号；按「组织 → 项目/用户」依赖补齐 |
| 8084 启动即报连不上 Langfuse | Langfuse 未启动 / 网络不通 / 端口写错 | 业务不受影响（导出异步降级）；先修网络，导出会自愈 |
| **拉镜像报 `registry-1.docker.io ... connection refused`** | 官方镜像名 `docker.langfuse.com/...` 是非 Hub registry，**绕过** registry mirror 直连被墙的 Hub | 改成 Docker Hub 引用（`docker.io/langfuse/langfuse:4`、`docker.io/langfuse/langfuse-worker:4`，与官方 tag/digest 一致）；本目录 compose 已改。另：受限网络下 `docker manifest inspect` 必失败（它要直连 `auth.docker.io`），**只能用 `docker pull` 判断可用性** |
| **web/worker 日志 `FATAL ERROR: Ineffective mark-compacts near heap limit ... JavaScript heap out of memory`** | `mem_limit` 给得太小（如 700m/500m），V8 堆随之被压到 ~340M/250M，Next.js 服务起不来 | 放宽到 `mem_limit: 1500m/1000m` 并显式 `NODE_OPTIONS=--max-old-space-size=1024/768`（本目录 compose 已含）；实测 web 稳定 ~900 MiB |
| **应用日志 `I/O error on GET ... HTTP/1.1 header parser received no bytes`，Langfuse 上 `prompt_name` 为空** | JDK HttpClient 对明文 HTTP 默认先发起 h2c 升级，真实局域网（VPN / 容器端口转发）下连接被重置 | 已修复（`LangfusePromptClient` 显式 HTTP/1.1）；若自研客户端遇到同样症状，同样强制 HTTP/1.1 |
| **UI 里看不到刚推的 trace** | 目标机时钟与推数据的机器相差较大，默认时间范围过滤掉了 | 放宽 UI 时间范围；用 `date` 对比两端时钟（本次环境差约 8.5 小时） |
| 磁盘增长快 | ClickHouse + MinIO 存全量明细 | 配保留期、降低采样率、清理 `docker system df` 显示的悬空卷 |

排查常用命令：

```bash
docker compose logs --tail=200 langfuse-web
docker compose logs --tail=200 langfuse-worker
# v4 的事件明细（旧 traces/observations 表在 events_only 模式下为空，属正常）
docker compose exec clickhouse clickhouse-client --user clickhouse --password "$CLICKHOUSE_PASSWORD" -q "select count() from events_full"
docker compose exec clickhouse clickhouse-client --user clickhouse --password "$CLICKHOUSE_PASSWORD" -q "select type, count() from events_full group by type"
docker compose exec postgres psql -U postgres -c "select count(*) from users;"
docker stats --no-stream --format 'table {{.Name}}\t{{.MemUsage}}\t{{.CPUPerc}}'
```

---

## 9. 安全基线

- **只对外暴露 3000**（必要时再加 9090）。compose 中 ClickHouse 8123/9000、Redis 6379、Postgres 15432、
  MinIO 控制台 9091 一律绑 `127.0.0.1`，**不要改绑 0.0.0.0**；如必须跨机访问，用 SSH 隧道或内网安全组。
- 所有口令 + `NEXTAUTH_SECRET` / `ENCRYPTION_KEY` / `SALT` 只放 `.env`（不入库，仓库只留 `.env.example` 占位符）。
- 8084 侧 `LANGFUSE_SECRET_KEY` 只从环境变量读，**任何接口都不回显**（`langfuse-status` 只回掩码 public key）。
- 默认 `LANGFUSE_CAPTURE_CONTENT=false`：Langfuse 里不出现 Prompt 正文与患者信息；
  开启后正文经 PII 脱敏与截断，但**仍属敏感数据**，需按院内数据分级管理该主机。
- 定期轮换 `LANGFUSE_INIT_*` 生成的 API Key（UI → Project Settings → API Keys），轮换后同步更新 8084 环境变量。
- Langfuse 自带用户体系与 8084 的 RBAC **不互通**：Langfuse 账号只给运维/研发，不开放给业务用户。

---

## 10. 验收清单

| # | 检查项 | 命令 / 位置 | 通过标准 |
|---|--------|-------------|----------|
| 1 | 六服务健康 | `docker compose ps` | 全部 Up，依赖服务 healthy |
| 2 | Web 健康 | `curl .../api/public/health` | 200 |
| 3 | Key 可用 | `curl -u "$PK:$SK" .../api/public/projects` | 200 |
| 4 | 应用自检 | `GET /observability/langfuse-status` | `enabled=true`、`otlpEndpoint` 正确、密钥为掩码 |
| 5 | 业务不受影响 | 跑 1 次 Agent Run | HTTP 200 `COMPLETED` |
| 6 | trace 入库 | `GET /api/public/traces/<otelTraceId>` | traceId 与响应头 `traceparent` 第 2 段一致 |
| 7 | 观测树 | `GET /api/public/observations?traceId=…` | 含 `agent.run` + generation×N + tool×N |
| 8 | token | 同上 generation 的 `usage` | input/output/total 与响应体 `usage` 一致 |
| 9 | 成本 | UI Trace 详情 | 有 cost（已配模型价格） |
| 10 | 维度 | UI Trace 列表 | 可按 user=1 / session=P001 过滤，tags 正确 |
| 11 | Prompt 管理 | `LANGFUSE_PROMPT_ENABLED=true` 后跑 Run | generation 上有 `prompt.name` / `prompt.version`；Langfuse 停机时回落本地且业务正常 |
| 12 | 内容开关 | `LANGFUSE_CAPTURE_CONTENT=true` 后跑 Run | UI 可见 Prompt/输出，手机号已掩码；置回 false 后不再上报 |
| 13 | 降级 | `docker compose stop` 后跑 Run | 业务仍 200；应用无自身错误日志（仅 SDK 导出错误） |

第 6–13 项的真实输出需粘贴进 `notes/impl-logs/week-18.md` 第 10 节。

---

## 11. 卸载与回滚

```bash
# 停止并保留数据（可随时 start 回来）
docker compose down
# 仅回滚应用侧接入：8084 设 LANGFUSE_ENABLED=false 即回到 Week 17 行为（无需回滚 Langfuse）
# 彻底删除（数据不可恢复，先确认备份）
docker compose down -v
docker volume ls | grep langfuse    # 应无残留
```
