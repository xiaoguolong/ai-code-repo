# Week 17 实现日志 — OpenTelemetry（Trace / Span / Metric / Context）

- 日期：2026-10-04
- 批次：1
- 对应 Spec：docs/specs/week-17.md
- 对应架构：docs/architecture/week-17-architecture.md
- 对应接口：docs/api/week-17-api.md
- 对应阅读：notes/week-17-opentelemetry.md
- 对应 Postman：docs/postman/week-17.postman_collection.json

## 1. 本周目标

1. 接入 **OpenTelemetry**（Micrometer Tracing 门面 + bridge-otel，OTLP/HTTP 导出），覆盖
   **HTTP 入站 → Agent 执行 → LLM 调用 → Tool 调用** 四段链路。
2. 新增观测端口 `AgentObservabilityPort`（领域层不出现任何 Micrometer/OTel 类型）+ 两个装饰器
   （`ObservableChatModelAdapter` / `ObservableToolPort`）+ `HttpServerSpanFilter`。
3. 输出 **调用与 Token 指标**（`agent.run.*` / `llm.call.*` / `llm.tokens.total` / `tool.call.*`），
   经 `/actuator/prometheus` 暴露。
4. **不破坏 Week 16 契约**：`X-Trace-Id` 原样回显、响应体 traceId、审计 traceId 全部保持，
   并让「日志 traceId ↔ 链路 traceId」单源可互推。

## 2. 边界

- Always：TDD（先 RED）；中文 JavaDoc；既有 API 契约不破坏；span 属性不含正文/隐私；无密钥入库；
  指标取真实 usage；OTLP 不可达不影响业务
- Never：引 Langfuse / SkyWalking / Grafana / Loki；把 OTel 类型放进 `domain` / `controller`；
  改 `X-Trace-Id` 回显契约；改 Graph / Workflow / Multi-Agent 编排；改 `libs/ai-core` 契约

## 3. 增量架构

见 `docs/architecture/week-17-architecture.md`（图 2 增量拓扑、图 3 单源派生、图 4 span 树、图 5 装配开关）。

## 4. 新增 / 改动类型清单

| 类型 | 名称 | 职责 |
|------|------|------|
| Model | `TraceContext` | W3C traceparent 解析结果（valid / traceId / parentSpanId / sampled） |
| Util | `TraceIds`（改动） | traceId 生成、**OTel traceId 确定性派生（MD5）**、traceparent 解析与格式化 |
| Filter | `TraceIdFilter`（改动） | traceId → MDC；**注入 `traceparent`**；回写 W3C 响应头 |
| Filter | `HttpServerSpanFilter` | 框架未建 span 时补 `http.server` SERVER span；已有活动 span 时不重复建 |
| Port | `AgentObservabilityPort` | 观测唯一入口：`openSpan` / `recordTokenUsage` / `recordCounter` / `recordDuration` / `currentTraceContext` / `activeSpanId` |
| Model | `SpanKind` / `SpanScope` / `TraceContextView` | span 种类、生命周期句柄（AutoCloseable）、链路上下文视图 |
| Const | `ObservabilityAttributes` | 属性键与标签键常量（对齐 OTel GenAI 语义约定，禁魔法字符串） |
| Adapter | `MicrometerObservabilityAdapter` | 唯一允许出现框架类型的实现：Tracer + MeterRegistry；MDC 单源对齐与恢复 |
| Adapter | `NoopObservabilityAdapter` | 观测关闭时的空实现（零 span、零指标） |
| Config | `ObservabilityConfiguration` | 按 `observability.enabled` 互斥装配真实/空实现 |
| Decorator | `ObservableChatModelAdapter` | `llm.chat` span + `llm.call.*` + `llm.tokens.total` |
| Decorator | `ObservableToolPort` | `tool.call` span + `tool.call.count` |
| UseCase | `PlatformObservabilityUseCase` | 读取当前链路上下文 |
| Controller | `PlatformObservabilityController` | `GET /api/v1/platform/observability/trace-context` |
| DTO | `TraceContextResponse` | 链路上下文响应体 |
| Test | `TraceIdsTest`(11) / `MicrometerObservabilityAdapterTest`(17) / `ObservableChatModelAdapterTest`(7) / `ObservableToolPortTest`(6) / `PlatformExecutionObservabilityTest`(7) / `HttpServerSpanFilterTest`(7) / `ObservabilityDisabledTest`(5) / `ObservabilityHttpContractTest`(8) / `ObservabilityProductionTraceLinkTest`(3) | 链路/span/指标/开关/HTTP 契约/生产建链 |
| Test Util | `OtelSdkTestSupport` / `InMemoryOtelTracer` | 真实 OTel SDK + `InMemorySpanExporter` 断言基座 |

