# Week 18 实现日志 — Langfuse（LLM Trace / Prompt 管理 / Token 与成本）

- 日期：2026-10-04
- 批次：1
- 对应 Spec：docs/specs/week-18.md
- 对应架构：docs/architecture/week-18-architecture.md
- 对应接口：docs/api/week-18-api.md
- 对应部署：docs/deploy/week-18-langfuse.md（+ deploy/langfuse/docker-compose.yml、.env.example）
- 对应 Postman：docs/postman/week-18.postman_collection.json

## 1. 本周目标

1. 在 Week 17 的 OTLP 管线上接入**自部署 Langfuse v4**：8084 的 span 经 OTLP 推到 Langfuse，
   形成 `trace → agent / generation / tool` 观测树。
2. 把 trace 维度（用户 / 会话 / trace 名 / 标签 / 环境 / 发布版本）传播到 trace 内**每个** span。
3. 按 Langfuse 官方属性映射写 `langfuse.*`：观测类型、模型与参数、**真实 usage**、按价目表算出的 cost、
   Prompt 版本关联、可选正文（脱敏 + 截断）。
4. 提供「Langfuse 接入自检」与「LLM 成本汇总」两个接口，以及成本/拉取结果两类指标。
5. 交付**可直接执行**的部署物与部署文档（v4 六服务 + headless 初始化，无需人工点 UI 建 Key）。
6. 零回退：Week 16 的 `X-Trace-Id` 回显契约、Week 17 的 traceId 单源与全部既有用例保持绿色。

## 2. 边界

- Always：TDD（先 RED）；中文 JavaDoc；默认不采集正文；密钥只从环境变量读；Langfuse 不可达不影响业务；
  成本取真实 usage；规范 6.0 交付包齐全
- Never：引 SkyWalking / Grafana；走 v4 已废弃的 REST ingestion；把 OTel / Langfuse 类型放进 `domain` / `controller`；
  把正文写进 `gen_ai.*` 通用属性或审计表；改 `libs/ai-core` 契约；破坏 `X-Trace-Id` 回显

## 3. 增量架构

见 `docs/architecture/week-18-architecture.md`（图 2 增量拓扑、图 3 双导出退避条件、图 4 观测树与属性映射、图 5 装配开关）。

## 4. 新增 / 改动类型清单

| 类型 | 名称 | 职责 |
|------|------|------|
| Const | `LangfuseAttributes` | `langfuse.*` 键名与观测类型常量（禁魔法字符串） |
| Model | `TraceDimensions` / `TraceScope` | 请求期 trace 维度 + 作用域句柄（AutoCloseable，可嵌套恢复） |
| Model | `LlmGenerationRecord` / `LlmCost` / `ModelPrice` / `ModelPriceCatalog` / `PromptReference` | 生成事实、成本、价目表、Prompt 引用 |
| Model | `LangfuseStatusView` / `LlmCostSummaryView` | 自检视图、成本汇总视图 |
| Port | `AgentObservabilityPort`（改动） | 新增 `beginTrace(TraceDimensions)` |
| Adapter | `MicrometerObservabilityAdapter`（改动） | `beginTrace` 写入请求期维度；`AGENT_RUN` → `langfuse.observation.type=agent`；保留 2 参构造器兼容既有测试 |
| Adapter | `NoopObservabilityAdapter`（改动） | `beginTrace` 空实现 |
| Config | `LangfuseProperties` | `langfuse.*` 配置：开关、host、密钥、projectId、正文策略、Prompt、价目表；`otlpEndpoint()` / `authorizationHeader()` / `traceUrl()` / `publicKeyMasked()` |
| Infra | `LangfuseContext` | ThreadLocal 维度持有者（`begin` 返回可恢复作用域） |
| Infra | `LangfuseSpanEnricher` | OTel `SpanProcessor`：span 开始时写 trace 维度（含 string[] tags） |
| Infra | `LangfuseOtlpSpanExporter` / `LangfuseOtlpSpanExporterFactory` | 包装 `OtlpHttpSpanExporter`：`/api/public/otel/v1/traces` + Basic + `x-langfuse-ingestion-version: 4` |
| Infra | `LangfuseGenerationAttributes` | 领域事实 → `langfuse.*` 属性（纯函数，五个 JSON 字符串属性在此统一序列化） |
| Infra | `LangfuseContentPolicy` | 正文采集：默认关、Guardrail 脱敏、按字符截断、guardrail 关闭时 fail-safe 到关 |
| Infra | `LlmCostCalculator` | 真实 usage × 价目表（6 位小数）；未配价返回空 |
| Infra | `LangfuseGenerationSupport` | 装饰器与工具类之间的唯一协作点（`applyGeneration` / `applyTool` / 成本指标名） |
| Infra | `LangfusePromptTracker` | 本次请求加载过的 Prompt（内容全等匹配版本，容量 16） |
| Infra | `LangfusePromptClient` / `LangfusePromptFetch` / `LangfusePromptPayload` | Prompt API 客户端：Basic 认证、label 查询、404→miss、异常→fallback、**永不抛** |
| Infra | `LangfusePromptAdapter` | `@Primary` 装饰 `PromptTemplatePort`：Langfuse 优先 + TTL 缓存 + 回退 classpath + 结果指标 |
| Infra | `LangfuseConfiguration` | 常驻能力 + 追踪（`langfuse.enabled`）+ Prompt（`langfuse.prompt.enabled`）三类装配 |
| UseCase | `PlatformLlmCostUseCase` | 按模型聚合执行记录的 token 与成本（管理员） |
| UseCase | `PlatformObservabilityUseCase`（改动） | 新增 `langfuseStatus()` / `langfuseTraceUrl()` |
| Controller | `PlatformObservabilityController`（改动） | `+GET /langfuse-status`、`+GET /llm-cost-summary` |
| DTO | `LangfuseStatusResponse` / `LlmCostSummaryResponse`；`TraceContextResponse`（改动，+`langfuseTraceUrl`） | 协议转换；自检响应**类型上没有** secret key 字段 |
| Decorator | `ObservableChatModelAdapter`（改动） | generation 属性 + 成本指标 `llm.cost.usd`；保留 2 参构造器 |
| Decorator | `ObservableToolPort`（改动） | tool 观测类型 + `toolCallId` + 可选正文；保留 2 参构造器 |
| UseCase | `PlatformExecutionUseCase`（改动） | 开 `agent.run` span 前 `beginTrace(...)`（会话维度取 `patientId`） |
| Test | `LangfuseConfigDefaultsTest`(2：出厂价目表默认值 + 属性覆盖) / `LangfuseGenerationAttributesTest`(4) / `LlmCostCalculatorTest`(5) / `LangfuseContentMaskingTest`(5) / `LangfusePromptTrackerTest`(5) / `LangfuseSpanEnricherTest`(4) / `LangfuseOtlpSpanExporterTest`(4) / `LangfusePromptClientTest`(6) / `LangfusePromptAdapterTest`(6) / `LangfuseChatModelAdapterTest`(7) / `LangfuseToolPortAdapterTest`(3) / `LangfuseWiringContextTest`(5) / `PlatformLlmCostUseCaseTest`(3) / `PlatformObservabilityLangfuseTest`(4) | 共 **63** 个新用例 |
| Test Util | `GuardrailStub` / `StubHttpServer` / `LangfuseTestFixtures` | 脱敏假实现、JDK `HttpServer` 请求桩（断言真实 OTLP/HTTP 头与 Prompt 拉取请求）、属性夹具 |

