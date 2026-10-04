# 第17周精读提纲：OpenTelemetry —— Trace / Span / Metric / Context

> 主题：把「Agent 上线后看不见」变成「每一次调用都能还原」
> 本周目标：读完能独立回答「一次请求在系统里经过了哪些节点、每步花了多久、烧了多少 token、失败在哪一跳」
> 阅读范围：OpenTelemetry 官方 Concepts（Signals / Traces / Metrics / Context）+ W3C Trace Context + Spring Boot Actuator & Micrometer Tracing 文档

---

## 0. 本周要建立的心智模型

读完后，必须能用自己的话回答这 7 件事：

| 概念 | 一句话定义 | 工程上为什么重要 |
|------|------------|------------------|
| Trace | 一次完整请求在分布式系统里的**全链路记录** | 定位「慢在哪一跳、错在哪一跳」的最小单位 |
| Span | Trace 里的一个**工作单元**（有开始/结束/状态/属性） | 一次 LLM 调用 = 一个 span；父子 span 构成调用树 |
| SpanContext | `traceId` + `spanId` + 采样标志 | 跨进程传播的就是它，**不带业务数据** |
| Context | 跨函数/跨线程携带当前 span 的载体 | 没有它，工具函数里就不知道「我在哪个 span 下」 |
| Metric | 可聚合的数值时间序列（counter / gauge / histogram） | 回答「总量、P99、成本趋势」，trace 回答「这一次」 |
| Propagation | 把 Context 编码进 HTTP 头传给下游 | 微服务链路能串起来的前提（W3C `traceparent`） |
| Sampling | 只保留部分 trace（如 10%） | 全量采样在高 QPS 下成本不可接受 |

**一句话区分三种信号**：

```
Trace   → 「这一次请求发生了什么」  高基数、逐条、贵
Metric  → 「整体趋势与总量如何」    低基数、聚合、便宜
Log     → 「某一刻的详细文本」      最丰富、最难聚合
```

三者靠 **traceId 互相跳转**：从 Grafana 面板（Metric）异常 → 点进某条 Trace → 跳到该 traceId 的 Log。
本周的落地重点就是把「已有 traceId 日志」和「新 OTel trace」**用同一个 ID 串起来**。

---

## 1. Trace 与 Span

### 1.1 必抓结论

- **Trace 是一棵树，不是一条线**。并行/嵌套调用都会长成分支。
- **TraceId 全局唯一（128 bit / 32 hex）**，`SpanId` 在 trace 内唯一（64 bit / 16 hex）。
  父 span 的 `spanId` 成为子 span 的 `parentSpanId` —— 父子关系的唯一依据。
- span 的**结束时间未知就不该创建**：span 是有生命周期的对象，`start → 打属性/事件 → end`。
  忘记 end（或异常路径没 end）会造成「幽灵 span」与内存泄漏。
- span 上能挂三类东西：
  | 载体 | 用途 | 例子 |
  |------|------|------|
  | Attributes | 结构化键值，**可被查询/聚合** | `gen_ai.request.model=deepseek-chat` |
  | Events | 时间点事件，带时间戳 | `exception` 事件 |
  | Status | `OK` / `ERROR` + 描述 | 失败原因分类 |
- **采样决策发生在 trace 根部并向下传递**：父不采样，子也一定不采样（`sampled=0`）。

### 1.2 工程红线

- **属性不是日志**：不要把 Prompt 正文、模型输出、患者信息塞进 span 属性 —— 属性会被索引、长期保存、
  跨团队可见，等于把敏感数据复制到第三个地方。本周只写**标识 / 模型名 / 长度 / token 数 / 状态**。
- 不要用「业务 ID 冒充 traceId」：业务 ID 会重复（同一患者多次就诊），traceId 必须每次请求唯一。
- span 粒度不是越细越好：每个 getter 都开 span 会让 trace 噪声化、开销上升。**按"有独立耗时与失败语义的工作单元"切分**。

### 1.3 本周的 span 设计（自检：为什么是这四层）

