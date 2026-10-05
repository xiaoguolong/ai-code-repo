# API: Week 18 — Langfuse 接入接口

> 本周**不新增业务接口**：既有登录 / Agent Run / 审计 / Eval / 执行记录接口路径与响应结构零改动。
> 新增 2 个可观测接口、1 个既有接口的**向后兼容字段**，并对 Langfuse 侧 API 的调用与查询给出约定。

---

## 1. 变更总览

| # | 接口 | 类型 | 说明 |
|---|------|------|------|
| 1 | `GET /api/v1/platform/observability/langfuse-status` | 新增 | Langfuse 接入自检（登录用户），**不回显密钥** |
| 2 | `GET /api/v1/platform/observability/llm-cost-summary` | 新增 | 按模型聚合 Token 与成本（**管理员**），不依赖 Langfuse 在线 |
| 3 | `GET /api/v1/platform/observability/trace-context` | 字段新增 | 增加 `langfuseTraceUrl`，既有字段不变 |
| 4 | 所有既有接口 | 无变更 | 请求/响应契约不变；span 属性新增 `langfuse.*`（不在 HTTP 契约内） |
| 5 | Langfuse 侧 `POST /api/public/otel/v1/traces` | 出站调用 | 本周应用作为 OTLP 客户端推送 span（带 Basic 认证 + `x-langfuse-ingestion-version: 4`） |
| 6 | Langfuse 侧 `GET /api/public/v2/prompts/{name}` | 出站调用 | Prompt 管理拉取（`langfuse.prompt.enabled=true` 时），失败回退本地模板 |

统一信封（规范 5.6）：成功 `code=SUCCESS`、失败为 `ApiErrorCode` 枚举名、`data` 失败恒为 `null`、`traceId` 必带。

---

## 2. `GET /api/v1/platform/observability/langfuse-status`

查看当前实例的 Langfuse 接入状态与**本次请求**被写入的 trace 维度。用于「配置是否生效」「维度是否写对」的自检。

- 权限：登录用户即可（与 `trace-context` 同级）。
- 密钥：`publicKey` 只回显**掩码**（前 6 位 + `***`），`secretKey` **永不返回**。

### 2.1 成功响应

```http
GET /api/v1/platform/observability/langfuse-status HTTP/1.1
X-Trace-Id: week18-demo
Cookie: satoken=<token>
```

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "enabled": true,
    "host": "http://192.168.132.128:3000",
    "otlpEndpoint": "http://192.168.132.128:3000/api/public/otel/v1/traces",
    "projectId": "ai-code-repo",
    "environment": "local",
    "release": "week18",
    "captureContent": false,
    "maxContentChars": 2000,
    "publicKeyMasked": "pk-lf-***",
    "promptManagementEnabled": true,
    "promptLabel": "production",
    "promptCacheTtlSeconds": 60,
    "trace": {
      "traceId": "week18-demo",
      "otelTraceId": "0f2a…",
      "langfuseTraceName": "agent:medical-assistant",
      "langfuseUserId": "1",
      "langfuseSessionId": "P001",
      "langfuseTraceUrl": "http://192.168.132.128:3000/project/ai-code-repo/traces/0f2a…"
    },
    "configuredModels": ["deepseek-v4-pro"]
  },
  "traceId": "0f2a…"
}
```

字段说明：

| 字段 | 含义 |
|------|------|
| `enabled` | `langfuse.enabled`；false 时其余导出相关字段仍返回（便于确认「关着」而不是「没配」） |
| `otlpEndpoint` | 实际导出端点（`host` + `/api/public/otel/v1/traces`） |
| `captureContent` | 是否采集 Prompt/输出正文（默认 false；true 时正文经脱敏与截断后才上报） |
| `publicKeyMasked` | 掩码后的 public key；**secretKey 不出现** |
| `promptManagementEnabled` | 是否从 Langfuse 拉取 Prompt（false 时用 classpath 模板） |
| `configuredModels` | 配置了单价的模型列表（决定 `cost_details` 是否下发） |
| `trace.*` | 本次请求的链路与 Langfuse 维度；未开启时 `langfuse*` 字段为 `null` |

### 2.2 失败

| 场景 | HTTP | `code` |
|------|------|--------|
| 未登录 | 401 | `UNAUTHORIZED` |

---

## 3. `GET /api/v1/platform/observability/llm-cost-summary`

按模型聚合**已落库的执行记录**里的 Token 与估算成本。数据源是平台执行记录（Week 16 落库），
因此该接口在 Langfuse 停机时依然可用——成本核算不依赖外部可观测系统。

- 权限：**管理员**（`PlatformPermissionChecker.isAdmin`）；非管理员 403 `FORBIDDEN`。
- 成本：按 `langfuse.model-prices` 价目表计算；未配价的模型 `estimatedCostUsd` 为 `null`（**不用 0 冒充未知**）。

### 3.1 成功响应

```http
GET /api/v1/platform/observability/llm-cost-summary HTTP/1.1
Cookie: satoken=<admin-token>
```

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "generatedAt": "2026-10-04T10:15:30Z",
    "executionCount": 12,
    "models": [
      {
        "model": "deepseek-v4-pro",
        "executionCount": 12,
        "promptTokens": 8400,
        "completionTokens": 41200,
        "totalTokens": 49600,
        "inputPricePerMillion": 0.27,
        "outputPricePerMillion": 1.10,
        "estimatedCostUsd": 0.047604
      },
      {
        "model": "unknown",
        "executionCount": 1,
        "promptTokens": 0,
        "completionTokens": 0,
        "totalTokens": 0,
        "inputPricePerMillion": null,
        "outputPricePerMillion": null,
        "estimatedCostUsd": null
      }
    ],
    "totalEstimatedCostUsd": 0.047604
  },
  "traceId": "…"
}
```