改动既有文件：`PlatformExecutionUseCase`（新增观测端口参数 + `agent.run` span 与指标）、
`AppConfiguration`（`@Primary` 装饰器装配 + `@Qualifier` 显式指名）、
`application.yml`（management / observability 配置）、`src/test/resources/application-test.yml`（关 OTLP 导出 + 采样 1.0）、
`pom.xml`（actuator / bridge-otel / otlp-exporter / prometheus / micrometer-tracing-test / opentelemetry-sdk-testing）、
既有 3 个测试的构造器（`PlatformExecutionUseCaseTest` / `PlatformExecutionAuditTest`）。

## 5. RED

- 先写 8 个测试类（含 1 个测试基座），随后执行
  `mvn -Djdk.17.home=... -pl apps/spring-ai-alibaba-agent -am test-compile`
- 结果：`BUILD FAILURE`（编译期即失败）
- 原因：`com.aicode.framework.observability.domain` / `.infrastructure` / `.application` 包、
  `TraceContext`、`TraceIds.otelTraceId` / `parseTraceparent` / `formatTraceparent`、
  `HttpServerSpanFilter` 等待测类型尚不存在（55+ 处 `cannot find symbol` / `package does not exist`）

## 6. GREEN

- 改动文件：见第 4 节清单（新增 19 个文件，改动 8 个文件）
- 命令：根目录 `mvn -Djdk.17.home=... test`（等价 `run-maven-jdk17.ps1 test`）
- 结果：`BUILD SUCCESS`，根聚合 **381** 个用例全绿

| 模块 | 用例数 | 结果 |
|------|--------|------|
| ai-core | 90 | 全绿 |
| enterprise-knowledge-agent | 21 | 全绿 |
| patient-agent | 19 | 全绿 |
| spring-ai-alibaba-agent | **251** | 全绿 |

8084 模块 Week 16 为 186，本周新增 65 个用例。

## 7. 重构

- **做了什么**：
  1. span 生命周期统一收口到 `SpanScope`（`AutoCloseable` + 幂等 `close`），
     `MicrometerObservabilityAdapter` 内保证「先结束 span、再退出作用域、最后恢复 MDC」的顺序；
  2. `MicrometerObservabilityAdapter.toMicrometerKind` 把「领域 `SpanKind` → Micrometer `Span.Kind`」映射
     从领域枚举搬进适配器（领域层彻底不出现框架类型）；
  3. `AppConfiguration` 中三个 `ToolPort` / 两个 `ChatModelPort` 的解析全部改为 `@Qualifier` 显式指名
     （多个 `@Primary` 会让 Spring 直接抛 `NoUniqueBeanDefinitionException`，实测踩到）；
  4. LLM 调用的次数/耗时指标抽成 `recordCall(...)` 一处，成功/失败路径共用，避免标签写法漂移。
- **没做什么**：没有引入 javaagent；没有把 `ChatModelPort` / `ToolPort` 契约下沉改动；
  没有为 `guardrail.blocked.count` 改动既有服务构造器（YAGNI）。

## 8. 质量门禁

- [x] 编译：`mvn -Djdk.17.home=... test` 全模块通过
- [x] 单测：根聚合 377 用例全绿，Week 8–16 无回退
- [x] JDK：未改 `JAVA_HOME`；构建/测试命令只传 `-Djdk.17.home=...`（打包运行 jar 时另用系统 JDK17 跑 Maven，见第 10.1 节）
- [x] 文档：新增/改动 public 类型均有中文 JavaDoc
- [x] 分层：`domain` / `application` 无 `io.micrometer.*` / `io.opentelemetry.*` 类型；
      Controller 只做登录态与 DTO 转换；SQL 仍只在 `infrastructure/persistence`
