# Spec: Week 18 — Langfuse（LLM Trace / Prompt 管理 / Token 与成本）

## Objective

在 Week 17 的 OpenTelemetry 管线之上接入**自部署 Langfuse v4**，让「一次 Agent 执行」在 Langfuse 里表现为可检索的
**trace → agent / generation / tool 观测树**：带上真实 Token 用量、按模型价格表算出的成本、用户/会话等过滤维度，
并让 classpath Prompt 可被 Langfuse 的 **Prompt 管理**托管（拉取 + 版本关联 + 不可用时回退本地）。

**红线不变**：Week 16 的 `X-Trace-Id` 回显契约、Week 17 的 traceId 单源与既有 span 属性隐私口径
（span 属性不写 Prompt 正文）都不回退；正文只走「默认关闭、显式开启」的独立通道。

## Tech Stack

| 项 | 本周启用 |
|----|----------|
| JDK 17、Spring Boot 3.4.5、Maven、PostgreSQL/H2、Flyway、Sa-Token、Spring AI Alibaba、ai-core | 复用，契约不改 |
| Langfuse **v4**（自托管 docker compose：web/worker + Postgres + ClickHouse + Redis + MinIO） | 本周新增（部署物 + 文档） |
| `io.opentelemetry:opentelemetry-exporter-otlp`（已有）自建 `OtlpHttpSpanExporter` | 复用依赖，新增装配 |
| `io.opentelemetry:opentelemetry-sdk-trace` `SpanProcessor` SPI（由 Boot 收集） | 本周新增用法 |
| JDK 自带 `com.sun.net.httpserver.HttpServer` | 本周新增测试夹具（断言 OTLP 请求头，不引 Testcontainers/WireMock） |
| Spring `RestClient`（已有） | 本周新增用法（Prompt 拉取） |

明确不做：**REST Ingestion API（`POST /api/public/ingestion`）**——Langfuse v4 已对该端点的
`trace-create`/`span-create`/`generation-create` 返回 400，旧同步端点 404，走它等于把自己锁在仅安全补丁期的 v3；
SkyWalking（第 19 周）、Prometheus/Grafana 服务端（第 20 周）；Langfuse score/evaluation 回写（第 20–21 周按需）。

## Commands

- 不改 `JAVA_HOME`；JDK17：`-Djdk.17.home=E:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot`
- Maven：`-s D:\soft\maven-3.9.4\conf\settings-ailocal.xml`（本地仓库 `E:\workRepositoryAi`，`.mvn/maven.config` 已配）
- 根聚合测试：`.\run-maven-jdk17.ps1 test`
- 单模块测试：`.\run-maven-jdk17.ps1 -pl apps/spring-ai-alibaba-agent -am test`
- 启动（8084，模块目录下）：`.\run-maven-jdk17.ps1 spring-boot:run`，或 `java -jar target/spring-ai-alibaba-agent-0.1.0-SNAPSHOT.jar`（配合模块 `.env`）
- 部署 Langfuse：见 `docs/deploy/week-18-langfuse.md`（`deploy/langfuse/docker-compose.yml`，`docker compose up -d`，Web `http://<langfuse-host>:3000`）
- 应用侧关键环境变量：`LANGFUSE_ENABLED`、`LANGFUSE_HOST`、`LANGFUSE_PUBLIC_KEY`、`LANGFUSE_SECRET_KEY`、`LANGFUSE_CAPTURE_CONTENT`
- Postman：导入 `docs/postman/week-18.postman_collection.json`

## 增量架构

见 `docs/architecture/week-18-architecture.md`（只画本周新增 / 改动节点）。

## 一、接入通道：OTLP 双导出（不复用既有 4318 单端点）

**事实约束（已核实，见 ADR）**：Langfuse 摄取端点是 `http://<langfuse-host>:3000/api/public/otel/v1/traces`，
需 HTTP Basic（public key 作用户名、secret key 作密码）与 `x-langfuse-ingestion-version: 4` 头；
只支持 OTLP/HTTP（protobuf 或 JSON），**不支持 gRPC**；v4 上 REST ingestion 已废弃。

**Spring Boot 3.4.5 的真实行为（读 autoconfigure 源码确认）**：`OtlpTracingConfigurations.Exporters`
的退避条件是 `@ConditionalOnMissingBean({OtlpGrpcSpanExporter.class, OtlpHttpSpanExporter.class})`，
而 `SpanExporters` 收集的是**容器里全部 `SpanExporter` bean**（`ObjectProvider<SpanExporter>`）。
因此：