| 字段 | 含义 |
|------|------|
| `executionCount` | 参与聚合的执行记录条数（顶层为全部，模型内为该模型的条数） |
| `inputPricePerMillion` / `outputPricePerMillion` | USD / 百万 token，来自配置；未配置为 `null` |
| `estimatedCostUsd` | `prompt/1e6*inputPrice + completion/1e6*outputPrice`，保留 6 位小数；未配价为 `null` |
| `totalEstimatedCostUsd` | 已配价模型的成本之和（不含 `null` 行；全为 `null` 时返回 `null`） |

### 3.2 失败

| 场景 | HTTP | `code` |
|------|------|--------|
| 未登录 | 401 | `UNAUTHORIZED` |
| 非管理员 | 403 | `FORBIDDEN` |

---

## 4. `GET /api/v1/platform/observability/trace-context`（改动）

响应新增 1 个**可选**字段，既有 6 个字段（`traceId` / `otelTraceId` / `spanId` / `traceparent` / `sampled` /
`observabilityEnabled`）名称、语义、类型全部不变（Week 17 契约与契约测试不回退）。

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "traceId": "week18-demo",
    "otelTraceId": "0f2a…",
    "spanId": "9f1c…",
    "traceparent": "00-0f2a…-9f1c…-01",
    "sampled": true,
    "observabilityEnabled": true,
    "langfuseTraceUrl": "http://192.168.132.128:3000/project/ai-code-repo/traces/0f2a…"
  },
  "traceId": "0f2a…"
}
```

| 新字段 | 说明 |
|--------|------|
| `langfuseTraceUrl` | Langfuse UI 深链，需 `langfuse.enabled=true` **且**配置了 `langfuse.project-id`；否则为 `null`（不构造可能 404 的链接） |

> 为什么用 OTel traceId 而不是 `X-Trace-Id` 做 Langfuse 的 traceId：Week 17 已确立「oteltraceId = MD5(X-Trace-Id)」
> 的单源派生关系，Langfuse 与通用链路后端共用同一 traceId，三处（响应体 / 日志 / 审计）可互查；
> 客户端原始 `X-Trace-Id` 作为 `langfuse.trace.metadata.clientTraceId` 保留，便于对账。

---

## 5. 应用 → Langfuse 的出站调用

### 5.1 OTLP 导出（span 推送）

```http
POST /api/public/otel/v1/traces HTTP/1.1
Host: <langfuse-host>:3000
Authorization: Basic <base64(publicKey:secretKey)>
x-langfuse-ingestion-version: 4
Content-Type: application/x-protobuf
```

| 项 | 约定 |
|----|------|
| 端点 | `${langfuse.host}/api/public/otel/v1/traces`（**端口 3000**，与通用 collector 的 4318 不同，两路并存） |
| 认证 | HTTP Basic：public key 作用户名、secret key 作密码（在应用内 base64，密钥只从环境变量读） |
| 版本头 | `x-langfuse-ingestion-version: 4` —— **不加会有最多 10 分钟摄取延迟** |
| 协议 | OTLP/HTTP + protobuf（Langfuse 不支持 gRPC） |
| 期望响应 | 200（已接受并落对象存储） |
| 失败行为 | 批量异步导出，**业务请求不受影响**；由 OTel SDK 自身 logger 记录错误（与 Week 17 collector 同口径） |

### 5.2 span 属性映射（Langfuse 侧可见字段的**唯一**来源）

| 我们的 span | 写入的属性 | 取值格式 |
|-------------|-----------|----------|
| 全部 span（富化器） | `langfuse.trace.name` | string，`agent:<agentKey>` |
| | `langfuse.user.id` | string，平台用户 ID |
| | `langfuse.session.id` | string，Run 入参 `patientId`（缺失不写） |
| | `langfuse.trace.tags` | **string[]**：`[agentType, agentKey]` |
| | `langfuse.environment` / `langfuse.release` | string，来自配置 |
| 入口 server span | （仅 trace 维度，同上） | — |
| `agent.run` | `langfuse.observation.type` | `agent`（不写正文，见下） |
| `llm.chat` | `langfuse.observation.type` | `generation`（显式，绝不依赖 model 兜底推断） |
| | `langfuse.observation.model.name` | string，真实生效模型 |
| | `langfuse.observation.model.parameters` | **JSON 字符串**：`{"temperature":0.7,"max_tokens":2048}` |
| | `langfuse.observation.usage_details` | **JSON 字符串**：`{"input":265,"output":1731,"total":1996}` |
| | `langfuse.observation.cost_details` | **JSON 字符串**：`{"input":0.000072,"output":0.001904,"total":0.001976}` |
| | `langfuse.observation.prompt.name` / `.version` | string / **string 化的整数**（Langfuse 解析为 prompt 版本） |
| | `langfuse.observation.input` / `.output` | **JSON 字符串**，仅 `capture-content=true` |
| `tool.call <name>` | `langfuse.observation.type` | `tool` |
| | `langfuse.observation.metadata.toolCallId` | string，工具调用 ID |
| | `langfuse.observation.input` / `.output` | **JSON 字符串**，仅 `capture-content=true` |
| 失败 span | `langfuse.observation.level` | `ERROR` |

> **正文范围**：正文只写 `llm.chat`（generation）与 `tool.call`（tool）两类观测。
> `agent.run` 只写观测类型 —— 写它的输入输出需要改执行用例构造器与既有测试，
> 而 Langfuse 上真正需要正文的位置是「每次模型调用看到了什么、回了什么」（YAGNI 裁剪）。
>
> **格式红线**：`usage_details` / `cost_details` / `model.parameters` / `input` / `output` 都是
> **一个 JSON 序列化后的字符串属性**，不是扁平键（Langfuse 对非 string 值会告警并丢弃）。
> 单测逐个断言这五个属性的字符串形态，避免"看起来对、Langfuse 里为空"。

### 5.3 `GET /api/public/v2/prompts/{name}`（Prompt 管理）

```http
GET /api/public/v2/prompts/medical-report?label=production HTTP/1.1
Authorization: Basic <base64(publicKey:secretKey)>
```

| 项 | 约定 |
|----|------|
| 何时调用 | 仅 `langfuse.prompt.enabled=true`；且**每次模板 TTL 过期后的首次加载** |
| 缓存 | 客户端缓存 `langfuse.prompt.cache-ttl-seconds`（默认 60s），TTL 内零网络请求 |
| 命中 | 用响应 `prompt` 文本 + `version` 构造 `PromptTemplate`，变量仍本地渲染；generation 上挂 `prompt.name`+`prompt.version` |
| 404 | 记 WARN + `langfuse.prompt.fetch.count{outcome=miss}`，**回落 classpath 模板** |
| 网络错误 / 5xx | 记 WARN + `{outcome=fallback}`，回落 classpath 模板；**不抛异常、不影响业务** |
| 超时 | `langfuse.timeout-ms`（默认 10s），与导出共用 |

> 安全：Prompt 拉取只读、只带 Basic 认证；**响应正文不写日志**（可能含业务提示词），只记 name / version / 状态。

---

## 6. 环境变量与配置（应用侧）

| 变量 | 默认 | 说明 |
|------|------|------|
| `LANGFUSE_ENABLED` | `false` | 总开关；false 时零导出、零富化、Prompt 走本地 |
| `LANGFUSE_HOST` | `http://localhost:3000` | Langfuse Web 根地址（不要带 `/api/...`） |
| `LANGFUSE_PUBLIC_KEY` / `LANGFUSE_SECRET_KEY` | 空 | 项目 API Key；**只从环境变量/`.env` 读，不入库** |
| `LANGFUSE_PROJECT_ID` | 空 | 用于拼 UI 深链；不配则 `langfuseTraceUrl` 为 `null` |
| `LANGFUSE_ENVIRONMENT` / `LANGFUSE_RELEASE` | `local` / 空 | 写入 `langfuse.environment` / `langfuse.release` |
| `LANGFUSE_CAPTURE_CONTENT` | `false` | 是否上报 Prompt/输出正文（开启后强制脱敏 + 截断） |
| `LANGFUSE_MAX_CONTENT_CHARS` | `2000` | 单条正文最大字符数 |
| `LANGFUSE_PROMPT_ENABLED` | `false` | 是否从 Langfuse 拉取 Prompt |
| `LANGFUSE_PROMPT_LABEL` | `production` | Prompt 标签 |
| `LANGFUSE_PROMPT_CACHE_TTL_SECONDS` | `60` | 客户端缓存 TTL |
| `LANGFUSE_TIMEOUT_MS` | `10000` | 导出与 Prompt 拉取超时 |