- [x] 安全：无密钥入库；span 属性无 Prompt/输出正文、无患者标识；`traceparent` 只含随机 ID
- [x] YAGNI：未引入 Langfuse / SkyWalking / Grafana / ORM / Testcontainers
- [x] 日志：本文件已填 RED/GREEN 与真实验收证据

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
[INFO] Tests run: 251, Failures: 0, Errors: 0, Skipped: 0
[INFO] Building ai-code-repo 0.1.0-SNAPSHOT                               [5/5]
[INFO] BUILD SUCCESS
```

关键验收点（均有对应测试）：

- **单源派生**：`TraceIds.otelTraceId("client-trace-123")` = `MD5` 32 hex 且同输入恒等（`TraceIdsTest`）
- **W3C 解析**：合法/非法/全 0/非 00 版本 traceparent 的解析与 round-trip（`TraceIdsTest`）
- **同源建链**：带上游 `traceparent` 时 span traceId = `otelTraceId(MDC traceId)`、`parentSpanId` = 上游 spanId（`MicrometerObservabilityAdapterTest`、`HttpServerSpanFilterTest`、`PlatformExecutionObservabilityTest`）
- **子 span 建链**：`llm.chat` / `tool.call` 的 `parentSpanId` = `agent.run` 的 `spanId`，traceId 相同（`ObservableChatModelAdapterTest`、`ObservableToolPortTest`）
- **无假父边 / 不全 0 父**：无上游时 `parentSpanId` = 全 0（只做根 span），避免「全 0 父被 SDK 忽略」与「随机父产生孤儿节点」两种坏情况（`MicrometerObservabilityAdapterTest`）
- **生产路径**：真实端口请求下 `traceparent` 的 trace-id = `MD5(X-Trace-Id)`、`X-Trace-Id` 原样回显、上游 traceparent 被采纳（`ObservabilityProductionTraceLinkTest`）
- **指标**：`agent.run.count` / `agent.run.duration` / `llm.call.count` / `llm.tokens.total` / `tool.call.count` 标签与数值（`MicrometerObservabilityAdapterTest`、`PlatformExecutionObservabilityTest`）
- **隐私**：`llm.chat` 属性不含消息正文、`tool.call` 属性不含参数与结果正文、`agent.run` 属性不含脱敏前后手机号与 patientId
- **开关**：`observability.enabled=false` → 空实现、零 span、零指标、不写 `traceparent`，业务功能不变（`ObservabilityDisabledTest`）
- **HTTP 契约**：`X-Trace-Id` 原样回显、`traceparent` 合法、上游 `traceparent` 被采纳、未登录 401 带 traceId、健康检查 UP、Prometheus 文本格式（`ObservabilityHttpContractTest`）

## 10. 真实验收（批次 1，真实 OTLP collector + 真实 PostgreSQL + 真实 LLM）

环境：本机 `E:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot` 运行打包产物；
PostgreSQL 与 Redis 在独立虚拟机（连接串取自本环境模块 `.env`，未新建库、未改同库其它应用对象）；
OTLP collector 为临时最小 OTLP/HTTP 接收器（按 protobuf wire format 解析 `/v1/traces`，
验收后删除，不属交付物，复现步骤见第 12 节）。

### 10.1 真库与启动

```text
Flyway: Current version of schema "public": 3        ← 本周无新增迁移，仅校验
Tomcat started on port 8084 (http) with context path '/'
Started SpringAiAlibabaAgentApplication in 14.953 seconds
```

> 启动方式说明：本机 `mvn` 默认跑在 JDK 1.8 上，`spring-boot:run` / `repackage` 会因
> `spring-boot-maven-plugin` 是 class file 61.0 而失败（`UnsupportedClassVersionError`）。
> 因此打包时用 `JAVA_HOME=D:\soft\jdk17.0.8` 让 Maven 自身跑在 JDK 17 上，
> 再用 `-Djdk.17.home=...` 指向项目约定的 Adoptium JDK 17 编译/运行测试。未改系统 `JAVA_HOME`。

### 10.2 真实 HTTP 验收（4 组）

| # | 请求 | HTTP | 响应头 `X-Trace-Id` | 响应头 `traceparent` | 响应体 `traceId` |
|---|------|------|--------------------|---------------------|------------------|
| 1 | 未登录 `GET /observability/trace-context` + `X-Trace-Id: week17-real-trace` | 401 | `week17-real-trace` | `00-ab475d15…b941f-8c6938d9c121422f-01` | `ab475d15…b941f` |
| 2 | `POST /auth/login`（admin） | 200 | `week17-real-trace` | `00-ab475d15…b941f-8e8c5ddeab98420b-01` | `ab475d15…b941f` |
| 3 | 登录后 `GET /observability/trace-context` | 200 | `week17-real-trace` | `00-ab475d15…b941f-d57a0aab4df24f0b-01` | `ab475d15…b941f` |
| 4 | 带上游 `traceparent: 00-4bf92f35…4736-00f067aa0ba902b7-01` | 200 | `4bf92f35…4736` | `00-4bf92f35…4736-9cbf281fd6eddb03-01` | `4bf92f35…4736` |

结论：

1. **`MD5("week17-real-trace")` = `ab475d15eddb104d282fd1cfde6b941f`**，与 `traceparent` 第 2 段、
   响应体 `traceId` 完全一致 → 「客户端 traceId ↔ 链路 traceId」可互推；
2. 上游合法 `traceparent` 被采纳：traceId、`X-Trace-Id`、响应 `traceparent` 三者都为上游 trace-id；
3. `X-Trace-Id` 始终原样回显客户端值 → Week 16 契约零破坏。

> 有意保留的双 ID 语义：响应头 `X-Trace-Id` = 客户端原始串联键（用于对账客户端）；
> 响应体 `traceId` / 服务端日志 `[traceId]` / 审计 `audit_log.trace_id` / `traceparent` 第 2 段
> = 同一条 OTel traceId（用于链路后端检索）。两者由 MD5 关系绑定，不是两套互不相干的 ID。

### 10.3 真实 collector 收到的 span 树（真实 LLM 调用，最终复验）

请求：`POST /api/v1/platform/agents/medical-assistant/runs`
（`X-Trace-Id: week17-final-check`，body `{"input":{"patientId":"P001","task":"follow up advice"}}`）
→ **HTTP 200 COMPLETED**（真实模型产出中风险报告 + 随访计划）。

响应头：`traceparent: 00-dbd1165e330c6483a62b4a4f931e193f-632472a6e8cd40b5-01`

其中 `MD5("week17-final-check")` = `dbd1165e330c6483a62b4a4f931e193f` —— **与 `traceparent` 第 2 段一致**。

collector 落盘 14 个 span，本次请求所在 trace 的树形为：

```text
http post /api/v1/platform/agents/{agentKey}/runs  spanId=e5892050392a230c  parent=632472a6e8cd40b5  ← 上游父 = 响应 traceparent 第 3 段
└── agent.run                                      spanId=70d431c677d21ba5  parent=e5892050392a230c
    ├── llm.chat  spanId=3eb25246cb234973          parent=70d431c677d21ba5
    │   └── chat deepseek-v4-pro  spanId=fbcb70ce27864c50  parent=3eb25246cb234973
    │       └── http post         spanId=658fe601f0b3fdc1  parent=fbcb70ce27864c50
    ├── llm.chat  spanId=4934397161e41b63          parent=70d431c677d21ba5
    │   └── chat deepseek-v4-pro  spanId=666d94bd354ba7bb  parent=4934397161e41b63
    │       └── http post         spanId=f7b5c2e36ed50002  parent=666d94bd354ba7bb
    ├── llm.chat  spanId=e58f1f0a68260b47          parent=70d431c677d21ba5
    │   └── chat deepseek-v4-pro  spanId=18d163fb495dfe29  parent=e58f1f0a68260b47
    │       └── http post         spanId=306c4438a32a2be6  parent=18d163fb495dfe29
    ├── tool.call PatientLookupTool  spanId=39695cfa39e0e6c3  parent=70d431c677d21ba5
    └── tool.call HealthMetricTool   spanId=7952d898c104f83e  parent=70d431c677d21ba5