- 直接注册一个 `OtlpHttpSpanExporter` bean（Langfuse 用）会**连带干掉**既有的 collector 导出（Boot 整块退避）；
- 本周做法：把 Langfuse 导出器包成**自定义类型** `LangfuseOtlpSpanExporter implements SpanExporter`（内部持有
  `OtlpHttpSpanExporter`），容器里不存在 `OtlpHttpSpanExporter` 类型的 bean → Boot 的 collector 导出器照旧装配，
  `SpanExporters` 同时收集两者 → **一次埋点、两路导出**。

| 开关 | 语义 |
|------|------|
| `management.otlp.tracing.export.enabled`（Week 17 已有） | 是否导出到通用 collector（Jaeger/Tempo） |
| `langfuse.enabled`（本周新增，默认 **false**） | 是否导出到 Langfuse + 是否做 Langfuse 属性富化 |

两个开关正交：`langfuse.enabled=true` 且 collector 关闭时，只有 Langfuse 收到 span；两个都关时零网络导出（单测口径）。

## 二、Trace 维度传播：`LangfuseContext` + `SpanProcessor`

Langfuse v4 的过滤/聚合越来越多地作用在**单个 observation** 上，官方要求 `userId` / `sessionId` / `traceName` /
`release` / `tags` 出现在 trace 内**每个** span 上，而不只是 root span；v4 还取消了 trace 级 input/output，改由
root observation 承载。

- `LangfuseContext`（infrastructure，单例 + ThreadLocal）：`begin(TraceDimensions)` / `current()` / `clear()`，
  同时把 `traceId` 镜像进 MDC 便于日志排查；`TraceDimensions` 为领域 record（`userId` / `sessionId` / `traceName` / `tags` / `release`）。
- `LangfuseSpanEnricher implements SpanProcessor`：`onStart` 时把 `LangfuseContext` 当前值与
  `langfuse.environment` / `langfuse.release` 写成 span 属性。**仅当 `langfuse.enabled=true` 时注册**（注册进
  Boot 的 `SpanProcessors` 收集点），关闭时零开销。
- **维度映射（业务语义）**：`langfuse.user.id` = 平台用户 ID；`langfuse.session.id` = Run 入参里的 `patientId`
  （同一患者的多次分析归为一个会话，比 executionId 更有检索价值；缺失则不下发）；`langfuse.trace.name` =
  `agent:<agentKey>`；`langfuse.trace.tags` = `[agentType, agentKey]`。
- **诚实的边界**：入口 HTTP server span 由框架观测建立、早于 `userId` 可知，故拿不到用户维度；
  但 trace 级字段可从同一 trace 的任一 span 解析，`agent.run` 已有该维度，不影响 trace 检索与按用户过滤 observation。

## 三、Observation 语义映射（本周核心映射表）

| 我们的 span | Langfuse 观测 | 本周新增属性（`langfuse.*`） |
|-------------|---------------|------------------------------|
| `http post /api/v1/platform/agents/{agentKey}/runs`（框架） | span（root） | 仅由富化器补 trace 维度 |
| `agent.run` | `langfuse.observation.type=agent` | trace 维度由富化器补；不写正文（见下） |
| `llm.chat` | `langfuse.observation.type=generation` | `.model.name` / `.model.parameters` / `.usage_details` / `.cost_details` / `.prompt.name` / `.prompt.version` / `.input` / `.output` |
| `tool.call <name>` | `langfuse.observation.type=tool` | `.metadata.toolCallId` / `.input`（参数）/ `.output`（结果），后两者仅内容采集开启时 |

- **正文只落 generation / tool**：`agent.run` 只承载观测类型与 trace 维度。原因：写它的输入输出需要给
  `PlatformExecutionUseCase` 增依赖并改动既有用例构造器与 5 个测试，而 Langfuse 上真正需要看正文的地方是
  「每一次模型调用看到了什么、回了什么」。这是有意的范围裁剪（YAGNI），不是遗漏。

- **必须显式写 `langfuse.observation.type`**：Langfuse 只在没有识别到显式 type 时才用 model 属性兜底推断 generation；
  显式值永远胜出（`llm.chat` 同时有 model 与 type，必须靠显式 type 落到 generation）。
