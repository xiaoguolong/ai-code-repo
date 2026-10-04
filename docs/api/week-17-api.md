# API: Week 17 — OpenTelemetry 可观测接口

> 本周**不新增业务接口**，只新增 1 个可观测自检接口 + 2 个 Actuator 端点。
> 既有接口（登录、Agent Run、审计、Eval）**路径与响应结构零改动**，仅在响应头增加标准链路头。

---

## 1. 变更总览

| # | 接口 | 类型 | 说明 |
|---|------|------|------|
| 1 | `GET /api/v1/platform/observability/trace-context` | 新增 | 返回当前请求的链路上下文，用于自检与「日志 ID ↔ 链路 ID」对照 |
| 2 | `GET /actuator/prometheus` | 新增（Actuator） | Prometheus 文本格式指标出口 |
| 3 | `GET /actuator/health` | 新增（Actuator） | 健康检查 |
| 4 | 所有既有接口 | 响应头变更 | 新增 `traceparent`；`X-Trace-Id` 行为不变 |

---

## 2. 链路头约定（所有接口生效）

### 2.1 请求头

| 头 | 必需 | 说明 |
|----|------|------|
| `X-Trace-Id` | 否 | 自研关联键，**原样回显**（Week 16 契约不变）。长度 > 64 截断 |
| `traceparent` | 否 | W3C 标准，格式 `00-<32hex trace-id>-<16hex parent-id>-<2hex flags>`。合法时**其 trace-id 被采纳**为本次链路的 traceId（真远程父） |

优先级：`traceparent`（合法） > `X-Trace-Id` > 新生成。

### 2.2 响应头

| 头 | 说明 |
|----|------|
| `X-Trace-Id` | 与响应体 `traceId` 一致（Week 16 契约） |
| `traceparent` | 本次 span 的 W3C 上下文，`trace-id` 与 OTel 链路一致，供下游/网关续接 |

### 2.3 三种输入的解析结果

| 请求 | 响应头 `X-Trace-Id` | OTel traceId（= 响应 `traceparent` 第 2 段 / 响应体 `traceId`） |
|------|--------------------|--------------------------------------------------------------|
| `traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01` | `4bf92f3577b34da6a3ce929d0e0e4736` | `4bf92f3577b34da6a3ce929d0e0e4736`（采纳上游） |
| `X-Trace-Id: week17-demo-trace` | `week17-demo-trace`（原样回显） | `MD5("week17-demo-trace")` = `ab475d15eddb104d282fd1cfde6b941f` |
| 都不传 | 新生成 32 位 hex | 与 `X-Trace-Id` 相同 |

> 设计要点：**派生而非双轨**。OTel traceId 由 traceId 确定性换算，任何一方都能推出另一方，
> 不会出现「日志一个 ID、Jaeger 另一个 ID」。换算规则与 `parseTraceparent` 均有单测。

**实测行为说明（真实服务验证）**：请求进入后，Spring Boot 的 MDC 关联装饰器会把 **span 自己的
traceId** 写进 MDC，因此：

- 响应头 `X-Trace-Id` = 客户端原始值（Week 16 契约，用于对账客户端串联）；
- 响应体 `traceId`、服务端日志 `[traceId]`、审计 `audit_log.trace_id`、`traceparent` 第 2 段
  = **同一条 OTel traceId**（可直接拿去链路后端检索）。

两套 ID 由 `MD5` 关系互推，因此「响应头里的客户端 ID」与「链路后端里的 traceId」不会混淆。

---

## 3. `GET /api/v1/platform/observability/trace-context`

返回当前请求的链路上下文（**最简权限：登录用户即可**）。

### 3.1 成功响应

```http
GET /api/v1/platform/observability/trace-context HTTP/1.1
X-Trace-Id: client-trace-123
```

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "traceId": "client-trace-123",
    "otelTraceId": "2b0f4d1e3a7be4c9e5d6f8a0b1c2d3e4",
    "spanId": "9f1c2ab34de56789",
    "traceparent": "00-2b0f4d1e3a7be4c9e5d6f8a0b1c2d3e4-9f1c2ab34de56789-01",
    "sampled": true,
    "observabilityEnabled": true
  },
  "traceId": "client-trace-123"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `traceId` | string | 自研关联键，与响应头 `X-Trace-Id`、响应体 `traceId` 一致 |