```

三条结论：

1. **链路 ID 单源**：traceId = `MD5(X-Trace-Id)`，同时出现在响应头 `traceparent`、响应体 `traceId`、
   服务端日志 `[traceId]`、审计 `audit_log.trace_id` 与链路后端；
2. **父边真实**：根 server span 的 `parentSpanId = 632472a6e8cd40b5`，正是响应头 `traceparent` 第 3 段
   → 注入的 W3C 上下文被链路后端真实采纳；
3. **span 树完整**：`http server → agent.run → llm.chat / tool.call`，
   且 Spring AI 自动埋点的 `chat deepseek-v4-pro` / `http post` 作为 `llm.chat` 子 span 一并落盘 —— 未关闭框架观测的红利。

span 属性抽样（collector 落盘原文）：

```text
llm.chat:
  gen_ai.system=spring-ai            gen_ai.request.model=deepseek-v4-pro
  gen_ai.response.finish_reason=STOP gen_ai.usage.prompt_tokens=265
  gen_ai.usage.completion_tokens=1731  gen_ai.usage.total_tokens=1996
  span.kind=LLM_CALL

tool.call:
  tool.name=PatientLookupTool  tool.call.id=query_patient
  tool.result.chars=78         duration.ms=15        span.kind=TOOL_CALL

agent.run:
  agent.key=medical-assistant  agent.type=MEDICAL_ASSISTANT  user.id=1
  execution.id=<uuid>          execution.status=COMPLETED    duration.ms=20
  span.kind=AGENT_RUN