| span | 回答什么问题 | 边界判定依据 |
|------|--------------|--------------|
| `http.server`（自动） | 请求整体多慢？状态码多少？ | 一次 HTTP 往来 |
| `agent.run` | 这个 Agent 跑一次多久、用哪个模型、烧多少 token、成功还是失败？ | 一次平台调度（含 RBAC 与执行记录终态） |
| `llm.chat` | 模型调用本身多慢、真实 usage 是多少？ | 一次 `chat()` 往返 —— **成本核算的最小单位** |
| `tool.call` | 哪个工具慢/失败？ | 一次工具执行 |

> 「LLM 调用」与「Agent 执行」必须分开：一个 Agent 可能调 5 次模型（ReAct 循环），
> 只看 `agent.run` 会把「模型慢」和「工具慢」混在一起。

---

## 2. Context 与 Propagation

### 2.1 必抓结论

- Context 是**隐式传参**：`Context.current()` 沿调用栈取当前 span，无需每个方法都传 `Span` 参数。
  这也是为什么「埋点可以只改装饰器，不改业务签名」。
- **线程切换是 Context 的头号杀手**：线程池执行、`@Async`、异步 HTTP 都会丢 Context。
  必须显式传递（Java 里由 Micrometer Tracing / OTel 的 scope 包装解决）。
- W3C Trace Context 头格式（本周直接实现）：

```
traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
             │  │                                │                │
             │  │                                │                └─ flags: 01=sampled
             │  │                                └─ parent-id (16 hex)
             │  └─ trace-id (32 hex)
             └─ version (00)
```

- `tracestate` 是厂商扩展位（本周不支持，遇到只透传/忽略）。

### 2.2 与 Week 16 自研 traceId 的冲突（本周最需要想清楚的点）

| | 自研 `X-Trace-Id`（Week 16） | W3C `traceparent`（OTel） |
|--|------------------------------|---------------------------|
| 格式 | 任意字符串（UUID/`client-trace-123`） | 严格 `32hex-16hex` |
| 用途 | 日志 MDC、响应体 `traceId`、审计落库 | 跨进程传播、链路后端索引 |
| 上游 | 客户端/网关可自定义 | 支持 OTel 的组件自动注入 |

**两条错误路线**：

1. 各生成各的 → 日志一个 ID、Jaeger 另一个 ID，「可观测」变成「多处找线索」；
2. 直接改用 32hex 替换 `X-Trace-Id` → 破坏 Week 16 已固化的回显契约与全部既有测试。

**本周选择的路线**：**派生而非双轨**。OTel traceId 由 traceId 确定性换算
（合法 `traceparent` 优先采纳其 trace-id；否则 `MD5(traceId)` 取 32 hex），
既有 ID 契约不动，两个 ID 可互推。响应同时回两种头，让下游各取所需。

```
traceId「client-trace-123」  ──MD5──▶  otelTraceId「2b0f4d1e…」(32 hex)
        ▲                                        ▲
        └── 日志 / 审计 / 响应体 traceId           └── Jaeger / Tempo 里搜这个
```

**这条经验可复用**：接入标准化设施时，**先保证"新旧 ID 可互推"，再谈替换**；
一上来就换 ID 体系，代价是全量契约回归 + 历史日志断链。

---

## 3. Metric

### 3.1 必抓结论

- 三种基础类型：
  | 类型 | 语义 | 本周例子 |
  |------|------|----------|
  | Counter | 只增不减的累计量 | `llm.tokens.total`、`agent.run.count` |
  | Gauge | 可增可减的瞬时值 | 队列长度、连接池活跃数（Boot 自带） |
  | Histogram / Timer | 分布（含分位数） | `llm.call.duration`、`agent.run.duration` |
- **高基数标签是成本炸弹**：把 `executionId`、`userId`、`patientId` 当标签，会让时间序列数爆炸
  （每次都新增一条序列），监控系统直接被打垮。
  本周允许的标签只有低基数的：`agent.key` / `agent.type` / `model` / `status` / `tool.name` / `token.type`。
  **需要按 executionId 追查时用 Trace，不要用 Metric。**