- **JSON 属性是「一个字符串」**：`usage_details` / `cost_details` / `model.parameters` / `input` / `output` 都是
  **JSON 序列化后的单个 string 属性**，不是扁平键（Langfuse 对非 string 会打 WARN 并丢弃）。
- **prompt 关联只能挂在 generation 上**：非 generation 观测上的 `prompt.name`/`version` 会被 Langfuse 置空。
- 时间戳：OTel SDK 会同时发 `startTimeUnixNano` / `endTimeUnixNano`，满足 Langfuse「两端都要有」的要求。

## 四、Token 与成本

- `usage_details` 取**真实** `ChatResult.usage()`，键为互斥桶
  `{"input":<prompt>,"output":<completion>,"total":<total>}`；上游未返回 usage（全 0）时**不下发**该属性，避免污染成本均值（与 Week 17 同口径）。
- 成本两条路径，**同时保留**：
  1. **客户端上报**（本周实现）：`langfuse.model-prices.<model>.input-per-1m` / `.output-per-1m`（USD/百万 token）
     配置价格表，`LlmCostCalculator` 用真实 usage 算出 `langfuse.observation.cost_details`
     `{"input":x,"output":y,"total":z}`（保留 6 位小数）。客户端上报值在 Langfuse 侧**优先于**服务端推断。
  2. **服务端推断**：未配置价格的模型，Langfuse 用其模型定义（`match_pattern` 正则）推断；
     因此部署文档要求为实际模型（如 `deepseek-v4-pro`）在 Project Settings → Models 配价格，否则 cost 不显示。
- Prometheus 侧新增 `llm.cost.usd`（Counter，标签 `gen_ai.request.model`），让成本也能在 Grafana 告警（第 20 周接入），
  不必打开 Langfuse 才能发现成本异常。
- 成本查询 API（可脱离 Langfuse 使用）：`GET /api/v1/platform/observability/llm-cost-summary`（管理员），
  按模型聚合执行记录里的 token 与估算成本 —— 价目表在配置里，故该端点不需要 Langfuse 在线。

## 五、内容采集（Prompt / 输出正文）策略

- `langfuse.capture-content` 默认 **false**：默认只上报模型名、token、成本、耗时、ID（与 Week 17 span 属性口径一致）。
- 置 true 时，正文只写入 **Langfuse 专用属性**（`langfuse.observation.input` / `.output`），不改变既有 `gen_ai.*` 属性，
  且必须：
  1. 经 `GuardrailPort.sanitizeTextOutput` **PII 脱敏**（复用 Week 14 的手机号 / 身份证 / 邮箱掩码，不重复实现）；
  2. 按 `langfuse.max-content-chars`（默认 2000）**截断**并标注 `…(truncated)`，控制单批 3.5 MB 上限风险；
  3. 序列化为 JSON 字符串（`[{"role":"system","content":"..."}]` / `{"content":...,"toolCalls":[...]}`）。
- 开启是一次**有意识的运维决定**（自部署 Langfuse 在信任边界内），文档需写明；测试断言「关闭时属性不存在」与「开启时已脱敏」。

## 六、Prompt 管理（拉取 + 版本关联 + 回退）

- `LangfusePromptAdapter implements PromptTemplatePort`（`@Primary` 装饰既有 `classpathPromptTemplateAdapter`）：
  - `render(name, vars)`：优先取 Langfuse `GET /api/public/v2/prompts/{name}?label=<label>`（默认 `production`），
    用返回的 `prompt` 文本 + `version` 构造 `PromptTemplate`，变量仍由本地模板引擎渲染；
  - **客户端缓存**（`langfuse.prompt.cache-ttl-seconds`，默认 60，与官方 SDK 默认一致）：TTL 内零网络请求；
  - **回退**：`langfuse.prompt.enabled=false` / 404 / 网络错误 / 5xx → 回落 classpath 模板并记 WARN，
    计数 `langfuse.prompt.fetch.count{outcome=hit|miss|fallback}`；**绝不因 Prompt 服务不可用导致业务 5xx**。
  - `list()` 仍走本地（Langfuse 列表接口不承担渲染职责）。
- **版本关联**：`LangfusePromptTracker`（ThreadLocal）记录本次请求刚加载的
  `(name, version, content)`；`ObservableChatModelAdapter` 在发起的 chat 的 system 消息与其中某条 `content` **完全相等**时，
  把 `langfuse.observation.prompt.name` / `langfuse.observation.prompt.version` 挂到该 `llm.chat`（generation）上。
  内容不等或无记录 → 不挂，不猜。

