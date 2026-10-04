# Spec: Week 17 — OpenTelemetry（Trace / Span / Metric / Context 落地）

## Objective

让 8084 平台从「只有自研 HTTP traceId + 日志」升级为**标准化分布式可观测**：接入 OpenTelemetry（经 Micrometer Tracing 桥接）产出 W3C 语义的 Trace/Span，覆盖 **Spring Boot 入站请求 → Agent 执行 → LLM 调用 → Tool 调用** 四段链路，并输出 **调用与 Token 指标**（Prometheus 可抓取）；同时保证 Week 16 的 `X-Trace-Id`（MDC / 响应头 / 审计落库）契约**零破坏**，OTel traceId 与既有 traceId 同源可互查。

## Tech Stack

| 项 | 本周启用 |
|----|----------|
| JDK 17、Spring Boot 3.4.5、Maven | 复用 |
| `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp` | 本周新增（Micrometer 抽象 → OTel SDK 落地） |
| `spring-boot-starter-actuator` + `micrometer-registry-prometheus` | 本周新增（指标出口） |
| PostgreSQL / H2、Flyway、Sa-Token、Spring AI Alibaba、ai-core | 复用，不改契约 |
| `micrometer-tracing-test`（`SimpleTracer`） | 本周新增（test scope，断言 span 树） |

明确不做（规范 3.4：P5 可观测 17–20，本周只做 OTel）：**Langfuse（第 18 周）、SkyWalking（第 19 周）、Grafana 面板与 Prometheus 服务端部署（第 20 周）**、分布式日志采集（Loki/ELK）。

## Commands

- 不改 `JAVA_HOME`；JDK17：`-Djdk.17.home=E:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot`
- Maven：`-s <maven-home>\conf\settings-ailocal.xml`，本地仓库由该 settings 指定（仓库内 `.mvn/maven.config` 已配）
- 根聚合测试：`.\run-maven-jdk17.ps1 test`
- 单模块测试：`.\run-maven-jdk17.ps1 -pl apps/spring-ai-alibaba-agent -am test`
- 启动（8084）：`apps/spring-ai-alibaba-agent` 下 `.\run-maven-jdk17.ps1 spring-boot:run`
- 指标：`GET http://localhost:8084/actuator/prometheus`
- 健康：`GET http://localhost:8084/actuator/health`
- OTLP 端点（默认）：`http://localhost:4318/v1/traces`（HTTP/protobuf），可用 `management.otlp.tracing.endpoint` 覆盖
- Postman：导入 `docs/postman/week-17.postman_collection.json`

## 增量架构

见 `docs/architecture/week-17-architecture.md`（只画本周新增 / 改动节点）。

## 一、Context：OTel traceId 与既有 `X-Trace-Id` 单源对齐

**问题**：Week 16 已有自研 traceId（`TraceIdFilter` → MDC → 响应头 → 审计落库），且契约测试要求
「客户端传入 `X-Trace-Id: client-trace-123` 必须原样回显」。OTel 的 traceId 是 **32 位小写 hex**，
两者格式不同。若各生成各的，就会出现「日志一个 ID、链路一个 ID、审计第三个 ID」，可观测性反而变差。

**决策（单源）**：OTel traceId 由既有 traceId **确定性派生**，两者可互相换算、永远指向同一次请求。

| 输入 | MDC `traceId`（对外契约，不变） | OTel traceId（32 hex） |
|------|-------------------------------|------------------------|
| `traceparent` 合法（W3C 上游传入） | 其 trace-id 原文 | 其 trace-id 原文（真远程父） |
| `X-Trace-Id: client-trace-123` | `client-trace-123`（原样回显） | `client-trace-123` 的 MD5（32 hex） |
| 两者都没有 | 新生成 16 hex（W3C 长度） | 与 MDC 相同 |