改动既有文件（非测试）：`AgentObservabilityPort`、`NoopObservabilityAdapter`、`MicrometerObservabilityAdapter`、
`ObservableChatModelAdapter`、`ObservableToolPort`、`ObservabilityConfiguration`、`AppConfiguration`、
`PlatformExecutionUseCase`、`PlatformObservabilityUseCase`、`PlatformObservabilityController`、
`TraceContextResponse`、`application.yml`、`src/test/resources/application-test.yml`、模块 `.env.example`。

## 5. RED

- 先写 13 个测试类 + 3 个测试夹具（无任何生产代码），随后执行
  `mvn -Djdk.17.home=... -pl apps/spring-ai-alibaba-agent -am test-compile`
- 结果：`BUILD FAILURE`（编译期即失败）
- 原因：`com.aicode.framework.observability.domain` / `.infrastructure` / `.application` 下
  `LangfuseAttributes` / `TraceDimensions` / `TraceScope` / `LangfuseProperties` / `LangfuseContext` /
  `LangfuseOtlpSpanExporter` / `LangfuseSpanEnricher` / `LangfuseGenerationAttributes` / `LlmCostCalculator` /
  `LangfuseContentPolicy` / `LangfuseGenerationSupport` / `LangfusePromptTracker` / `LangfusePromptClient` /
  `LangfusePromptAdapter` / `LangfuseConfiguration` / `LangfuseStatusView` / `LlmCostSummaryView` /
  `PlatformLlmCostUseCase` 等待测类型全部不存在（`找不到符号` / `程序包不存在`，60+ 处）。

## 6. GREEN

- 命令：`mvn -Djdk.17.home=... test`（等价 `run-maven-jdk17.ps1 test`）
- 结果：`BUILD SUCCESS`，根聚合 **450** 个用例全绿

| 模块 | Week 17 | Week 18 | 结果 |
|------|---------|---------|------|
| ai-core | 90 | 90 | 全绿 |
| enterprise-knowledge-agent | 21 | 21 | 全绿 |
| patient-agent | 19 | 19 | 全绿 |
| spring-ai-alibaba-agent | 251 | **320** | 全绿 |

8084 模块本周新增 63 个用例。

## 7. 重构

- **做了什么**：
  1. 五个 JSON 字符串属性（`usage_details` / `cost_details` / `model.parameters` / `input` / `output`）的序列化
     统一收口到 `LangfuseGenerationAttributes`，避免装饰器与内容策略各写一遍导致格式漂移；
  2. 「域事实 → Langfuse 观测」的协作只留一个入口 `LangfuseGenerationSupport`，两个装饰器不必各自拼 Map，
     也便于关闭时整体空转；
  3. `LangfuseProperties` 承担全部配置语义（`otlpEndpoint()` / `authorizationHeader()` / `publicKeyMasked()` /
     `traceUrl()`），调用方不再拼字符串；
  4. 两个装饰器与 `MicrometerObservabilityAdapter` 都保留原构造器并委托到新构造器（默认「Langfuse 关闭」），
     因此 Week 17 的既有测试一行未改 —— 这比「改测试去适配新签名」更能证明零回退。
- **没做什么**：没有自研 REST ingestion 客户端（v4 已废弃）；没有引入 `com.langfuse:langfuse-java`
  （官方不建议用于 tracing，而本周只需拉 Prompt 一个 GET）；没有把观测下沉到 `libs`；
  没有为 `agent.run` 增加正文上报（会改执行用例构造器与既有测试，属 YAGNI 裁剪）。

## 8. 质量门禁

- [x] 编译：根聚合 `mvn ... test` 全模块通过
- [x] 单测：根聚合 **450** 用例全绿，Week 8–17 无回退（8084 由 251 → 320）
- [x] JDK：未改 `JAVA_HOME`；构建命令只传 `-Djdk.17.home=...`
- [x] 文档：新增/改动 public 类型均有中文 JavaDoc
- [x] 分层：`domain` / `application` 无 `io.opentelemetry.*` / `io.micrometer.*`；Langfuse 框架类型只在
      `observability.infrastructure`；Controller 只做登录态与 DTO 转换
- [x] 安全：无密钥入库（`.env.example` 只放占位符）；自检响应无 secret key 字段；
      正文默认不采集，采集必须过 Guardrail 脱敏且 guardrail 关闭时自动失效