```

> 早先批次还观测到一次 `agent.run` 为 `FAILED` 的记录（来自未带 `input` 包装的探测请求），
> 说明失败路径同样产出 span 与 `FAILED` 指标。

### 10.4 Prometheus 指标出口实测

```text
GET /actuator/prometheus  → 326 行

agent_run_count_total{agent_key="medical-assistant",execution_status="COMPLETED"} 1.0
agent_run_count_total{agent_key="medical-assistant",execution_status="FAILED"} 5.0
agent_run_duration_seconds_count{agent_key="medical-assistant",execution_status="COMPLETED"} 1
llm_call_count_total{gen_ai_request_model="deepseek-v4-pro",llm_outcome="SUCCESS"} 2.0
tool_call_count_total{tool_name="PatientLookupTool",tool_outcome="SUCCESS"} 1.0
tool_call_count_total{tool_name="HealthMetricTool",tool_outcome="SUCCESS"} 1.0
http_server_requests_seconds_count{method="POST",status="200",uri="/api/v1/platform/agents/{agentKey}/runs"} 1
jvm_memory_used_bytes{...}   （框架自带指标同时暴露）
```

`agent.run.count{COMPLETED}=1` 对应真实的 1 次成功执行，`llm_call_count=2` 对应 ReAct 两轮模型调用，
`tool.call.count=2` 对应两次工具执行 —— 指标与真实业务动作一一对应。

## 10.5 Postman 集合并发验证（收尾批次）

对 `docs/postman/week-17.postman_collection.json` 的请求逐条按其 `test` 脚本断言在真实服务上执行。

### 10.5.1 主链 8 个请求：**27 条断言全部通过**

| 请求 | 断言要点 | 结果 |
|------|----------|------|
| 1 登录（admin） | 200 / `code=SUCCESS` / `data.token` 非空 / `body.traceId` 非空 | 4/4 |
| 2 自检（带 `X-Trace-Id`） | 响应头原样回显；`body.traceId==data.traceId`；`otelTraceId` 32hex；`spanId` 16hex；`traceparent` 三段一致；`observabilityEnabled=true` | 6/6 |
| 3 自检（带 W3C `traceparent`） | 采纳上游 trace-id；响应 `traceparent` 沿用；`X-Trace-Id` 变为上游值 | 3/3 |
| 4 未登录 | 401 / `UNAUTHORIZED` / `data=null` / 仍带 `traceId` | 4/4 |
| 5 `actuator/health` | 200 / `UP` | 1/1 |
| 6 `actuator/prometheus` | 含 `# TYPE` / `jvm_memory_used_bytes` / `http_server_requests` | 3/3 |
| 7 Agent Run | 带 `traceId`；`traceparent` 合法；`status=COMPLETED`；`model` 非空 | 4/4 |
| 8 执行记录 | `code=SUCCESS`；`data` 为数组；首条含 `usage` | 3/3 |

### 10.5.2 新增「9. 指标回查」：**12 条断言全部通过**

背景：请求 6 抓指标时还没跑过 Agent，`agent_run_count_total` 必然是 0 行（顺序导致的正常现象，
不是漏埋点）。为避免误判，集合末尾新增请求 **9. 指标回查（跑完 Agent 后验证自定义指标）**，
把「自定义指标真的产生了」这组断言放到 Agent Run 之后执行。实测输出：

```text
agent_run_count_total{agent_key="medical-assistant",execution_status="COMPLETED"} 1.0
llm_call_count_total{gen_ai_request_model="deepseek-v4-pro",llm_outcome="SUCCESS"} 2.0
llm_call_duration_seconds_count{gen_ai_request_model="deepseek-v4-pro",llm_outcome="SUCCESS"} 2
llm_call_duration_seconds_sum{gen_ai_request_model="deepseek-v4-pro",llm_outcome="SUCCESS"} 34.274
llm_call_duration_seconds_max{gen_ai_request_model="deepseek-v4-pro",llm_outcome="SUCCESS"} 18.042
llm_tokens_total{gen_ai_request_model="deepseek-v4-pro",token_type="completion"} 2988.0
llm_tokens_total{gen_ai_request_model="deepseek-v4-pro",token_type="prompt"} 790.0
tool_call_count_total{tool_name="HealthMetricTool",tool_outcome="SUCCESS"} 1.0
tool_call_count_total{tool_name="PatientLookupTool",tool_outcome="SUCCESS"} 1.0
```

请求 2 的实测响应（可作为集合断言正确性的样例）：