> **正文采集的联锁**：`LANGFUSE_CAPTURE_CONTENT=true` 但 `guardrail.enabled=false` 时，采集**自动失效**并打 WARN
> —— 不允许未脱敏的患者数据出站。此时 `langfuse-status` 的 `captureContent` 返回 `false`（如实反映有效值）。

模型价目表**出厂即配**（`application.yml`，单位 USD / 百万 token），数值可用环境变量覆盖，换环境不必改文件：

```yaml
langfuse:
  model-prices:
    deepseek-v4-pro:
      input-per-1m: ${LANGFUSE_PRICE_DEEPSEEK_V4_PRO_INPUT:0.27}
      output-per-1m: ${LANGFUSE_PRICE_DEEPSEEK_V4_PRO_OUTPUT:1.10}
    deepseek-chat:
      input-per-1m: ${LANGFUSE_PRICE_DEEPSEEK_CHAT_INPUT:0.27}
      output-per-1m: ${LANGFUSE_PRICE_DEEPSEEK_CHAT_OUTPUT:1.10}
```

也可用启动参数临时覆盖（**必须放在 `-jar xxx.jar` 之后**，作为 Boot 应用参数）：

```powershell
java -jar target\spring-ai-alibaba-agent-0.1.0-SNAPSHOT.jar `
  "--langfuse.model-prices.deepseek-v4-pro.input-per-1m=0.40"