- Timer 自带 `count` / `sum` / `max`，不必再单独记一次 count（但语义清晰的 count counter 更易读，本周两者都留）。
- 命名与单位：Micrometer 用 `.` 分隔（`llm.call.duration`），导出 Prometheus 时自动转下划线并加
  `_seconds` / `_total` 后缀；**单位写进名字**（`duration` 默认秒）。

### 3.2 AI 应用的黄金指标（本周落地）

```
成本：llm.tokens.total{token_type=prompt|completion}   ← 直接换算钱
用量：llm.call.count、agent.run.count
性能：llm.call.duration、agent.run.duration（P95/P99）
质量：agent.run.count{execution_status=FAILED} 比例、guardrail.blocked.count
```

> 注意：**Token 数必须取上游返回的真实 usage**，不能自己按字符估算 —— 估算值在计费与容量规划上不可用。
> 上游未返回 usage 时（`unknown`），**不要记 0**，跳过即可，否则平均值会被稀释成假数据。

---

## 4. Spring Boot 接入路径（为什么选 Micrometer 而非直接 OTel SDK）

| 方案 | 做法 | 优点 | 缺点 |
|------|------|------|------|
| javaagent 自动探针 | 启动加 `-javaagent:opentelemetry-javaagent.jar` | 零代码，HTTP/JDBC/Redis 自动有 span | **打不出业务语义 span**（`agent.run` 它不认识）；多一份启动参数与版本耦合 |
| 直接用 OTel SDK | 手写 `TracerProvider` / `SpanProcessor` / `Exporter` | 最贴近标准 | 要自己实现采样、导出线程、Spring 集成；Boot 的 HTTP 自动埋点用不上 |
| **Micrometer Tracing + OTel bridge**（本周选） | 依赖 `micrometer-tracing-bridge-otel`，Boot 自动装配 `Tracer` | 门面统一、Boot 自动埋 HTTP/JDBC、`MeterRegistry` 已是既有指标门面、换后端只换 bridge | 多一层抽象（但对本周是优点：领域层可以完全不认识 OTel） |

**Micrometer 的定位**：它不产生遥测数据，而是**门面（facade）**——
`Tracer` 门面背后可以接 OTel、Brave、OpenZipkin；`MeterRegistry` 背后可以接 Prometheus、Datadog。
所以「先用 Micrometer 门面，再决定后端」是风险最低的接法。

### 4.1 关键配置项与语义

| 配置 | 语义 | 本周取值与理由 |
|------|------|----------------|
| `management.tracing.enabled` | 是否装配 `Tracer` | `true`；关掉则所有 span 静默丢弃（降级开关） |
| `management.tracing.sampling.probability` | 采样率 | 演示 1.0；生产应降到 0.1 量级（高 QPS + LLM 单次很贵） |
| `management.otlp.tracing.endpoint` | OTLP/HTTP 导出地址 | `http://localhost:4318/v1/traces` |
| `management.otlp.tracing.export.enabled` | 是否真的外发 | 单测关掉，避免测试期打网络 |
| `management.endpoints.web.exposure.include` | 暴露哪些 Actuator 端点 | `health,info,metrics,prometheus` |

### 4.2 优雅降级（生产必答题）

collector 挂了会怎样？**Batching + 异步导出 + 超时**，业务不受影响。

但要留意一个实测细节（Week 17 验收踩到）：**导出失败不是"静默 WARN"** ——
OTel SDK 会在自己的 logger 上打 **ERROR + 完整堆栈**（`io.opentelemetry.exporter.internal.http.HttpExporter`），
且日志里的 traceId 是空的（批量导出跑在 SDK 线程池线程上，没有请求上下文）。
所以「优雅降级」的准确含义是：**不影响业务，但会污染日志、并可能触发把 ERROR 当事故的告警**。
两个务实做法：本地没 collector 时直接关掉导出（`OTEL_EXPORT_ENABLED=false`，只影响 span 外发，
不影响埋点/日志关联/指标），或部署后用日志级别配置把这类第三方 ERROR 收进既定策略。
理由与 Week 16 审计一致：**可观测设施不是业务单点**。反过来，如果 trace 导出是同步阻塞的，
collector 抖动就会直接变成业务接口超时 —— 这是接入可观测时最常见的自伤。

---

## 5. LLM / Agent 可观测的特殊之处