- 派生用 MD5 而非「按位填充」：MD5 输出天然 32 hex，且对任意长度 traceId 稳定、无填充歧义。
- 响应头同时回写 `X-Trace-Id`（兼容既有客户端）与 `traceparent`（OTel 标准，供下游/网关续接）。
- **反向可查**：`GET /api/v1/platform/observability/trace-context` 返回当前请求的
  `traceId` / `otelTraceId` / `spanId` / `traceparent`，让「日志里的 ID」与「Jaeger 里的 ID」能对上。

## 二、Port：观测能力抽象（不泄漏 OTel 类型到领域层）

| 类型 | 位置 | 职责 |
|------|------|------|
| `SpanScope` | app `observability/domain` | 一次 span 的生命周期句柄；`attribute(k,v)` / `recordError(t)` / `close()`，继承 `AutoCloseable` 以支持 try-with-resources |
| `AgentObservabilityPort` | app `observability/domain` | `openSpan(kind, name, attrs)` + `recordTokenUsage(...)` + `isEnabled()`；**领域/应用层只依赖这个接口，不出现任何 `io.opentelemetry.*` / `io.micrometer.*` 类型** |
| `SpanKind` / `ObservabilityAttributes` | app `observability/domain` | span 种类（`SERVER`/`AGENT_RUN`/`LLM_CALL`/`TOOL_CALL`）与属性键名常量（禁止散落魔法字符串） |

实现（`observability/infrastructure`）：

| 类型 | 说明 |
|------|------|
| `MicrometerObservabilityAdapter` | 唯一实现：`io.micrometer.tracing.Tracer` + `MeterRegistry`；span 开始即把 traceId 写回 MDC（保证链路内所有既有日志仍带同一 traceId），关闭时恢复并清理 |
| `NoopObservabilityAdapter` | `observability.enabled=false` 时的空实现；保证单测与无 collector 环境不产生额外开销 |
| `TraceContext` / `TraceIds` | traceId ↔ OTel traceId 派生与格式化（纯函数，可单测） |

装配：`ObservabilityConfiguration` 按 `observability.enabled` 二选一注册（互斥）。

## 三、Trace：四类 span

| span 名 | kind | 埋点位置 | 关键属性 |
|---------|------|----------|----------|
| `http get /path` / `http post /path`（Spring Boot HTTP 观测按 `management.observations.http.server.requests.name` 生成） | SERVER | 框架自动埋点（`ServerHttpObservationFilter`）→ **本周注入 `traceparent` 使其采纳我们的 traceId** | `http.request.method` / `url.path` / `http.response.status_code` / `http.trace_id`（原 `X-Trace-Id`，便于对账） |
| `agent.run` | AGENT_RUN | `PlatformExecutionUseCase.runAgent` | `agent.key` / `agent.type` / `user.id` / `execution.id` / `execution.status` / `gen_ai.request.model` / `gen_ai.usage.total_tokens` / `duration.ms` |
| `llm.chat` | LLM_CALL | `ObservableChatModelAdapter`（装饰 `ChatModelPort`） | `gen_ai.system` / `gen_ai.request.model` / `gen_ai.usage.prompt_tokens` / `gen_ai.usage.completion_tokens` / `gen_ai.usage.total_tokens` / `gen_ai.response.finish_reason` / `gen_ai.tool.call.count` |
| `tool.call` | TOOL_CALL | `ObservableToolPort`（装饰 `ToolPort` 最外层） | `tool.name` / `tool.call.id` / `tool.result.chars` / `duration.ms` |

备用：`HttpServerSpanFilter` 在 **框架观测未建立当前 span** 时补一个 `http.server` 的 SERVER span
（例如裁剪掉 Actuator 依赖的部署）；框架已建 span 时它不重复建 span —— 避免业务入口出现两个兄弟 server span
（实测踩过，见实现日志）。

- **隐私红线**：span 属性**禁止**写入 Prompt 正文、模型输出正文、患者身份、密码、token、API Key；
  只写标识、模型名、长度、token 数与状态（与 Week 16 审计同一口径）。