```json
{"code":"SUCCESS","message":"OK","data":{
  "traceId":"a2234718bfd32b952871b1fa29f32a88",
  "otelTraceId":"a2234718bfd32b952871b1fa29f32a88",
  "spanId":"a30e62af5c8f57a9",
  "traceparent":"00-a2234718bfd32b952871b1fa29f32a88-a30e62af5c8f57a9-01",
  "sampled":true,"observabilityEnabled":true},
 "traceId":"a2234718bfd32b952871b1fa29f32a88"}
```

请求 8 返回的最新执行记录（真实 token 用量）：

```text
status=COMPLETED model=deepseek-v4-pro usage={"promptTokens":700,"completionTokens":3958,"totalTokens":4658}
```

### 10.5.3 本次并发验证发现并修正的 3 个问题（都在文档/集合，不在代码）

| # | 问题 | 影响 | 处置 |
|---|------|------|------|
| 1 | 集合里所有请求的 `url.host` 写成 `["{{baseUrl}}"]`（把含协议的完整 URL 塞进 host 字段），且 login body 里用户名带多余空格 | 导入 Postman 后 URL 解析可能不按预期展开 | 集合改为标准结构：新增 `protocol` / `host` / `port` 三个变量，`url` 用 `protocol`+`host`+`port`+`path[]`；body 重写 |
| 2 | 文档把 token 指标的 Prometheus 名写成 `llm_tokens_total_total` | 按文档去 grep 会**一个都搜不到**（Micrometer 的 counter 不会给已含 `_total` 的名字再加后缀） | 全仓修正为 **`llm_tokens_total`**（README / spec / api / 代码注释共 4 处），并把 api 文档的示例输出换成实测原文 |
| 3 | 集合没有「跑完 Agent 后再看指标」的请求，导致按顺序跑会在请求 6 看到 0 行而误判埋点缺失 | 验证者可能得出「自定义指标没生效」的错误结论 | 新增请求 9（含 12 条断言），集合说明里写明执行顺序与「请求 6 计数为 0 属正常」的原因 |

> 问题 2 是「文档与实现不一致」类缺陷：代码一直是对的，是文档写错了名字。
> 触发点是本次用集合跑完后，发现 `grep llm_tokens_total_total` 命中 0 行才暴露。
> 问题 3 则是「验证脚本本身有顺序陷阱」——**验收脚本和被测代码一样需要被验证**。

## 11. 真实验收暴露并修复的 4 个缺陷（单测全部测不出来）
| # | 现象 | 根因 | 处置 |
|---|------|------|------|
| 1 | 单测全绿，真实服务器上「日志 traceId」与「Jaeger traceId」是两个不同 ID | Spring Boot 自动 HTTP 观测在自建 Context 中生成 OTel traceId，**不读**我们按 `X-Trace-Id` 派生并写入 MDC 的值 | `TraceIdFilter` 把派生出的 traceId 以**标准 `traceparent` 头注入请求**，让标准传播器（框架观测）采纳同一条 trace-id；第 10.3 节确认响应头 traceparent 与落盘 traceId 完全一致 |
| 2 | 一度同时出现两个 `http.server` 兄弟 span（业务入口重复） | 自建 `HttpServerSpanFilter` 与框架观测都建 server span | 过滤器改为「框架已有活动 span 就不重复建」；第 10.3 节确认每个请求只有一个 server span |
| 3 | span 树出现指向不存在 span 的父子假边（孤儿子节点） | 注入 `traceparent` 时用了新生成的占位 spanId 当父 | 只在**上游真的给了 traceparent** 时用其 spanId 建远程父；否则不编造父 spanId |
| 4 | 修 #3 时改用「全 0 父 spanId」，结果子 span 的 traceId 变成随机值（第 9 节 5 个用例失败） | OTel SDK 判定 `spanId=0000000000000000` 的父上下文无效，**整条父上下文被忽略**（含 traceId），直接另生成一条 trace | 用一段最小复现程序确认（`parentSpanId=0000…` → traceId 不匹配；`aabbccdd…` → 匹配），改为「无活动 span 且无上游父时**不设父**」，并保留「有活动 span 时走正常父子」这条生产主路径；第 10.3 节确认 span 树依旧完整 |

另有一个**仅在整模块测试运行时**才出现的顺序相关问题：`PlatformExecutionObservabilityTest` 中
「span 内 MDC 应等于请求 traceId」的断言，在单独跑该类时通过、在完整模块跑时失败。
排查后用 `-Dweek17.probe.mdc=true` 定位到：Spring Boot 的 `CorrelationScopeDecorator`
会在 span 成为当前 span 时把 **span 自己的 traceId** 写进 MDC —— 这正是「日志 ID 与链路 ID 一致」
所依赖的正确行为，不是缺陷。故该断言按真实语义更正（改为验证 span traceId = 派生值），
并在 `MicrometerObservabilityAdapter.restoreMdc` 的 JavaDoc 里写明这一协作关系。