### 5.1 与普通微服务的差异

| 维度 | 普通服务 | LLM/Agent 服务 |
|------|----------|----------------|
| 单次耗时 | 毫秒级 | **秒级到分钟级**，超时设置完全不同 |
| 单次成本 | 近似为 0 | **每次调用都花钱**（按 token 计费） |
| 失败形态 | 异常/超时 | 异常/超时 **+ 内容不合规 + 工具死循环** |
| 一次请求内的调用数 | 通常固定 | **不确定**（ReAct 循环，1~N 次 LLM + M 次工具） |
| 敏感数据 | 常规 PII | Prompt/上下文里可能带患者隐私，**落 span 风险极高** |

### 5.2 由此推得的埋点原则

1. **token 与成本必须是 span 属性 + 指标双写**：span 里看单次，指标里看趋势；
2. **必须记迭代次数 / 工具调用数**：Agent 的成本失控通常是「循环次数膨胀」，这是普通服务没有的故障模式；
3. **span 属性做白名单**：LLM 系统里"顺手把 prompt 记下来"是最常见的隐私事故；
4. **长耗时 span 要配超时与取消**：否则 trace 里会出现悬挂几十分钟的 span；
5. 语义约定优先用 **OpenTelemetry GenAI semantic conventions**（`gen_ai.system` / `gen_ai.request.model` /
   `gen_ai.usage.prompt_tokens` / `gen_ai.usage.completion_tokens`），命名跟着社区走，
   后面接 Langfuse（第 18 周）等专业 LLM 观测平台时字段能直接对上。

### 5.3 与第 18 周 Langfuse 的分工（先想清楚，避免重复建设）

| | OpenTelemetry（本周） | Langfuse（下周） |
|--|----------------------|------------------|
| 定位 | **通用**分布式追踪标准 | **LLM 专用**观测与实验平台 |
| 强项 | 服务拓扑、DB/Redis/HTTP 全链路、生态广 | Prompt 版本管理、数据集与评分、成本分析看板 |
| 数据 | span 树 + 指标 | trace + generation + score + prompt |
| 关系 | traceId 互相关联即可，不重复造 | 消费同一 traceId，做 LLM 视角的深加工 |

> 结论：本周**不要**为了"LLM 友好"去自造一套 LLM 追踪模型；把标准 OTel 打扎实，
> 下周 Langfuse 通过 traceId 关联即是最省力路径。

---

## 6. 本周自检题（能答出即达标）

1. 为什么「Agent 执行」和「LLM 调用」要拆成两层 span？只留一层会看不清什么？
2. 忘记 `span.end()` 会发生什么？本周代码里靠什么机制保证一定结束？
3. `otelTraceId` 为什么由 `traceId` 派生，而不是各生成一套？
4. 为什么 `executionId` / `patientId` 不能当指标标签？那要按 executionId 追查怎么办？
5. 为什么 token 指标要跳过 `unknown`（–1）而不是记 0？
6. collector 不可用时，本系统会发生什么？为什么这样设计？
7. 采样率设为 0.1 时，为什么"父 span 不采样则子 span 必然不采样"很重要？
8. 一次 ReAct 循环 5 轮、每轮 1 次 LLM + 2 次工具，最终 trace 里应该有几个 `llm.chat` span？几个 `tool.call`？

---

## 7. 落地映射（读完对照代码）

| 概念 | 本周落地位置 |
|------|--------------|
| Context / Propagation | `TraceIdFilter`（解析/回写 `traceparent`）、`TraceIds`（派生与格式化） |
| Trace / Span | `AgentObservabilityPort` + `SpanScope`；`MicrometerObservabilityAdapter` |
| span 树 | `HttpServerSpanFilter`（server）→ `PlatformExecutionUseCase`（agent.run）→ `ObservableChatModelAdapter`（llm.chat）/ `ObservableToolPort`（tool.call） |
| Metric | `MicrometerObservabilityAdapter.recordTokenUsage` + `/actuator/prometheus` |
| 采样与导出 | `management.tracing.sampling.probability` + `management.otlp.tracing.*` |
| 隐私红线 | span 属性白名单（只标识/长度/token/状态） |