- 失败一律 `recordError` + 正常抛出，span 状态置 `ERROR`；`finally` 关闭 scope 防止泄漏。
- 参数/结果长度只记**字符数**，不记内容。
- **trace 根节点与父边**：有活动 span（框架观测）时走正常父子；
  无活动 span 但有上游 `traceparent` 时用其真实 span-id 建远程父；两者都没有则**不设父**
  （不编造 spanId，避免「全 0 父被 SDK 忽略」与「随机父产生孤儿节点」两种实测过的坏情况）。

## 四、Metric：调用与成本指标

| 指标名 | 类型 | 标签 | 采集点 |
|--------|------|------|--------|
| `agent.run.count` | Counter | `agent.key` / `execution.status` | `PlatformExecutionUseCase` |
| `agent.run.duration` | Timer | `agent.key` / `execution.status` | `PlatformExecutionUseCase` |
| `llm.call.count` | Counter | `gen_ai.request.model` / `llm.outcome` | `ObservableChatModelAdapter` |
| `llm.call.duration` | Timer | `gen_ai.request.model` / `llm.outcome` | `ObservableChatModelAdapter` |
| `llm.tokens.total` | Counter | `gen_ai.request.model` / `token.type`=`prompt\|completion` | `ObservableChatModelAdapter` |
| `tool.call.count` | Counter | `tool.name` / `tool.outcome` | `ObservableToolPort` |

- 出口：`/actuator/prometheus`。命名注意：`llm.tokens.total` → **`llm_tokens_total`**（不是 `llm_tokens_total_total`），
  `agent.run.count` → `agent_run_count_total`，真实导出名以实测为准（见 `docs/api/week-17-api.md` 第 4 节）。
- `agent.type` 只写进 span 属性，不参与指标标签（避免冗余维度）。
- Token 指标来自真实 `ChatResult.usage()`，不估算；上游未返回 usage 时 `TokenUsage.unknown()` 全 0，此时跳过不记录
  （记 0 会稀释成本均值）。
- 未做 `guardrail.blocked.count`：`PlatformGuardrailService` 目前只暴露两个方法且无返回值语义，
  为它新增依赖会改动 5 个已有测试的构造器；本周观测目标已由 agent / LLM / tool / token 指标覆盖，按 YAGNI 推迟。

## 五、Spring Boot 接入与导出

```yaml
management:
  tracing:
    enabled: true
    sampling:
      probability: ${OTEL_TRACES_SAMPLER_PROBABILITY:1.0}   # 演示环境全采样；生产建议 0.1
  otlp:
    tracing:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318/v1/traces}
      export:
        enabled: ${OTEL_EXPORT_ENABLED:true}
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: never

observability:
  enabled: ${OBSERVABILITY_ENABLED:true}
```

- **优雅降级**：collector 未启动时 OTLP 导出失败**不得影响业务请求**（Batching + 异步导出 + 超时）。
  实测：23 个业务请求全部 200、应用自身零错误日志、启动阶段零报错。
  注意导出失败由 **OTel SDK 自己的 logger 打 ERROR**（含堆栈，`ERROR []` 无 traceId，因导出跑在线程池上），
  项目不应去覆盖第三方日志级别；本地没 collector 时建议直接 `OTEL_EXPORT_ENABLED=false`（实测零报错且埋点/日志/指标不受影响）。
- **单测隔离**：`application-test.yml` 设 `management.otlp.tracing.export.enabled: false` + profile `test` 时
  `observability.enabled=false`（既有 300+ 用例不因新增埋点改变行为与耗时）。
- **HTTP 语义**：`traceparent` 用 W3C 格式；上游传入合法 `traceparent` 时采纳其 trace-id 作为真远程父。

## Boundaries

- **Always**：TDD（先 RED）；中文 JavaDoc；`X-Trace-Id` 既有契约不破坏；span 属性不含正文/隐私；
  无密钥入库；指标取真实 usage；OTLP 不可达不影响主流程
- **Ask first**：改动 `libs/ai-core` 的 Port 契约；引入 Langfuse / SkyWalking；改动 Graph / Workflow / Multi-Agent 编排；
  给 enterprise（8083）/ patient（8082）同步接入 OTel