## 七、部署（交付物，见部署文档）

`deploy/langfuse/`：`docker-compose.yml`（Langfuse v4 六服务）、`.env.example`（密钥占位 + **headless 初始化**，
让 `LANGFUSE_INIT_PROJECT_PUBLIC_KEY` / `SECRET_KEY` 在启动时把项目与 API Key 建好，验收无需人工点 UI）；
`docs/deploy/week-18-langfuse.md` 覆盖：版本水位（ClickHouse ≥25.12 / Postgres ≥15 / Redis ≥7）、
端口与防火墙（仅 3000 对外，其余绑 127.0.0.1）、连通性自检、模型价格配置（UI 与 `POST /api/public/models`）、
数据保留与备份卷、常见故障（401 / 属性延迟 10 分钟 / root span 必须送达 / 磁盘）、升级与回滚、验收步骤。

## 八、开关、降级与单测隔离

```yaml
langfuse:
  enabled: ${LANGFUSE_ENABLED:false}                 # 总开关，默认关（本地/单测零网络）
  host: ${LANGFUSE_HOST:http://localhost:3000}       # 不写具体环境主机名作默认值以外的承诺
  public-key: ${LANGFUSE_PUBLIC_KEY:}
  secret-key: ${LANGFUSE_SECRET_KEY:}
  environment: ${LANGFUSE_ENVIRONMENT:local}
  release: ${LANGFUSE_RELEASE:}
  capture-content: ${LANGFUSE_CAPTURE_CONTENT:false}
  max-content-chars: ${LANGFUSE_MAX_CONTENT_CHARS:2000}
  timeout-ms: 10000
  prompt:
    enabled: ${LANGFUSE_PROMPT_ENABLED:false}
    label: ${LANGFUSE_PROMPT_LABEL:production}
    cache-ttl-seconds: 60
  model-prices: {}
```

- 未开启时：不注册 `LangfuseOtlpSpanExporter`、不注册富化 `SpanProcessor`、不装饰 Prompt 端口、端口方法为空操作 → 既有 300+ 用例行为与耗时不变。
- `application-test.yml` 显式 `langfuse.enabled=false`（与 `management.otlp.tracing.export.enabled=false` 并列）。
- Langfuse 不可达时：导出失败由 OTel SDK 的批量导出线程记录，**业务请求不受影响**（与 Week 17 collector 同口径，实测复验）；
  Prompt 拉取失败走本地回退。
- 密钥只走环境变量 / 模块 `.env`（`.env` 不入库）；自检接口**不回显** secret key。

## Boundaries

- **Always**：TDD（先 RED）；中文 JavaDoc；Week 16/17 契约零回退；`langfuse.*` 属性不写密钥；默认不采集正文；
  密钥只从环境变量读；Langfuse 不可达不影响业务；成本取真实 usage；规范 6.0 交付包齐全
- **Ask first**：改动 `libs/ai-core` 的 Port 契约；在 8083/8082 同步接入 Langfuse；把 Langfuse 下沉到 `libs`；
  用 Langfuse Cloud（数据出公网）
- **Never**：本周引 SkyWalking / Grafana / Loki；走 v4 已废弃的 REST ingestion；
  把 `io.opentelemetry.*` / Langfuse 类型放进 `domain` / `controller`；把正文写进 `gen_ai.*` 通用 span 属性或审计表；
  把 `X-Trace-Id` 改成 Langfuse traceId 破坏 Week 16 回显；为「可能用到」增加 score/dataset 回写

## Success Criteria

> 全部条目已于批次 1 完成并留下实测证据（见 `notes/impl-logs/week-18.md` 第 9–10 节：
> 真实 PostgreSQL 16.15 + 真实 LLM + 真实自部署 Langfuse v4.50.0 的运行输出）。

- [x] `langfuse.enabled=false`（默认）时：既有 381 个用例全绿且无网络导出；新增用例断言「零 Langfuse bean / 零属性」
- [x] `LangfuseOtlpSpanExporter` 用真实 `HttpServer` 夹具断言：路径 `/api/public/otel/v1/traces`、
      `Authorization: Basic base64(pk:sk)`、`x-langfuse-ingestion-version: 4`、请求体为 protobuf OTLP
      （真实验收复验：接收端实测到 `content-type: application/x-protobuf` + `content-encoding: gzip`）