| `otelTraceId` | string | 32 位小写 hex，OTel 链路 ID；**与 `traceparent` 第 2 段一致** |
| `spanId` | string | 16 位小写 hex，当前 server span ID |
| `traceparent` | string | 完整 W3C 头值，可直接复制到下游请求 |
| `sampled` | boolean | 当前 span 是否被采样（未采样时 `spanId` 仍返回，导出侧丢弃） |
| `observabilityEnabled` | boolean | `observability.enabled` 开关状态；`false` 时 `otelTraceId`/`spanId` 为空串、`traceparent` 为空 |

### 3.2 失败响应

| 场景 | HTTP | code |
|------|------|------|
| 未登录 | 401 | `UNAUTHORIZED` |
| 约定：未知路径 | 404 | `NOT_FOUND` |

失败响应 `data` 为 `null`，必带 `traceId`（Week 16 规范 5.6）。

### 3.3 自检用法

```bash
curl -s -D - "http://localhost:8084/api/v1/platform/observability/trace-context" \
  -H "satoken: <token>" -H "X-Trace-Id: client-trace-123"
```

断言链：

1. 响应头 `X-Trace-Id` = `client-trace-123`（Week 16 契约：原样回显）；
2. 响应体 `data.otelTraceId` = `MD5("client-trace-123")`（32 位 hex，确定性派生）；
3. 响应体 `data.traceparent` 第 2 段 = `data.otelTraceId`，第 3 段 = `data.spanId`；
4. 响应体 `body.traceId` = `data.traceId` = `data.otelTraceId`（**注意不是** `client-trace-123`
   —— 服务端日志/审计/响应体统一记的是链路 traceId；客户端的原始值只保留在响应头 `X-Trace-Id` 里）；
5. 服务端日志 `[traceId]` 与第 2 步的值一致，可直接拿去链路后端检索。

### 3.4 Postman 集合（推荐的自检方式）

`docs/postman/week-17.postman_collection.json` 共 9 个请求，**按顺序跑**即可完成全部验收
（`token` 变量由请求 1 自动写入）：

| # | 请求 | 作用 |
|---|------|------|
| 1 | 登录（admin） | 取 `satoken` |
| 2 | 自检（带 `X-Trace-Id`） | 验证「客户端 ID ↔ 链路 ID」的派生关系（6 条断言） |
| 3 | 自检（带 W3C `traceparent`） | 验证上游 trace-id 被采纳（3 条断言） |
| 4 | 未登录访问 | 验证 401 信封与 `traceId`（4 条断言） |
| 5 | `actuator/health` | 探活（1 条断言） |
| 6 | `actuator/prometheus` | 验证指标端点在、格式对（3 条断言） |
| 7 | Agent Run | 触发真实链路与指标产生（4 条断言） |
| 8 | 执行记录 | 验证真实 token 用量（3 条断言） |
| 9 | **指标回查** | 验证请求 7 产生的自定义指标已出现（12 条断言） |

> **顺序陷阱**：请求 6 在请求 7 之前执行，那时 `agent_run_count_total` 还是 0 行 —— 这是正常的，
> 不是漏埋点。自定义业务指标由 Agent 执行时才产生，所以其存在性断言放在最后的请求 9。
> 若不按顺序单独点请求 9，会因没有跑过 Agent 而失败。

---

## 4. `GET /actuator/prometheus`

Prometheus 文本格式。本周新增的指标（Micrometer 命名 → Prometheus 命名）：

| Micrometer | Prometheus | 类型 | 标签 |
|------------|-----------|------|------|
| `agent.run.count` | `agent_run_count_total` | counter | `agent_key` `execution_status`（`agent_type` 仅作为 span 属性，不参与指标标签） |
| `agent.run.duration` | `agent_run_duration_seconds_*` | timer | `agent_key` `execution_status` |
| `llm.call.count` | `llm_call_count_total` | counter | `gen_ai_request_model` `llm_outcome` |
| `llm.call.duration` | `llm_call_duration_seconds_*` | timer | `gen_ai_request_model` `llm_outcome` |
| `llm.tokens.total` | `llm_tokens_total` | counter | `gen_ai_request_model` `token_type` |
| `tool.call.count` | `tool_call_count_total` | counter | `tool_name` `tool_outcome` |