- [x] YAGNI：未引入 Langfuse Java SDK / SkyWalking / Grafana / Testcontainers / WireMock
- [x] 数据库脚本：本周无新增迁移（可观测数据不进业务库）
- [x] 日志：本文件已填 RED/GREEN 证据

## 9. 验证证据

```text
# 根聚合测试（真实输出摘要）
[INFO] Building ai-core 0.1.0-SNAPSHOT                                    [1/5]
[INFO] Tests run: 90,  Failures: 0, Errors: 0, Skipped: 0
[INFO] Building enterprise-knowledge-agent 0.1.0-SNAPSHOT                 [2/5]
[INFO] Tests run: 21,  Failures: 0, Errors: 0, Skipped: 0
[INFO] Building patient-agent 0.1.0-SNAPSHOT                              [3/5]
[INFO] Tests run: 19,  Failures: 0, Errors: 0, Skipped: 0
[INFO] Building spring-ai-alibaba-agent 0.1.0-SNAPSHOT                    [4/5]
[INFO] Tests run: 320, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

关键验收点（均有对应测试）：

- **双导出成立（本周最关键工程结论）**：`langfuse.enabled=true` 时容器里同时存在 Boot 的
  `OtlpHttpSpanExporter` 与自定义 `LangfuseOtlpSpanExporter`，`SpanExporters.list()` 有 **2** 个导出器
  （`LangfuseWiringContextTest`）。依据：Boot 3.4.5 `OtlpTracingConfigurations.Exporters` 的退避条件是
  `@ConditionalOnMissingBean({OtlpGrpcSpanExporter.class, OtlpHttpSpanExporter.class})`，
  而 `SpanExporters` 收集容器内全部 `SpanExporter`。
- **真实 HTTP 请求形态**：本地 `HttpServer` 桩收到 `POST /api/public/otel/v1/traces`、
  `Authorization: Basic base64(pk:sk)`、`x-langfuse-ingestion-version: 4`、protobuf 正文（`LangfuseOtlpSpanExporterTest`）。
- **缺密钥启动即失败**：`langfuse.enabled=true` 且未配密钥 → 上下文启动失败且错误信息含 `langfuse.public-key`
  （`LangfuseWiringContextTest`），避免「数据悄悄不出现」。
- **trace 维度传播到每个 span**：`langfuse.trace.name` / `.user.id` / `.session.id` / `langfuse.environment` /
  `langfuse.release` 出现在**每个** span 上，`langfuse.trace.tags` 为字符串数组；作用域关闭后不再污染
  （`LangfuseSpanEnricherTest`，真实 OTel SDK + 内存 exporter）。
- **generation 属性**：`langfuse.observation.type=generation`、`.model.name`、`.model.parameters`、
  `.usage_details` = `{"input":1000,"output":2000,"total":3000}`（JSON 字符串）、
  `.cost_details` = `{"input":0.00027,"output":0.0022,"total":0.00247}`，且 `llm.cost.usd` 指标同步累加
  （`LangfuseChatModelAdapterTest`）。
- **未知用量/未配价不编造**：usage 全 0 时不下发 usage 与 cost，也不记成本指标；未配价时只缺 cost
  （`LangfuseGenerationAttributesTest`、`LangfuseChatModelAdapterTest`、`LlmCostCalculatorTest`）。
- **tool / agent 观测类型**：`tool.call` → `tool` + `metadata.toolCallId`；`agent.run` → `agent`
  （`LangfuseToolPortAdapterTest`、`MicrometerObservabilityAdapterTest` 系列）。
- **Prompt 版本关联**：system 消息与已加载 Prompt **内容全等**时才挂 `prompt.name` + `prompt.version`；
  不等则不挂（`LangfuseChatModelAdapterTest`、`LangfusePromptTrackerTest`）。
- **正文策略**：关闭时零正文字段；开启时手机号已掩码为 `138****5678`、超长截断带 `truncated`；
  guardrail 关闭时采集自动失效（`LangfuseContentMaskingTest`）。
- **Prompt 管理**：命中用 Langfuse 文本与版本、TTL 内零网络请求、变量本地渲染、
  404→`miss`、5xx/网络异常/响应格式异常→`fallback`，三种结果均**回退 classpath 且不抛异常**
  （`LangfusePromptClientTest`、`LangfusePromptAdapterTest`）。
- **配置绑定**：`langfuse.model-prices.deepseek-v4-pro.input-per-1m` / `output-per-1m` 正确绑定进价目表
  （`LangfuseWiringContextTest.bindsModelPriceTableFromConfiguration`）。
- **自检接口**：`enabled` / `otlpEndpoint` / `captureContent`（有效值）/ `configuredModels` / trace 维度与
  深链；public key 只回 `pk-lf-***`，**任何位置都不出现 secret key**（`PlatformObservabilityLangfuseTest`）。
- **成本汇总**：按模型聚合真实 token，已配价算成本（1500/3000 token → `0.003705` USD）、未配价返回 `null`，
  非管理员抛 `PlatformAccessDeniedException`（`PlatformLlmCostUseCaseTest`）。
- **零回退**：`ObservabilityDisabledTest` / `ObservabilityHttpContractTest` / `ObservabilityProductionTraceLinkTest` /
  `ObservableChatModelAdapterTest` / `ObservableToolPortTest` 等 Week 17 用例**一行未改**即全绿。

## 10. 真实验收（批次 1）

### 10.1 本地真实验收（真实 PostgreSQL + 真实 LLM + Langfuse 协议兼容接收端）—— 已完成

环境：8084 打包产物（JDK17）连真实 PostgreSQL 16.15（`192.168.132.128:5432`，Flyway 仅校验不迁移），
真实 DeepSeek 模型调用；Langfuse 侧用**协议兼容接收端**（127.0.0.1:3000，临时脚本，验收后删除、不属交付物）：
按 Langfuse 的端点与认证约定接收 OTLP 并解压落盘，用来证明「应用真的按协议发出了什么」。
启动参数：`LANGFUSE_ENABLED=true`、`LANGFUSE_HOST=http://127.0.0.1:3000`、
`LANGFUSE_PROMPT_ENABLED=true`、`LANGFUSE_CAPTURE_CONTENT=true`、`OTEL_EXPORT_ENABLED=false`（无 collector），
`--langfuse.model-prices.deepseek-v4-pro.input-per-1m=0.27 --langfuse.model-prices...output-per-1m=1.10`。