- [x] 双导出成立：`langfuse.enabled=true` 时容器内同时存在 collector 的 `OtlpHttpSpanExporter` 与 `LangfuseOtlpSpanExporter`，
      且 `SpanExporters` 收集到 2 个（用 `ApplicationContextRunner` 断言，不引真 collector）
- [x] `LangfuseSpanEnricher` 断言：开启后每个 span 带 `langfuse.trace.name` / `.user.id` / `.session.id` / `langfuse.environment` / `langfuse.release`，
      `langfuse.trace.tags` 为字符串数组；`LangfuseContext.clear()` 后新 span 不再带
      （真实事件库复验：业务 span 与框架自动埋点 span 都带上了维度）
- [x] `llm.chat` span 断言：`langfuse.observation.type=generation`、`.model.name`、
      `.usage_details` = `{"input":..,"output":..,"total":..}` JSON 字符串、`.cost_details` 按配置价目表算出、
      `.model.parameters` 为 JSON；usage 全 0 时不下发 usage/cost
      （真实事件库复验：`usage_details={'input':261,'output':2048,'total':2309}`，成本两侧口径一致）
- [x] `tool.call` / `agent.run` 分别映射为 `tool` / `agent` 观测类型（真实事件库 type 列为 TOOL / AGENT）
- [x] 内容采集关闭时 `langfuse.observation.input` / `.output` **不存在**；开启时存在、手机号/身份证/邮箱已掩码、超长已截断（`LangfuseContentMaskingTest`）
      （真实对照：capture=true 的 trace 正文长度 729/710、1233/1308；capture=false 的 trace 全为 0）
- [x] `LlmCostCalculator`：已配置模型的成本精确到 6 位小数，未配置模型返回空（不下发 cost）
- [x] `llm.cost.usd` 指标出现在 `SimpleMeterRegistry` / `/actuator/prometheus`（实测 `llm_cost_usd_total` 数值逐位吻合）
- [x] `LangfusePromptAdapter` 断言：命中缓存只发 1 次请求；404 与网络异常均回退 classpath 且不抛；
      命中时 `PromptTemplate.version` 为 Langfuse 版本号；generation 上出现 `langfuse.observation.prompt.name` / `.version`
      （真实复验：`medical-report@1` / `medical-followup@1`；修复前为空，见实现日志 10.2 ⑥-3）
- [x] `GET /api/v1/platform/observability/langfuse-status` 返回 enabled/host/captureContent/promptEnabled/当前 trace 维度，
      且**不含** secret key（未登录 401）
- [x] `GET /api/v1/platform/observability/llm-cost-summary`（管理员）按模型返回 token 与成本聚合；非管理员 403
- [x] `/observability/trace-context` 增加 `langfuseTraceUrl`（启用且配了 project id 时给出 Langfuse UI 深链），既有字段不变
- [x] 根聚合 `test` 全绿，Week 8–17 用例不回退（**450** 全绿；8084 模块 251 → 320，Langfuse 新增 63）
- [x] **真实端到端**：192.168.132.128 上 `docker compose up -d` 起 Langfuse **v4.50.0**（headless 初始化建好项目与 key）→
      8084 以 `LANGFUSE_ENABLED=true` 运行 → 跑真实医疗助手 Agent（真实 LLM）→
      在 Langfuse 事件库查到该 trace，含 `agent.run`(AGENT) / generation×2（真实 token + cost + prompt 版本）/ tool×2，
      traceId 与响应头 `traceparent` 一致；Langfuse 停机时业务仍 200（详见实现日志 10.2）

## 与计划的偏差（预登记）