同时暴露 Spring Boot 自带指标（`http_server_requests_seconds_*`、`jvm_*`、`hikaricp_*`、`process_*`）。

```bash
curl -s http://localhost:8084/actuator/prometheus | grep -E '^(agent_run_count_total|llm_tokens_total)'
```

**真实环境实测输出**（第 17 周验收，真实 LLM 调用一次医疗助手 Agent；`/actuator/prometheus` 原文摘录）：

```text
# HELP llm_tokens_total
# TYPE llm_tokens_total counter
llm_tokens_total{gen_ai_request_model="deepseek-v4-pro",token_type="prompt"} 700.0
llm_tokens_total{gen_ai_request_model="deepseek-v4-pro",token_type="completion"} 3958.0

llm_call_count_total{gen_ai_request_model="deepseek-v4-pro",llm_outcome="SUCCESS"} 2.0
llm_call_duration_seconds_count{gen_ai_request_model="deepseek-v4-pro",llm_outcome="SUCCESS"} 2
llm_call_duration_seconds_sum{gen_ai_request_model="deepseek-v4-pro",llm_outcome="SUCCESS"} 42.782
llm_call_duration_seconds_max{gen_ai_request_model="deepseek-v4-pro",llm_outcome="SUCCESS"} 22.552

agent_run_count_total{agent_key="medical-assistant",execution_status="COMPLETED"} 1.0
agent_run_duration_seconds_count{agent_key="medical-assistant",execution_status="COMPLETED"} 1

tool_call_count_total{tool_name="HealthMetricTool",tool_outcome="SUCCESS"} 1.0
tool_call_count_total{tool_name="PatientLookupTool",tool_outcome="SUCCESS"} 1.0

http_server_requests_seconds_count{method="POST",status="200",uri="/api/v1/platform/agents/{agentKey}/runs"} 1
```

也可用 Actuator 的 JSON 视图逐项核对（带低基数标签值）：

```text
GET /actuator/metrics/agent.run.count
{"name":"agent.run.count","measurements":[{"statistic":"COUNT","value":1.0}],
 "availableTags":[{"tag":"execution.status","values":["COMPLETED"]},
                  {"tag":"agent.key","values":["medical-assistant"]}]}
```

> 命名提醒：Micrometer 的 counter 在 Prometheus 出口**不会**变成 `llm_tokens_total_total` ——
> `llm.tokens.total` 转成 `llm_tokens_total` 即可（`.total` 已含 `_total` 语义）。
> 另外应用里还有一条 Spring AI 自带的 `gen_ai_client_token_usage_total`（由框架自动埋点），
> 与我们的 `llm_tokens_total` 并行存在，前者不区分 token 类型，看成本请用后者。

`token.type` 取值：`prompt` / `completion`。上游未返回 usage 时 `TokenUsage.unknown()` 全为 0，此时**不记录**，
避免把未知值混入成本统计（记 0 会稀释均值）。

---

## 5. OTLP 导出

| 配置 | 默认 | 说明 |
|------|------|------|
| `management.otlp.tracing.endpoint` | `http://localhost:4318/v1/traces` | OTLP/HTTP 端点（Jaeger / OTel Collector / Tempo） |
| `management.otlp.tracing.export.enabled` | `true` | 关掉即只采样不导出；**本地没起 collector 时建议关掉**（见 5.1） |
| `management.tracing.sampling.probability` | `1.0` | 采样率 |
| `observability.enabled` | `true` | 自定义 span/指标埋点开关（与导出无关，关掉导出不影响它） |

本地验证（Jaeger all-in-one，OTLP 4318 已开）：

```bash
docker run --rm -p 16686:16686 -p 4318:4318 jaegertracing/all-in-one:latest
```

服务启动后访问 `http://localhost:16686`，按 `service.name = spring-ai-alibaba-agent` 查询，
应看到 `agent.run` → `llm.chat` / `tool.call` 的父子 span 树。

### 5.1 collector 不可达时会发生什么（实测）

**先回答最常问的**：`java.net.ConnectException: Failed to connect to localhost/[0:0:0:0:0:0:0:1]:4318`
是**预期行为**，不是配置错误 —— 只要 4318 上没有 collector 就会打印。它由 **OTel SDK 自己的 logger**
（`io.opentelemetry.exporter.internal.http.HttpExporter`）输出，不经过本项目的日志规范。