**① 自检接口（真实响应）**

```text
GET /api/v1/platform/observability/langfuse-status
enabled=True  host=http://127.0.0.1:3000  endpoint=http://127.0.0.1:3000/api/public/otel/v1/traces
capture=True  prompt=True  label=production  models=deepseek-v4-pro
publicKeyMasked=pk-lf-***   projectId=ai-code-repo  env=local

GET /api/v1/platform/observability/trace-context
{"traceId":"c532f4d9…d335","otelTraceId":"c532f4d9…d335","spanId":"275d9c37…685c",
 "traceparent":"00-c532f4d9…d335-275d9c37…685c-01","sampled":true,"observabilityEnabled":true,
 "langfuseTraceUrl":"http://127.0.0.1:3000/project/ai-code-repo/traces/c532f4d9…d335"}
```

**② 真实 Agent Run（医疗助手，真实模型）**

| # | 请求（`X-Trace-Id`） | HTTP | 结果 | 真实 usage |
|---|---------------------|------|------|-----------|
| 1 | `week18-real-check`（P001） | 200 | COMPLETED，model=deepseek-v4-pro | 1705 / 7813 / 9518 |
| 2 | `week18-cost-check`（P002） | 200 | COMPLETED，model=deepseek-v4-pro | 648 / 2713 / 3361 |
| 3 | `week18-content-check`（P003） | 200 | COMPLETED，model=deepseek-v4-pro | 631 / 2892 / 3523 |

`MD5("week18-real-check")` 与响应头 `traceparent` 第 2 段一致（`bb07ac48f4a416ff15bb14e5df0b5eb8`），
即 **Langfuse 的 traceId = 日志/审计/响应体的 traceId**，Week 17 的单源派生没有被破坏。

**③ 接收端实测到的出站请求（原文摘录）**

```json
{"method":"POST","path":"/api/public/otel/v1/traces",
 "headers":{"authorization":"Basic cGstbGYtd2VlazE4LWxvY2FsOnNrLWxmLXdlZWsxOC1sb2NhbA==",
            "x-langfuse-ingestion-version":"4",
            "content-type":"application/x-protobuf","content-encoding":"gzip",
            "user-agent":"OTel-OTLP-Exporter-Java/1.43.0"}}
{"method":"GET","path":"/api/public/v2/prompts/medical-report?label=production",
 "headers":{"authorization":"Basic cGstbGYtd2VlazE4LWxvY2FsOnNrLWxmLXdlZWsxOC1sb2NhbA==",
            "user-agent":"Java-http-client/17.0.8"}}
{"method":"GET","path":"/api/public/v2/prompts/medical-followup?label=production", ...}
```

`Basic` 串 base64 解码 = `pk-lf-week18-local:sk-lf-week18-local`（public key 作用户名、secret key 作密码）；
两个 Prompt（报告 + 随访）各拉取一次 —— TTL 缓存生效，不是每次请求都打 Prompt API。

**④ 导出载荷解压后逐个检索（这是「Langfuse 里到底有什么」的直接证据）**

```text
payload files: 4   total decompressed bytes: 11679
otel-2.bin <= langfuse.observation.type / .input   + tool.call（工具观测）
otel-3.bin <= Langfuse 托管 …                      ← 输入里出现 Langfuse 托管 Prompt 的原文
             langfuse.observation.input / .type=generation
             usage_details / cost_details / prompt.name
otel-4.bin <= 同上 + agent.run
```

抽样（`tool.call` 观测的属性原文，protobuf 解压后可直接读）：

```text
langfuse.observation.input    = {"name":"PatientLookupTool","id":"query_patient",
                                 "arguments":{"patientId":"P003"}}
langfuse.observation.metadata.toolCallId = query_patient
langfuse.observation.output   = {"output":{"age":62,"patientId":"P003","name":"…",
                                 "diagnosis":"2 型糖尿病","gender":"male"}}
langfuse.observation.type     = tool
langfuse.session.id           = P003          ← 会话维度 = Run 入参 patientId
langfuse.trace.name           = agent:medical-assistant
```

三条结论：

1. **Prompt 管理端到端成立**：Langfuse 侧下发的 Prompt 文本出现在真实模型调用的 `input` 属性里，
   且同一 span 带 `prompt.name`（版本关联）——「拉取 → 使用 → 上报关联」闭环打通；
2. **观测类型与维度正确**：`agent.run` / `generation` / `tool` 三种类型齐全，
   `usage_details`、`cost_details`、`langfuse.session.id` 都按预期落到导出的 span 上；
3. **隐私红线守住**：本轮业务假数据里没有手机号，掩码断言由单测覆盖
   （`LangfuseContentMaskingTest` / `LangfuseChatModelAdapterTest` 断言 `138****5678` 存在且 `13812345678` 不存在）；
   `capture-content=false` 的三次运行中，正文属性**一个都没出现**（同一接收端可对比验证）。

**⑤ 成本与指标（真实值）**

```text
GET /actuator/prometheus
agent_run_count_total{agent_key="medical-assistant",execution_status="COMPLETED"} 1.0
llm_call_count_total{gen_ai_request_model="deepseek-v4-pro",llm_outcome="SUCCESS"} 2.0
llm_tokens_total{gen_ai_request_model="deepseek-v4-pro",token_type="prompt"} 648.0
llm_tokens_total{gen_ai_request_model="deepseek-v4-pro",token_type="completion"} 2713.0
llm_cost_usd_total{gen_ai_request_model="deepseek-v4-pro"} 0.003159   ← 648/1e6*0.27 + 2713/1e6*1.10，逐位吻合

GET /api/v1/platform/observability/llm-cost-summary
{"executionCount":13,"totalEstimatedCostUsd":0.031201,
 "models":[{"model":"deepseek-v4-pro","executionCount":7,"promptTokens":6432,"completionTokens":26785,
            "totalTokens":33217,"inputPricePerMillion":0.27,"outputPricePerMillion":1.1,
            "estimatedCostUsd":0.031201},
           {"model":"unknown","executionCount":6,"totalTokens":0,
            "inputPricePerMillion":null,"outputPricePerMillion":null,"estimatedCostUsd":null}]}
```