- **Never**：本周引 Langfuse / SkyWalking / Grafana / Loki；把 OTel `Span` 类型暴露进 `domain` 或 `controller`；
  把 traceId 改成「OTel 32 hex」而破坏 Week 16 回显契约；在 span 里记 Prompt 正文或患者标识；
  用 OTel 注解/Agent 探针（javaagent）——只用代码内埋点

## Success Criteria

- [x] `TraceIds.otelTraceId(...)` 稳定产出 32 位 hex，且同输入恒等（`TraceIdsTest`：11 例）
- [x] 合法 `traceparent` 请求头被采纳：traceId = 其 trace-id 原文，响应 `traceparent` 沿用同一 trace-id（真实验收第 4 组）
- [x] 无任何输入时生成 32 位 hex traceId，响应同时带 `X-Trace-Id` 与合法 `traceparent`（`X-Trace-Id` 回显客户端值）
- [x] Week 16 契约不回退：`X-Trace-Id` 原样回显；`ApiStandardsContractTest` 等既有用例全绿
- [x] `GET /api/v1/platform/observability/trace-context` 返回 `traceId`/`otelTraceId`/`spanId`/`traceparent`/`sampled`/`observabilityEnabled`，
      且 `otelTraceId` = `traceparent` 第 2 段、`spanId` = 第 3 段（`ObservabilityHttpContractTest` + 真实验收第 3 组）
- [x] 真实 OTel SDK 断言 `agent.run` span 下存在 `llm.chat` 子 span（同 traceId、`parentSpanId` = 父 `spanId`）
- [x] 真实 OTel SDK 断言 `tool.call` span，属性含 `tool.name`，且属性中**不含**工具参数/结果正文
- [x] `llm.chat` span 属性含真实 `gen_ai.usage.prompt_tokens` / `completion_tokens` / `total_tokens`（真实 LLM：265 / 1731 / 1996）
- [x] `SimpleMeterRegistry` 断言 `agent.run.count` / `agent.run.duration` / `llm.call.count` / `llm.tokens.total` / `tool.call.count` 均被记录，标签正确
- [x] `observability.enabled=false` 时装配空实现：零 span、零指标、不写 `traceparent`、业务功能不变
- [x] `/actuator/prometheus` 可访问且包含 `agent_run_count_total` / `llm_call_count_total` / `tool_call_count_total` / `jvm_*` / `http_server_requests_*`
- [x] 根聚合 `test` 全绿，Week 8–16 用例不回退（316 → 381，8084 模块 186 → 251）
- [x] **真实 OTLP collector 收到 span**：本地起最小 OTLP/HTTP collector → 启动 8084（真实 PostgreSQL）→
      跑一次真实 LLM 医疗助手 Agent，collector 落盘 span 树
      `http post /agents/{agentKey}/runs → agent.run → llm.chat → tool.call` 且 traceId 与响应头 `traceparent` 一致
      （并观测到 Spring AI 自动产生的 `chat deepseek-v4-pro` / `http post` 子 span）

## 与计划的偏差（已实现，故按实现校正）

| 计划 | 实际 | 原因 |
|------|------|------|
| server span 名 `http.server` | 真实服务器上为 Spring Boot 观测名 `http get /path` / `http post /path` | 框架自动观测先建立 server span；本周改为「注入 traceparent 让框架采纳我们的 traceId」而非另建 span，避免业务入口两个兄弟 server span |
| `guardrail.blocked.count` 指标 | 未做 | 需给 `PlatformGuardrailService` 新增依赖并改 5 个已有测试的构造器；观测目标已被其他指标覆盖（YAGNI） |
| 单测断言用 `SimpleTracer` | 改用**真实 OTel SDK + 内存 exporter** | `SimpleTracer` 的 span context 不保证生成合法 traceId/spanId，无法验证「与既有 traceId 同源」这一核心契约（实测踩过，见实现日志） |

## Open Questions