第 17 周实测结论（23 个业务请求 + 无 collector）：

| 观察项 | 实测结果 | 结论 |
|--------|----------|------|
| 业务状态码 | 23/23 全部 **200** | ✅ 隔离成立：可观测设施不是业务单点 |
| 应用自身错误日志 | **0 条** | ✅ 导出失败不会污染业务日志 |
| 启动阶段 | **0 次**报错（启动不打网络） | ✅ 启动不受影响 |
| 报错日志级别 | **ERROR**（不是 WARN） | ⚠️ 与「审计只打 WARN」的约定不同，见下 |
| 报错频率 | **按批次 1 条**，不按请求；日志随重试增长 | ⚠️ 持续不可达时会累积 ERROR + 堆栈 |
| 报错里的 `traceId` | 空（`ERROR []`） | 属正常：批量导出在线程池线程上执行，无请求上下文 |

**为什么级别是 ERROR**：ERROR 来自 OTel SDK 的 `HttpExporter`，本项目并未（也不应）覆盖第三方组件的日志级别。
它的实际运维影响是：**如果告警规则把 ERROR 当成事故，collector 没起就会持续误报**。

### 5.2 本地没 collector 时的推荐做法

**关闭导出**（推荐，实测零报错且功能不受影响）：

```powershell
$env:OTEL_EXPORT_ENABLED="false"     # 只采样不导出
```

实测（`OTEL_EXPORT_ENABLED=false`，无 collector）：

```text
登录 HTTP = 200
X-Trace-Id: off-test
traceparent: 00-b42b069501c668c82c80e3d41db1f68a-0b275432c9ad41c0-01
ConnectException 次数: 0
'Failed to export' 次数: 0
observability.enabled 仍为 true → span 照常产生、MDC/日志 traceId 照常、/actuator/prometheus 照常
```

也就是说：**关掉导出只影响「span 有没有送到链路后端」**，日志关联、审计 traceId、Prometheus
指标全部不受影响。想真正看链路时再起 collector 并打开导出即可。

若必须让导出常开又不想被 ERROR 打扰，可选择：

| 方案 | 做法 | 代价 |
|------|------|------|
| 收起第三方日志（务实） | `logging.level.io.opentelemetry.exporter: OFF`（或 `ERROR` 时只留一行） | 会连真实的导出失败一起静音 |
| 发到临时 collector | 起 Jaeger all-in-one（见上） | 多一个容器 |
| 生产接真实 collector | `OTEL_EXPORTER_OTLP_ENDPOINT` 指向 OTel Collector / Tempo | 属第 20 周范围 |

> 结论：**业务上"正常"，运维上"不干净"**。本地开发建议关掉导出；生产环境按第 20 周计划部署 collector 后，
> 再统一决定这类第三方 ERROR 是否进告警。

---

## 6. 隐私与安全

| 规则 | 说明 |
|------|------|
| span 属性白名单 | 只允许标识、模型名、长度、token 数、状态；**禁止 Prompt 正文、模型输出正文、患者姓名/身份证/手机号** |
| 已有脱敏 | Week 14 `PiiMasker` 仍在输出侧生效，本周不放松 |
| `traceparent` | 只含随机 ID，不含任何业务信息，可安全透出 |
| Actuator 端点 | 生产环境应经网关鉴权或只在内网暴露；本周保持 Spring Boot 默认（本地 8084 直连），网关统一鉴权留第 19 周 |
| 日志 | 仍遵循 Week 16 规范：不打印密码 / token / API Key |

---

## 7. 兼容性说明（不回退 Week 16 契约）

| 契约 | Week 16 | Week 17 |
|------|---------|---------|
| 响应信封 | `{code,message,data,traceId}` | **不变** |
| `X-Trace-Id` 回显 | 原样回显客户端值 | **不变**（`ApiStandardsContractTest.incomingTraceIdIsPropagatedInsteadOfRegenerated` 仍绿） |
| 响应头 / 响应体 traceId 一致 | 一致 | **不变** |
| 审计 `trace_id` | 取自 MDC | **不变**（MDC 仍写 traceId，仅新增 OTel span 包裹） |
| 错误码 | `ApiErrorCode` 枚举 | **不变** |