未配价的模型成本是 `null`（不是 0）——「未知」与「零成本」在数据上被区分开，符合设计意图。

### 10.2 真实 Langfuse v4 自部署端到端验收 —— 已完成

目标机：`192.168.132.128`（Ubuntu 24.04，4C/3.8G + 3.8G swap，已有业务 PostgreSQL/Redis 在跑）。
部署物：`deploy/langfuse/`（compose + `.env.example`），实际落地在 `~/langfuse`；headless 初始化自动建好
组织/项目/API Key（未手工点 UI）。**Langfuse 版本 v4.50.0**，六容器 running、四依赖 healthy，
内存占用 2.5 GiB / 3.8 GiB。

```text
{"status":"OK","version":"4.50.0"}
clickhouse|running|Up (healthy)      postgres|running|Up (healthy)
langfuse-web|running|Up              redis|running|Up (healthy)
langfuse-worker|running|Up           minio|running|Up (healthy)
```

**① Langfuse 侧数据面无旧读接口（重要事实，避免误判）**

v4 运行在 `events_only` 模式：`GET /api/public/traces`、`/api/public/observations` 等旧读接口返回
`This endpoint is not available on deployments running in Langfuse v4 events_only mode` —— 这**不是数据缺失**。
数据落在 v4 事件库（ClickHouse `events_core` / `events_full`），验证走 UI + 事件表 + Metrics v2。
本次一开始就是被这个 404 误导过一次，故已写进部署文档与接口文档。

**② 真实 Agent Run（真实 LLM）× 4**（含修复后复跑、正文开关对照、停机降级）

| # | 场景（`X-Trace-Id`） | HTTP | 结果 | 真实 usage |
|---|---------------------|------|------|-----------|
| 1 | `week18-langfuse-acceptance`（P001） | 200 | COMPLETED | 787 / 3042 / 3829 |
| 2 | 修复 HTTP/1.1 后复跑（同一 X-Trace-Id → 同一 traceId） | 200 | COMPLETED | 600 / 3567 / 4167 |
| 3 | `week18-nocontent`（P009，capture=false 对照） | 200 | COMPLETED | 734 / 2386 / 3120 |
| 4 | `week18-langfuse-down`（**Langfuse 全停**时） | **200** | **COMPLETED** | 663 / 2937 / 3600 |

**③ Langfuse 事件库实测**（共 37 条事件：SPAN 16 / GENERATION 12 / TOOL 6 / AGENT 3）

一次医疗助手执行的 span 树（trace `380090d6583d0c26da212603f0bb23d0`，含框架自动埋点共 20 个 span）：

```text
http post /api/v1/platform/agents/{agentKey}/runs   (SPAN, parent=590868fe3edd4bb0 ← 上游 traceparent 第 3 段)
└── agent.run                                       (AGENT, trace_name=agent:medical-assistant, user_id=1, session_id=P001)
    ├── llm.chat                                    (GENERATION, prompt=medical-report@1)
    │   └── chat deepseek-v4-pro                    (GENERATION, Spring AI 自动埋点)
    ├── tool.call × 2                               (TOOL)
    └── llm.chat                                    (GENERATION, prompt=medical-followup@1)
```

trace 维度传播（`events_full` 结构化列）：

```text
name                                  trace_name                user_id  session_id  tags
http post /api/v1/platform/agents/... (空)                      (空)     (空)        []
agent.run                             agent:medical-assistant   1        P001        ['MEDICAL_ASSISTANT','medical-assistant']
tool.call                             agent:medical-assistant   1        P001        同上
llm.chat                              agent:medical-assistant   1        P001        同上
chat deepseek-v4-pro（框架自动埋点）    agent:medical-assistant   1        P001        同上
（所有 span 均带 environment=vm-lab / release=week18）
```

> **业务 span 与框架自动埋点的 span 都带上了维度**（说明 SpanProcessor 方案奏效）；
> 入口 server span 维度为空 —— 它在 `userId`/`patientId` 可知之前就开始，正是 Spec 写明的已知边界。

generation 的用量、成本与 Prompt 关联：

```text
name                  model              usage_details                             cost_details                                          prompt
llm.chat              deepseek-v4-pro    {'input':261,'output':2048,'total':2309}   {'input':0.00007,'output':0.002253,'total':0.002323}  medical-report@1
llm.chat              deepseek-v4-pro    {'input':339,'output':1519,'total':1858}   {'input':0.000092,'output':0.001671,'total':0.001763} medical-followup@1
chat deepseek-v4-pro  deepseek-v4-pro    {'input':261,'output':2048,'total':2309}   {'input':0.00007047,'output':0.0022528,'total':0.00232327}  —
```

核对：261/1e6×0.27 = 0.0000705 ✔、2048/1e6×1.10 = 0.0022528 ✔ —— 客户端 `cost_details` 与
Langfuse 服务端推断（单价单位修正后）**一致**。

**④ 正文开关对照**

| 运行 | `capture-content` | generation/tool 的 input/output 长度 | Prompt 关联 |
|------|-------------------|--------------------------------------|-------------|
| 修复后复跑 | `true` | 729/710、1233/1308（有正文，已脱敏+截断） | medical-report@1 / medical-followup@1 |
| P009 对照 | `false` | **全部为 0**（一个正文字段都不写） | medical-report@1（Prompt 管理与正文开关相互独立） |

**⑤ 停机降级**