```

> 单价全为 0 的行视为未配置（`ModelPriceCatalog` 构造时剔除），未配价的模型：应用侧**不下发** `cost_details`，
> Langfuse 侧按其模型定义推断；两侧都没配 → Langfuse 不显示成本。
>
> 若要让 **Langfuse 服务端**也推断成本，需在 Project Settings → Models（或 `POST /api/public/models`）配模型定义；
> 注意 Langfuse 存的是**每 token** 价：0.27 USD/1M 要填 `2.7e-7`，填成 `0.27` 会放大 1e6 倍（实测踩过，见部署文档 5.1）。

---

## 7. 度量口径（`/actuator/prometheus` 新增）

| 指标 | 类型 | 标签 | 说明 |
|------|------|------|------|
| `llm_cost_usd_total` | Counter | `gen_ai_request_model` | 按模型累计估算成本（USD），用于成本告警（第 20 周 Grafana） |
| `langfuse_prompt_fetch_count_total` | Counter | `outcome`=`hit`\|`miss`\|`fallback` | Prompt 拉取结果计数；`fallback` 持续增长说明 Langfuse 或网络异常 |

> 命名遵循 Week 17 实测口径：Micrometer 的 Counter 已含 `_total` 后缀，**文档与实现必须一致**（Week 17 曾出现
> 文档写成 `llm_tokens_total_total` 的缺陷）。真实导出名以验收时的 `/actuator/prometheus` 原文为准。

---

## 8. Langfuse 侧验收命令（只读，用于核对）

> ⚠️ **v4 是 `events_only` 模式：旧读接口 `GET /api/public/traces`、`/api/public/observations` 不可用**
> （返回 `This endpoint is not available on deployments running in Langfuse v4 events_only mode`）。
> 这**不代表数据没到** —— 数据在 v4 事件库（ClickHouse `events_core` / `events_full`）。
> 核对走 UI + 事件表 + Metrics v2（本次真实验收即按此口径完成）。

```bash
# 1) 连通性与认证（应返回 200；401 说明 Basic 认证串错）
curl -s -o /dev/null -w '%{http_code}\n' -u "$PK:$SK" "$LANGFUSE_HOST/api/public/projects"