**一条经验**（写给后续周次）：接入标准化设施时，「用最小复现程序验证库的真实行为」
比反复猜测快得多 —— 本次 #4 的根因 3 分钟就被一段 20 行的 `main` 定位。

## 12. 收尾专项：collector 不可达时的真实行为（把「文档声称」变成「实测结论」）

触发：使用中出现 `java.net.ConnectException: Failed to connect to localhost/[0:0:0:0:0:0:0:1]:4318`。
第 9 节与文档原先只写了「collector 不可达时导出只打 WARN，不影响业务」，但**从未实测验证过**。
本批次专门跑了一轮无 collector 场景（23 个业务请求），结论如下。

### 12.1 该报错是预期的，不是配置错误

- 只要 4318 上没起 collector 就会出现；由 **OTel SDK 自己的 logger**
  （`io.opentelemetry.exporter.internal.http.HttpExporter`）输出，不经过本项目的日志规范。
- 报错里的 `[]` 是空的 traceId —— 批量导出跑在 SDK 的线程池线程上，没有请求上下文，属正常。

### 12.2 实测数据

| 观察项 | 实测结果 | 结论 |
|--------|----------|------|
| 业务状态码 | **23/23 全部 200** | ✅ 「可观测设施不是业务单点」成立 |
| 应用自身错误日志 | **0 条**（排除 SDK 那几条） | ✅ 导出失败不污染业务日志 |
| 启动阶段报错 | **0 次** | ✅ 启动不打网络 |
| 报错日志级别 | **ERROR**（文档原写 WARN，**不准确**） | ⚠️ 与「审计只打 WARN」的约定不同 |
| 报错频率 | 按**批次** 1 条，不按请求；每次含 ~23 行堆栈 | ⚠️ 持续不可达会累积 |
| 日志体积增长 | 3 请求后 8.6 KB → 再 20 请求后 16.9 KB | 可接受，但长期不可达会持续增长 |

**修正**：文档中「导出线程打 WARN」的说法不成立，已按实测改为 ERROR，并说明其来源是第三方组件
（本项目不应去覆盖第三方日志级别），同时给出运维影响：**若告警把 ERROR 当事故，collector 未起会持续误报**。

### 12.3 推荐做法：本地没 collector 就关掉导出

实测 `OTEL_EXPORT_ENABLED=false`（无 collector）：

```text
登录 HTTP = 200
X-Trace-Id: off-test
traceparent: 00-b42b069501c668c82c80e3d41db1f68a-0b275432c9ad41c0-01
ConnectException 次数 : 0
'Failed to export' 次数: 0
observability.enabled 仍为 true → span 照常产生、MDC/日志 traceId 照常、/actuator/prometheus 照常
```

**关键澄清**：`OTEL_EXPORT_ENABLED=false` 只影响「span 是否送到链路后端」，
**不影响** `observability.enabled` 的埋点、日志 traceId 关联、审计 traceId、Prometheus 指标。
这点已写入 `docs/api/week-17-api.md` 5.1 / 5.2 节。

### 12.4 一个操作教训

排查过程中第一次重启失败，报的是 `Port 8084 was already in use` —— 上一批
`job_kill` 杀掉的是 PowerShell 外层进程，**子 Java 进程仍存活并占着端口**。
后续停服应显式确认端口释放（`Get-NetTCPConnection -LocalPort 8084`），
不要只看 job 状态。已在本批次处理掉残留进程。

## 13. 风险与下周输入

- **复现本周真实验收**（collector 脚本为临时文件，未入库）：
  1. 起一个 OTLP/HTTP 接收器监听 `127.0.0.1:4318/v1/traces`（本仓库脚本按 protobuf wire format 解析，
     只需记录收到的 span 名与 traceId/spanId/parentSpanId）；
  2. 用 JDK17 跑 Maven 打包：`JAVA_HOME=<jdk17> mvn -q -DskipTests -pl apps/spring-ai-alibaba-agent -am package`；
  3. 在模块目录加载 `.env` 后启动：
     `OTEL_EXPORTER_OTLP_ENDPOINT=http://127.0.0.1:4318/v1/traces OTEL_EXPORT_ENABLED=true java -jar target/spring-ai-alibaba-agent-0.1.0-SNAPSHOT.jar`；
  4. 登录后跑 `POST /api/v1/platform/agents/medical-assistant/runs`，body 形如
     `{"input":{"patientId":"P001","task":"follow up advice"}}`；
  5. 比对响应头 `traceparent` 第 2/3 段与 collector 落盘 span 的 `traceId` / 首个 span 的 `parentSpanId`。