Langfuse 六容器全停后跑真实 Agent：**HTTP 200 / COMPLETED**；应用自身零业务异常，只有 OTel SDK 自己的
导出线程打 ERROR（与 Week 17 collector 不可达同形态，未去覆盖第三方日志级别）：

```text
ERROR [] i.o.e.internal.http.HttpExporter - Failed to export spans. The request could not be executed. Full error message: timeout
```

重启 Langfuse：`health OK`（35s 内）、Basic 认证 200，数据继续正常落库。

**⑥ 真实验收暴露并修复的 4 个缺陷（本地单测/桩测都测不出来）**

| # | 现象 | 根因 | 处置 |
|---|------|------|------|
| 1 | web/worker 启动即 `FATAL ERROR: JavaScript heap out of memory` 重启循环 | `mem_limit` 设成 700m/500m，V8 堆上限被压到 ~340M/250M，Next.js 起不来 | 放宽到 1500m/1000m + 显式 `NODE_OPTIONS=--max-old-space-size=1024/768`；实测 web 稳定 ~900 MiB |
| 2 | Spring AI 自动埋点那层 generation 成本显示 **1472.95 USD**（虚高 1e6 倍） | Langfuse 模型定义的 `inputPrice` 是**每 token** 价（内置表 `babbage-002=4e-07` 即 0.4 USD/1M），我按「0.27 USD/1M」填了 0.27 | 改为 `inputPrice=2.7e-7 / outputPrice=1.1e-6`；修正后推断值与客户端上报一致。部署文档补「每 token」换算 |
| 3 | Prompt 一直回退本地模板；日志 `I/O error on GET ... HTTP/1.1 header parser received no bytes`；`prompt_name` 为空 | JDK HttpClient 对**明文 HTTP** 默认先发起 h2c 升级，真实局域网路径（VPN/容器端口转发）下被重置；本地回环桩测测不出来 | `LangfusePromptClient` 显式 `HttpClient.Version.HTTP_1_1`；**并补断言"请求不得带 upgrade 头"的单测**，把现场经验固化成回归防线 |
| 4 | **Week 17 的 `ObservabilityDisabledTest` 一次都没被执行过**（surefire 目录里没有它的报告）；纳入执行后立刻暴露 1 条失败断言 | surefire 默认只匹配 `*Test.java`，且**默认 excludes 含内部类**，于是 `Outer$Inner` 形式的静态嵌套测试类被静默跳过；而该类里的 span 断言又与 Week 17 自己记录的「MockMvc 下 span 不落内存 exporter」结论相矛盾（当年没跑，所以没人发现） | ① `pom.xml` 显式配置 `includes`（`%regex[.*Test(\$.*)?]`）并覆盖 `excludes`；② 把失败断言按真实语义改为「未登录 401 时同样回写 `traceparent`、`X-Trace-Id` 原样回显、响应体 `traceId` 非空」，并注明入口 server span 由 `HttpServerSpanFilterTest`、`ObservabilityProductionTraceLinkTest` 与真机事件库共同覆盖。模块用例 312 → **320**（+8 全部来自此前从未执行过的嵌套类） |

> 第 4 条是本周**最有价值的一条**：它说明「测试全绿」这件事本身也需要被验证 ——
> 一个从不执行的测试类，会长期以「已覆盖」的姿态存在（Week 17 的日志甚至把它算进了覆盖清单）。
> 已把结论写进全局规范 `docs/code-implementation-spec.md` 第 8 节，避免后续周次重复踩坑。

> 一处**不是缺陷但值得记录**：`agent.run` 出现 `usage_details={'total':3829}` —— 来自 Week 17 已有的
> `gen_ai.usage.total_tokens` 属性被 Langfuse 当用量读。成本只对 `generation`/`embedding` 计算，
> 因此不会产生错误成本，属可接受的语义重叠。

**⑦ 验收清单（全部通过）**

- [x] `docker compose ps`：六服务 running、四依赖 healthy
- [x] `GET /api/public/health` → `{"status":"OK","version":"4.50.0"}`；`/api/public/projects` → 200
- [x] `/observability/langfuse-status` → `enabled=true`、OTLP 端点正确、public key 仅掩码、无 secret 字段
- [x] `POST /agents/medical-assistant/runs` → 200 COMPLETED（真实模型、真实 token）
- [x] Langfuse 事件库查到同一 traceId（= 响应头 `traceparent` 第 2 段），根 span parent = 第 3 段
- [x] 观测树含 `agent.run`(AGENT) + `llm.chat`(GENERATION)×2 + `tool.call`(TOOL)×2（+ 框架自动埋点）
- [x] generation 的 usage 与响应体 usage 一致；成本两侧口径一致
- [x] 每个业务/框架 span 带 `trace_name` / `user_id=1` / `session_id=P001` / `tags` / `environment` / `release`
- [x] generation 上有 `prompt_name=medical-report|medical-followup` + `prompt_version=1`
- [x] `capture-content` 开关对照：true 有正文（脱敏+截断）/ false 正文长度为 0
- [x] 停机后业务仍 200，应用无业务异常（仅 SDK 导出 timeout）
- [x] `/observability/llm-cost-summary` 与 `/actuator/prometheus`（`llm_cost_usd_total`）给出成本

**⑧ UI 复核入口（留给人工看一眼）**

```text
Langfuse UI : http://192.168.132.128:3000   （账号 admin@ai-code-repo.local）
深链示例    : http://192.168.132.128:3000/project/ai-code-repo/traces/380090d6583d0c26da212603f0bb23d0
```

> 提示：VM 时钟比本机快约 8.5 小时（VM 07:5x / 本机 23:5x）；UI 默认时间范围若看不到刚推的数据，
> 放宽时间范围即可，这是环境时钟差异而非摄取延迟。

**⑨ 演示数据重置（批次 1 收尾，按使用者要求执行）**

验收数据里混着一条**脏成本**记录（第 ①/② 轮模型单价填错时产生的 3558.70 USD，见 ⑥-2），
为避免以后看成本看板时被它干扰，执行了一次干净重置：