| 计划 | 实际做法 | 原因 |
|------|----------|------|
| README「部署 Langfuse / PostgreSQL / ClickHouse」 | 部署 **v4**（6 服务），并交付 headless 初始化 | 新部署上 v3 已进入仅安全补丁期（2027-01 止），v3→v4 基础设施组件不变但版本水位提高 |
| 「接入 Prompt 记录 / Token / Agent 执行链」 | 走 OTLP 属性映射，不写 REST ingestion 适配器 | v4 已废弃 ingestion 的 trace/span/generation 事件（400），OTLP 是官方唯一路径 |
| 计划中的「OTLP collector 扇出」 | 应用内双导出（自定义 `SpanExporter` 包装 `OtlpHttpSpanExporter`） | 本环境没有常驻 collector；包装类型可绕开 Boot 的 `OtlpHttpSpanExporter` 退避，同样实现扇出且少一个组件 |
| 成本分析 | 客户端价目表上报 + 成本查询 API + `llm.cost.usd` 指标 | 未配置价格时 Langfuse 不显示成本；价目表在配置里可离线验收 |
| 复盘方式：v4 旧 `traces`/`observations` 读接口 | 原计划用它们做验收断言 | 改为 **UI 深链 + ClickHouse `events_*` + Metrics v2** | v4 运行在 `events_only` 模式，旧接口直接返回「not available」，一度被误判为「数据没到」；改为直接查 v4 事件库后一次看清 span 树与属性 |
| 复盘方式：Langfuse 模型单价口径 | 原按「USD / 1M」填写 | 改为**每 token**（0.27 USD/1M → `2.7e-7`） | 实测发现填 `0.27` 会被服务端推断放大 1e6 倍（265 token 显示 1472.95 USD），与内置价格表口径不符 |
| Prompt 拉取的 HTTP 版本 | 依赖 JDK HttpClient 默认（会尝试 h2c 升级） | **显式 HTTP/1.1** | 明文 HTTP + 跨机路径（VPN/端口转发）下 h2c 升级导致 `header parser received no bytes`，Prompt 永远回退本地；已补单测锁死「不得带 upgrade 头」 |

## Open Questions

- Langfuse 服务端推断成本与客户端上报成本并存的展示口径（本周以上报优先为准）→ 第 20 周做成本看板时复核。
- `langfuse.session.id` 取 `patientId` 是否是最佳会话语义（当前无 chat_session 概念）→ 第 21 周 SaaS 整合时统一。
- 是否把 `observability` + Langfuse 下沉 `libs` 供 8082/8083 复用 → 第 21 周决定（本周只落 8084）。
- 生产采样率与 Langfuse 数据保留策略（本周部署默认全采样 + 不限保留）→ 第 20 周容量规划时定。

## ADR

| 决策 | 选项 | 选择 | 理由 |
|------|------|------|------|
| 接入协议 | REST ingestion / OTLP | **OTLP** | v4 上 ingestion 的 trace/span/generation 事件返回 400，旧同步端点 404；OTLP 是官方指定路径，且已有 Week 17 管线可复用 |
| Langfuse 版本 | v3 / v4 | **v4** | 新部署无历史包袱；v3 仅安全补丁到 2027-01；两者基础设施组件相同 |
| 双导出落地位置 | 改 OTLP 端点单发 Langfuse / 部署 collector 扇出 / 应用内包装导出器 | **应用内包装 `SpanExporter`** | 读 Boot 3.4.5 源码确认退避条件只看 `OtlpHttpSpanExporter`/`OtlpGrpcSpanExporter` 类型；包装后 collector 导出照旧，且不引入常驻 collector；`otlphttp/langfuse` 扇出写入部署文档作为生产备选 |
| trace 维度传播 | 只写 root span / 每个 span 都写（SpanProcessor） | **SpanProcessor 全 span 富化** | v4 的过滤聚合作用在 observation 上，官方明确要求传播到每个 span；否则无法按 user/session 过滤 |
| 正文采集 | 跟随 span 属性一起写 / 独立开关 + 脱敏 + 截断 | **独立开关（默认关）+ `GuardrailPort` 脱敏 + 截断** | 观测系统不该默认成为第二份患者数据副本；复用 Week 14 脱敏避免重复实现 |
| 成本来源 | 只靠服务端推断 / 客户端价目表上报 | **客户端价目表上报（可缺省）+ 服务端推断兜底** | 未配模型定义时 Langfuse 不计算成本；价目表放配置使成本与查询接口都能离线验收 |
| Prompt 管理落点 | 改 ai-core 端口契约 / 在 app 装饰 `PromptTemplatePort` | **app 内 `@Primary` 装饰器** | 规范 5.8.2 要求 ai-core 契约稳定；装饰器零契约改动即可插入 Langfuse，且可回退 |
| generation 与 prompt 版本关联 | 猜测最近一次加载 / system 消息内容全等匹配 | **内容全等匹配（`LangfusePromptTracker`）** | 一个请求里可能加载多个 prompt（报告 + 随访）；内容全等是确定性判据，不匹配就不挂，不产生错误归因 |
| OTLP 请求头实现 | 依赖 Spring 属性 / 自建 exporter | **自建 `OtlpHttpSpanExporter` 包装** | `management.otlp.tracing.*` 只支持单端点 + 全局 headers，无法对两个后端分别带认证头 |