- **MockMvc 下不断言 span 导出**：`@SpringBootTest` + MockMvc 上下文中框架观测的 span 未落内存 exporter
  （standalone 过滤链正常）。因此 HTTP 契约与 span 断言分处两个测试类；
  生产链路以第 10 节真实 collector 验收为准。下周接 Langfuse 时同样建议用真实运行验证。
- **server span 命名不统一**：真实服务器上是 `http get /path`（框架观测名），
  备用过滤器上才是 `http.server`。若第 20 周要看板统一，可配 `management.observations.http.server.requests.name=http.server`。
- **采样率默认 1.0**：演示足够，生产必须按容量降到 0.1 量级（环境变量 `OTEL_TRACES_SAMPLER_PROBABILITY`）。
- **`guardrail.blocked.count` 未做**：需给 `PlatformGuardrailService` 加观测依赖并改 5 个既有测试构造器，留待有明确需求时补。
- **OTLP 导出为异步批量**：collector 不可达时业务请求不受影响（23/23 返回 200），但 **SDK 会打 ERROR + 堆栈**，
  日志随重试累积；本地无 collector 时建议 `OTEL_EXPORT_ENABLED=false`（详见第 12 节）；
  第 20 周引入 Prometheus/Grafana 时需同时规划 collector 高可用与 trace 保留策略。
- **下周（Week 18 Langfuse）输入**：本周已把 `traceId` 打通到「响应体 / 日志 / 审计 / 链路后端」四处，
  Langfuse 接入时应直接复用该 traceId 关联，不要另建 ID；`gen_ai.*` 属性已按语义约定命名，可直接映射。

## 13. 决策记录 ADR

| 决策 | 选项 | 选择 | 理由 |
|------|------|------|------|
| OTel 落地方式 | javaagent / 代码内 Micrometer Tracing 桥接 | 代码内 `micrometer-tracing-bridge-otel` | javaagent 打不出 `agent.run` 这类业务语义 span；桥接方案复用 Boot 自动装配且零额外进程参数 |
| traceId 关系 | 两套 ID / 由既有 traceId 派生 | 派生（MD5 → 32hex） | 两套 ID 会让日志、审计、链路三方对不上；派生后 `X-Trace-Id` 契约不破坏 |
| 框架 span 与自建 span | 关闭框架观测 / 注入 `traceparent` 共存 | 注入 + 复用框架 span | 关闭框架观测会丢 `http_server_requests` 指标，且空观察名会引发 NPE（实测）；注入方案让框架 span 与日志/审计共用一个 traceId |
| 无上游时的父 spanId | 随机 / 全 0 | 全 0 | 避免链路后端画出指向不存在 span 的父子假边 |
| 观测抽象层级 | 直接用 `Tracer` / 自定义 Port | 自定义 `AgentObservabilityPort` | 规范 3.2 禁止框架类型泄漏到领域层；单测可用空实现，换后端不动调用方 |
| 指标出口 | 自研端点 / Micrometer + Actuator | `/actuator/prometheus` | Spring Boot 事实标准，零成本对接第 20 周体系 |
| 测试断言手段 | 连真 collector / `SimpleTracer` / 真实 OTel SDK | 真实 OTel SDK + `InMemorySpanExporter` | `SimpleTracer` 的 span context 不保证合法 traceId/spanId，验证不了核心契约；真 collector 留作验收 |
| 门面类型归属 | `domain` / `infrastructure` | `infrastructure` | 领域层不出现 `io.micrometer.*` / `io.opentelemetry.*` |
| Kind 映射位置 | 领域枚举内 / 适配器内 | 适配器内 | 领域枚举若引用 `Span.Kind` 就产生框架依赖；映射属基础设施细节 |
| ToolPort 解析方式 | 依赖 `@Primary` / `@Qualifier` 显式指名 | `@Qualifier` | 容器内有 3 个 `ToolPort`、2 个 `ChatModelPort`；多个 `@Primary` 直接抛 `NoUniqueBeanDefinitionException`（实测） |
| `guardrail.blocked.count` | 本周做 / 推迟 | 推迟 | 需改 5 个既有测试构造器，收益低于已覆盖的四类指标（YAGNI） |