```bash
cd ~/langfuse && docker compose down -v     # 只删 Langfuse 自己的 5 个卷；宿主 pg16 / redis7 未受影响
docker compose up -d && bash wait-langfuse.sh   # health OK（40s 内）
# 同一套 .env → headless 初始化用相同 pk/sk 重建项目，因此 8084 侧配置无需改动
```

重置后复核：`events_full = 0`、项目 `AI Code Repo Platform`、Prompt `medical-report@1` 与
`medical-followup@1`（production）、模型价 `deepseek-v4-pro inputPrice=2.7e-07 outputPrice=1.1e-06`
（= 0.27 / 1.10 USD per 1M）✔。

**顺带补交付物**：手工灌数时我第二次把单价填错（旧脚本 `seed-model.py` 还是 0.27/1.1），说明这件事
不该靠手写 curl。新增 [`deploy/langfuse/seed-langfuse.py`](../../deploy/langfuse/seed-langfuse.py)：
入参按「每 1M」给、内部除以 1e6 写库，幂等（先删同名模型定义），并带 `--check` 复核模式
（会把 `inputPrice` 大得离谱的定义标出来）。部署文档新增 3.5 节引用它。原验收证据已完整保留在本节，
数据卷删除不影响结论的可追溯性。

**⑩ 演示实例开启正文采集（批次 1 收尾，按使用者要求执行）**

干净环境重置后，按使用者要求把**本地模块 `.env`** 的 `LANGFUSE_CAPTURE_CONTENT` 置为 `true`，
以便在 Langfuse UI 上直接观察 Prompt 与回答正文。要点：

- **只改本地 `.env`（不入库）**；出厂默认仍是关闭 —— `application.yml` 的 `capture-content: ${LANGFUSE_CAPTURE_CONTENT:false}`
  与 `.env.example` 里的 `false` 都保持不动，新环境默认不会外发正文；
- 生效条件：`guardrail.enabled=true`（默认即 true），否则 `LangfuseContentPolicy` 会 fail-safe 到关闭并在启动时打 WARN；
  自检接口 `/observability/langfuse-status` 返回的 `captureContent` 是**计入联锁后的有效值**，可用于确认这次真的开了；
- 开启后正文仍受两条约束：**先经 `GuardrailPort.sanitizeTextOutput` 脱敏，再按 `langfuse.max-content-chars`（默认 2000）截断**
  （先脱敏后截断，避免把手机号截成半截数字串）；
- 想恢复「正文不出站」：把该值改回 `false` 并重启（环境变量不是热加载）；
- **真机复验（trace `691404f5b303a18d3b0e637a58310b5c`）**：重启后 `captureContent=true`、`maxContentChars=2000`；
  为验证脱敏，把手机号放进 Run 入参（`task: 请记录联系电话 13812345678 并安排随访`），实测：

```text
name                  type        input_len  output_len  raw_phone_in_input  masked_in_input
llm.chat              GENERATION        738        1282                   0              369
llm.chat              GENERATION       1816         963                   0              375
tool.call             TOOL               82         101                   0                0
chat deepseek-v4-pro  GENERATION          0           0                   0                0   ← 框架自带埋点不含正文，符合预期

聚合：raw_leaks = 0（未脱敏手机号一个都没有）  masked_hits = 2（138****5678 出现 2 次）
input 片段：[{"role":"system","content":"你是医疗报告撰写助手。根据提供的患者信息…  ← Langfuse 托管 Prompt 原文
output 片段：{"content":"患者：张三（P401，62岁，2型糖尿病）\n\n本次指标：收缩压 148.0 mmHg…  ← 模型真实输出
```

  也就是说：**正文确实上传了，且上传前已把手机号掩码成 `138****5678`（原件不出站）**。
  注意框架自动埋点的 `chat deepseek-v4-pro` 仍无正文 —— 正文只由我们的装饰器在 `llm.chat` / `tool.call` 上采集，
  通用属性仍保持「不写正文」的口径。

## 11. 风险与下周输入

- **本次现场踩到的 4 条环境约束（已写进部署文档，供后续复现）**：
  1. **镜像引用必须是 Docker Hub 域名**：官方 compose 用 `docker.langfuse.com/...` 属非 Hub registry，
     会**绕过**目标机配置的 registry mirror 去直连被墙的 `registry-1.docker.io`（实测 `connect: connection refused`）；
     改成 `docker.io/langfuse/...` 后由 mirror 正常拉取（`docker.langfuse.com` 与 Hub 的 tag/digest 一致）。
     另：`docker manifest inspect` 需要直连被墙的 `auth.docker.io`，**在受限网络下不可作为可用性判据**，
     要用 `docker pull` 判断。
  2. **Node 服务的内存上限要显式给**：`mem_limit` 太小会让 V8 先于 cgroup 触发 heap OOM（见 10.2 ⑥-1）。
  3. **Langfuse 的模型单价是「每 token」**，不是每百万——按 USD/1M 直接填会放大 1e6 倍（见 10.2 ⑥-2）。
  4. **明文 HTTP 下 JDK HttpClient 的 h2c 升级在跨机路径会失败**，Prompt 拉取必须显式 HTTP/1.1（见 10.2 ⑥-3）。
- **Langfuse 版本水位**：本周按 v4（当前主线）实现，最小基础设施版本 ClickHouse ≥ 25.12 / PostgreSQL ≥ 15 /
  Redis ≥ 7.0；v3 已进入仅安全补丁期，且 **v4 的 REST ingestion 对 trace/span/generation 返回 400**，
  因此「自研 ingestion 客户端」这条路已封死，后续若必须停留在 v3，需要改回 ingestion 适配器（不建议）。
- **v4 读接口形态**：`events_only` 模式下旧 `traces`/`observations` 读接口不可用，验证要么走 UI，
  要么查 ClickHouse `events_*`，要么用 Metrics v2（需 `query` 参数）。第 20 周做看板时要按 v4 API 设计。