# 2) UI 深链（浏览器打开即可看到 trace；<otelTraceId> 取响应头 traceparent 第 2 段）
echo "$LANGFUSE_HOST/project/ai-code-repo/traces/<otelTraceId>"

# 3) 事件库核对 span 树与 trace 维度（在 Langfuse 主机上执行）
docker compose exec -T clickhouse clickhouse-client --user clickhouse --password "$CH_PASS" -q "
SELECT name, type, trace_name, user_id, session_id, tags, environment, release
FROM events_full WHERE trace_id = '<otelTraceId>' ORDER BY start_time"

# 4) 观测的用量 / 成本 / Prompt 关联 / 正文字符数
docker compose exec -T clickhouse clickhouse-client --user clickhouse --password "$CH_PASS" -q "
SELECT name, type, provided_model_name, usage_details, cost_details, prompt_name, prompt_version,
       length(input) AS input_len, length(output) AS output_len
FROM events_full WHERE trace_id = '<otelTraceId>' ORDER BY start_time FORMAT Vertical"

# 5) Metrics v2（v4 新指标接口，必须带 query 参数，否则报 Invalid input: expected string）
curl -s -u "$PK:$SK" "$LANGFUSE_HOST/api/public/v2/metrics?query=%7B%22view%22%3A%22observations%22%7D" | head -c 400

# 6) 模型价格是否按「每 token」配置（Langfuse 存的是每 token 价，配成每 1M 会放大 1e6 倍）
curl -s -u "$PK:$SK" "$LANGFUSE_HOST/api/public/models" | grep -o '"deepseek-v4-pro".\{0,160\}'
```

预期（v4.50.0 实测口径）：

```text
trace  name=agent:medical-assistant  userId=1  sessionId=P001  tags=[MEDICAL_ASSISTANT,medical-assistant]
├── span        http post /api/v1/platform/agents/{agentKey}/runs   （root，parent=上游 traceparent 第 3 段）
├── agent       agent.run
├── generation  llm.chat   model=deepseek-v4-pro  usage={input,output,total}  cost={input,output,total}  prompt=medical-report@1
│   └── generation  chat deepseek-v4-pro        （Spring AI 自动埋点）
├── generation  llm.chat   model=deepseek-v4-pro  prompt=medical-followup@1
└── tool        tool.call  PatientLookupTool / HealthMetricTool
```

> 入口 server span 的 `trace_name` / `user_id` / `session_id` 为空属**预期**：它在用户与会话可知之前就已开始
> （见 Spec「诚实的边界」）；按 user/session 过滤时用业务 span 即可。