- 生产采样率取值（当前默认 1.0 便于演示）→ 第 20 周接 Prometheus/Grafana 与容量规划时定。
- `X-Trace-Id` 与 W3C `traceparent` 双头的长期取舍 → 第 19 周 Gateway 接入时评估；
  已确认下游应优先用 `traceparent`（服务端日志/审计/响应体 `traceId` 三者与它一致）。
- `management.observations.http.server.requests.name` 是否统一改成 `http.server` 以便与备用 span 同名 → 第 20 周定。
- 是否把 `observability` 下沉到 `libs`（供 8082/8083 复用）→ 第 21 周 SaaS 整合时决定；本周只落 8084，避免过度设计。

## ADR

| 决策 | 选项 | 选择 | 理由 |
|------|------|------|------|
| OTel 落地方式 | javaagent 自动探针 / 代码内 Micrometer Tracing 桥接 | 代码内 `micrometer-tracing-bridge-otel` | javaagent 无法给「Agent 执行」这类业务语义打 span；桥接方案可用 `Tracer` 手工埋点，且 Spring Boot 已自动装配 HTTP/JDBC span，零额外进程参数 |
| traceId 关系 | OTel 自成一套 / 由既有 traceId 派生 | 派生（MD5 → 32 hex） | 两套 ID 会让日志、审计、链路三方对不上，违背可观测初衷；派生后 `X-Trace-Id` 契约不破坏（Week 16 契约测试仍绿） |
| 观测抽象层级 | 直接用 `io.micrometer.tracing.Tracer` / 自定义 Port | 自定义 `AgentObservabilityPort` + 适配器 | 规范 3.2 禁止框架类型泄漏到领域层；自定义 Port 让单测可用 `Noop` 且未来换 SkyWalking/自研不影响调用方 |
| 指标出口 | 自研 JSON 端点 / Micrometer + Actuator | Micrometer + `/actuator/prometheus` | Micrometer 已是 Spring Boot 事实标准，Prometheus 文本格式零成本对接第 20 周体系；自研格式无法被采集器消费 |
| 采样率 | 固定 1.0 / 可配 + 默认 1.0 | `management.tracing.sampling.probability` 可配 | 演示与验收需要看到全部 span；生产改环境变量即可，无需改代码 |
| collector 不可达 | 启动失败 / 打印日志降级 | **不失败、只打印日志**（SDK 打 ERROR + 堆栈） | 可观测设施故障不应成为业务单点故障（与 Week 16 审计同口径）；实测 23/23 业务请求 200 |
| 埋点位置 | 改 Graph/Workflow 内部 / 在端口装饰器埋点 | 端口装饰器 + UseCase | Graph/Workflow 是 Week 8–11 稳定编排，装饰 `ChatModelPort`/`ToolPort` 可一次覆盖全部 Agent 类型，且不改编排代码（YAGNI） |
| 测试断言手段 | 连真 collector / `SimpleTracer` / 真实 OTel SDK + 内存 exporter | **真实 OTel SDK + `InMemorySpanExporter`** | `SimpleTracer` 的 span context 不保证生成合法 traceId/spanId，无法验证「与既有 traceId 同源」；真 collector 留给验收步骤 |
| 与框架自动 HTTP span 的关系 | 关闭框架观测、自己建 server span / **注入 `traceparent` 让框架采纳我们的 traceId** | 注入 + 复用框架 span | 关闭框架观测会连带丢 `http_server_requests` 指标（且空观察名会导致 NPE）；注入方案让框架、备用过滤器、日志/审计共用一个 traceId，并保留框架指标 |
| 无上游时的父 spanId | 随机 spanId / 全 0 / **不设父** | 不设父（有活动 span 时走正常父子） | 随机 spanId 会画出指向不存在 span 的假边；全 0 父会被 OTel SDK 判定无效并**连带忽略 traceId**（两者均实测） |
| `guardrail.blocked.count` | 本周做 / 推迟 | 推迟 | 需给 `PlatformGuardrailService` 引入观测依赖并改动 5 个既有测试构造器；收益低于本周已覆盖的四类指标（YAGNI） |