- **双导出的属性外溢**：`langfuse.*` 属性会同时发给通用 collector（同一个 `BatchSpanProcessor`）。
  如需隔离，在 collector 侧用 `attributes` 处理器删除 `langfuse.` 前缀（部署文档已给出）。
- **成本口径**：本周以「客户端价目表上报」为主，未配价的模型交给 Langfuse 服务端按模型定义推断；
  两侧都未配时 Langfuse 不显示成本（不是显示 0）。第 20 周做成本看板时统一口径。
- **会话维度语义**：`langfuse.session.id` 取 Run 入参 `patientId`（无 chat_session 概念时的近似），
  第 21 周 SaaS 整合时统一。
- **本周实现的取舍说明（写给自己）**：最先尝试的路径是「应用内直连 Langfuse + 复用 Boot 的 OTLP 导出器」，
  读 Boot 源码后发现退避条件只看 `OtlpHttpSpanExporter` / `OtlpGrpcSpanExporter` **类型**，
  直接注册会把 collector 导出整块挤掉（症状是「Legacy 后端静默没有数据」，极难排查）。
  最终用「自定义类型包一层」既保住 collector 又实现扇出；这条经验值得在接任何第二后端时复用。
- **下周（Week 19 SkyWalking）输入**：Gateway 与 Spring Cloud 接入时，链路 ID 仍应复用本周的
  `traceparent` / `X-Trace-Id` 单源；Langfuse 与 SkyWalking 定位不同（LLM 观测 vs 服务拓扑），
  不要互相替代。
- **一次环境教训**：本会话中 Maven 首次构建失败于**沙箱文件权限**（工作区 `target` 写入被拒），
  而非代码问题；后续构建需要工作区可写。若再遇「Access denied 写 target」，
  先按 `diagnose-windows-sandbox-acl` 处理，不要改代码。
- **验收环境残留（待收尾）**：VM 上 `wl` 用户被加入 `docker` 组、本机 `E:\workNew\.ssh-week18\` 留有部署私钥、
  VM 的 `~/.ssh/authorized_keys` 有我这条公钥、`~/langfuse` 目录与六个容器仍在运行。
  按「用完即弃」约定，验收结束后应执行：`sudo gpasswd -d wl docker`、删除 authorized_keys 中对应行、
  删除本机私钥目录；容器是否保留由使用者决定（保留即可随时看 UI）。

## 12. 决策记录 ADR

| 决策 | 选项 | 选择 | 理由 |
|------|------|------|------|
| 接入协议 | REST ingestion / OTLP | **OTLP** | v4 上 ingestion 的 trace/span/generation 事件返回 400、旧同步端点 404；OTLP 是官方唯一路径，且已有 Week 17 管线 |
| Langfuse 版本 | v3 / v4 | **v4** | 新部署无历史包袱；v3 仅安全补丁到 2027-01；两者基础设施组件相同 |
| 双导出落地 | 改 OTLP 端点单发 / collector 扇出 / 应用内包一层 | **应用内包装 `SpanExporter`** | 读 Boot 源码确认退避条件只看类型；包装后 collector 导出器照旧装配，且不引入常驻 collector 组件 |
| trace 维度传播 | 只写 root span / SpanProcessor 全 span | **SpanProcessor 全 span 富化** | 官方要求维度出现在每个 span 上，否则按 user/session 过滤看不到子观测 |
| 正文采集 | 跟随通用 span 属性 / 独立开关 + 脱敏 + 截断 | **独立开关（默认关）+ Guardrail 脱敏 + 截断 + 联锁** | 观测系统不该默认成为第二份患者数据副本；guardrail 关闭时自动失效，宁可少数据不可漏数据 |
| 成本来源 | 只靠服务端推断 / 客户端上报 | **客户端价目表上报（可缺省）+ 服务端推断兜底** | 自部署环境未必配了国产模型价格；价目表在配置里让成本与汇总接口都能离线验收 |
| Prompt 管理落点 | 改 ai-core 契约 / app 内 `@Primary` 装饰 | **app 内装饰器** | 规范 5.8.2 要求 ai-core 契约稳定；装饰器零契约改动即可插入且可回退 |
| Prompt 版本关联 | 取最近一次加载 / 内容全等匹配 | **内容全等匹配** | 一个请求里可能加载多个 Prompt（报告 + 随访），全等是确定性判据，不匹配就不挂 |
| `agent.run` 正文 | 一并上报 / 不做 | **不做** | 需要给 `PlatformExecutionUseCase` 增依赖并改既有构造器与 5 个测试；Langfuse 上真正需要正文的是每次模型调用（YAGNI） |
| 装饰器构造器 | 直接改签名 / 保留旧构造器重载 | **保留旧构造器（默认关闭）** | 既有 Week 17 测试一行未改即通过，是「零回退」最直接的证据 |
| Java SDK | 引 `com.langfuse:langfuse-java` / 自写一个 GET | **自写 `LangfusePromptClient`** | 官方明确不建议用该 SDK 做 tracing；本周只需拉 Prompt 一个只读接口，引生成式 SDK 会多一份依赖与升级负担 |
| 模型价目表放哪 | 只在启动参数里传 / 写进 `application.yml` 出厂即配 | **写进 `application.yml`，数值用环境变量占位可覆盖** | 只在启动参数里传，等于每个环境都要记得手写两行，忘一次就静默没有成本；写成 `input-per-1m: ${ENV:默认值}` 既开箱可用又便于换环境，且键名（模型名）留在版本控制里 |
| 嵌套测试类怎么被 surefire 发现 | 不支持（保持现状）/ 改 JUnit5 `@Nested` / 模块 pom 显式 includes + 覆盖 excludes | **模块 pom 显式 includes/excludes** | 改造成本最低且一次覆盖全模块（含 Week 17 那两个从未执行的类）；`@Nested` 需要逐个类改造，且部分类依赖「两个独立上下文」的静态嵌套语义 |
