# API：Week 20 — 完整监控体系（统一自检 + 指标出口）

> 平台端口 **8084**，网关 **8080**。响应信封见 `docs/code-implementation-spec.md` 5.6。
> 本周只新增 **1 个业务自检接口** 与 **1 个 Actuator 指标出口（网关）**；
> 既有接口路径、响应结构、状态码**零改动**。

---

## 1. 变更总览

| # | 接口 / 端点 | 类型 | 说明 |
|---|-------------|------|------|
| 1 | `GET /api/v1/platform/observability/monitoring-overview` | 新增 | 六组件体检 + 跨后端关联；仅管理员 |
| 2 | `GET /actuator/prometheus`（网关 8080） | 新增（Actuator） | 网关指标出口（第 19 周只有 `health,info`） |
| 3 | `GET /actuator/prometheus`（平台 8084） | 不变 | 第 17 周已交付，本周增加公共标签与直方图桶 |
| 4 | Prometheus `:19090` | 新增（运维） | 抓取 / 查询 / 告警规则 API |
| 5 | Grafana `:3001` | 新增（运维） | 两张 provisioning 仪表盘 |

---

## 2. `GET /api/v1/platform/observability/monitoring-overview`

**鉴权**：需要登录 **且为管理员**（`admin` 角色）。端点会暴露内部地址与组件开关，故不对普通用户开放。

### 2.1 成功响应（200）

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "generatedAt": "2026-10-18T14:40:00Z",
    "allHealthy": true,
    "components": [
      {
        "key": "otel",
        "name": "OpenTelemetry",
        "enabled": true,
        "status": "UP",
        "endpoint": "http://localhost:4318/v1/traces",
        "detail": "OTLP/HTTP；采样率 1.0",
        "consoleUrl": ""
      },
      {
        "key": "prometheus",
        "name": "Prometheus",
        "enabled": true,
        "status": "UP",
        "endpoint": "http://127.0.0.1:19090",
        "detail": "就绪；抓取 /actuator/prometheus（15s）",
        "consoleUrl": "http://127.0.0.1:19090/graph"
      },
      {
        "key": "grafana",
        "name": "Grafana",
        "enabled": true,
        "status": "UP",
        "endpoint": "http://127.0.0.1:3001",
        "detail": "仪表盘 aicode-platform-overview",
        "consoleUrl": "http://127.0.0.1:3001/d/aicode-platform-overview"
      },
      {
        "key": "skywalking",
        "name": "SkyWalking",
        "enabled": false,
        "status": "DISABLED",
        "endpoint": "",
        "detail": "observability.skywalking.enabled=false（HTTP/JDBC/Redis/JVM 由 Java Agent 提供）",
        "consoleUrl": "http://127.0.0.1:8088"
      },
      {
        "key": "langfuse",
        "name": "Langfuse",
        "enabled": false,
        "status": "DISABLED",
        "endpoint": "http://localhost:3000",
        "detail": "langfuse.enabled=false",
        "consoleUrl": ""
      },
      {
        "key": "gateway-metrics",
        "name": "Gateway Metrics",
        "enabled": true,
        "status": "UP",
        "endpoint": "http://127.0.0.1:8080/actuator/prometheus",
        "detail": "网关 /actuator/prometheus 可抓取",
        "consoleUrl": ""
      }
    ],
    "correlation": {
      "traceId": "week20-e2e-70345",
      "otelTraceId": "70d786370b9cf81c8071262aeab0479f",
      "correlationTag": "aicode.trace_id",
      "langfuseTraceUrl": "",
      "grafanaDashboardUrl": "http://127.0.0.1:3001/d/aicode-platform-overview",
      "skywalkingUiUrl": "http://127.0.0.1:8088",
      "queryHints": {
        "logs": "traceId=week20-e2e-70345",
        "audit": "audit_log.trace_id=week20-e2e-70345",
        "skywalking": "aicode.trace_id = week20-e2e-70345",
        "langfuse": "traceId = 70d786370b9cf81c8071262aeab0479f",
        "prometheus": "指标无 trace 维度，按 application / agent_key / gen_ai_request_model 聚合"
      }
    }
  },
  "traceId": "week20-e2e-70345"
}
```

### 2.2 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `generatedAt` | string | 生成时间（ISO-8601 UTC） |
| `allHealthy` | boolean | 所有**已启用**组件均为 `UP` 才为 true；没有组件时为 false |
| `components[].key` | string | 稳定标识：`otel` / `prometheus` / `grafana` / `skywalking` / `langfuse` / `gateway-metrics`（顺序固定） |
| `components[].enabled` | boolean | 是否启用；false 时 `status` 必为 `DISABLED` |
| `components[].status` | string | `UP` / `DOWN` / `DISABLED` / `UNKNOWN` |
| `components[].endpoint` | string | 脱敏后的端点（已剥离 `user:pass@`），未配置为空串 |
| `components[].detail` | string | 一行说明；失败时为异常短名（如 `ConnectException` / `timeout(800ms)`），不含堆栈 |
| `components[].consoleUrl` | string | 控制台深链；没有时为空串 |
| `correlation.traceId` | string | 业务 traceId（= 响应头 `X-Trace-Id` = 响应体 `traceId`） |
| `correlation.otelTraceId` | string | OTel / Langfuse 的 W3C traceId |
| `correlation.correlationTag` | string | SkyWalking span tag 名（`aicode.trace_id`） |
| `correlation.langfuseTraceUrl` | string | Langfuse trace 深链；未启用/未配 project id 时为空串 |
| `correlation.grafanaDashboardUrl` | string | 默认仪表盘入口；Grafana 地址未配置时为空串 |
| `correlation.skywalkingUiUrl` | string | SkyWalking UI 入口；未配置时为空串 |
| `correlation.queryHints` | object | 五个系统的检索提示（见上例） |

### 2.3 状态语义（契约）

| 状态 | 含义 | 是否参与 `allHealthy` |
|------|------|------------------------|
| `UP` | 启用且探测可达（2xx/3xx） | 是，必须为 UP |
| `DOWN` | 启用但探测失败，或 SkyWalking 配置开了但 Agent 未挂载 | 是，出现即 false |
| `DISABLED` | 未启用 / 地址为空 | 否 |
| `UNKNOWN` | 已启用但缺少判定依据（当前实现不会产生，保留给未来探针） | 是（不等于 UP） |

**探测上限**：单次 800ms（`monitoring.probe.timeout-ms`），结果缓存 30s
（`monitoring.probe.cache-ttl-seconds`）；缓存 TTL 设为 0 可关闭缓存。

### 2.4 失败响应

| 场景 | HTTP | code |
|------|------|------|
| 未登录 | 401 | `UNAUTHORIZED` |
| 已登录但非管理员 | 403 | `FORBIDDEN` |
| 未知路径 | 404 | `NOT_FOUND` |

失败时 `data` 为 `null`，必带 `traceId`（Week 16 规范）。

### 2.5 自检用法

```bash
# 1) 登录拿平台 token
TOKEN=$(curl -s -X POST http://localhost:8084/api/v1/platform/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | jq -r .data.token)

# 2) 调概览
curl -s http://localhost:8084/api/v1/platform/observability/monitoring-overview \
  -H "Authorization: Bearer $TOKEN" -H 'X-Trace-Id: week20-overview' | jq
```

---

## 3. 指标出口

### 3.1 平台 `GET http://localhost:8084/actuator/prometheus`

第 17 周已交付。本周新增：

- 公共标签 `application="ai-code-platform"`（`management.metrics.tags.application`）；
- 直方图桶：`agent_run_duration_seconds_bucket` / `llm_call_duration_seconds_bucket` /
  `http_server_requests_seconds_bucket`。

关键指标（导出名，详见 `docs/architecture/week-20-architecture.md` 第 4 节）：

```text
agent_run_count_total{application="ai-code-platform",agent_key="medical-assistant",execution_status="COMPLETED"}
agent_run_duration_seconds_count / _sum / _bucket
llm_call_count_total{gen_ai_request_model="deepseek-v4-pro",llm_outcome="SUCCESS"}
llm_call_duration_seconds_count / _sum / _bucket
llm_tokens_total{token_type="prompt|completion"}
llm_cost_usd_total{gen_ai_request_model="deepseek-v4-pro"}
tool_call_count_total{tool_name="PatientLookupTool",tool_outcome="SUCCESS"}
http_server_requests_seconds_count / _bucket
jvm_memory_used_bytes{area="heap|nonheap"}
```

### 3.2 网关 `GET http://localhost:8080/actuator/prometheus`（本周新增）

除 Actuator 自带 HTTP/JVM 指标与 Feign 的 `http_client_requests_*` 外，新增：

| 导出名 | 标签 | 含义 |
|--------|------|------|
| `gateway_session_login_count_total` | `outcome=SUCCESS\|FAILURE` | 登录结果 |
| `gateway_catalog_lookup_count_total` | `cache_hit=true\|false` | 目录查询是否命中缓存 |
| `gateway_agent_run_count_total` | `outcome=SUCCESS\|FAILURE` | 经网关的 Agent 执行次数 |
| `gateway_agent_run_duration_seconds_*` | `outcome` | 经网关的执行耗时（含 `_bucket`） |
| `gateway_upstream_error_count_total` | `error_code` | 上游失败（4xx 透传 / 5xx 收敛后的错误码） |

> 网关的 `http_client_requests_*` 来自 `feign-micrometer`（Week 19 依赖）；
> 若该面板为空，先确认网关确实发生过 Feign 调用（目录命中缓存时不会回源）。

### 3.3 抓取与查询

| 端点 | 说明 |
|------|------|
| `http://127.0.0.1:19090/-/ready` | Prometheus 就绪 |
| `http://127.0.0.1:19090/api/v1/targets` | 抓取目标与健康状态 |
| `http://127.0.0.1:19090/api/v1/rules` | 12 条告警规则 |
| `http://127.0.0.1:19090/api/v1/query?query=...` | PromQL 查询 |
| `http://127.0.0.1:19090/alerts` | firing / pending 告警 UI |
| `http://127.0.0.1:3001/api/health` | Grafana 健康 |
| `http://127.0.0.1:3001/d/aicode-platform-overview` | Platform 仪表盘 |
| `http://127.0.0.1:3001/d/aicode-gateway-overview` | Gateway 仪表盘 |

以上端口只监听云端回环，需先开 `tools\tunnel.ps1`（本地 `19090` / `3001` 转发）。

---

## 4. 契约测试索引

| 契约 | 测试 |
|------|------|
| 概览 HTTP 协议（401 / 403 / 200 / 信封 / traceId 回显 / 无密钥） | `PlatformMonitoringHttpTest`（3 例） |
| 六组件顺序与状态语义（DISABLED 不参与、DOWN 拉低整体、SkyWalking 无 Agent=DOWN） | `PlatformMonitoringUseCaseTest`（8 例） |
| URL 凭据剥离、关联块字段、非管理员 403 | 同上 |
| 探针（2xx/3xx/5xx/连接失败/非法 URL/缓存/TTL） | `HttpMonitoringProbeTest`（7 例） |
| 配置默认值与归一 | `MonitoringPropertiesTest`（5 例） |
| 指标名与标签（真实 Prometheus 注册表抓文本） | `MonitoringMetricsContractTest`（4 例） |
| DTO 字段名与无密钥 | `MonitoringOverviewResponseTest`（4 例） |
| 网关四个自有指标（名字/标签/计数/归一） | `MicrometerGatewayMetricsAdapterTest`（5 例） |
| 网关用例埋点分支（缓存命中/回源、成功/失败/上游错误码） | `GatewayAgentUseCaseMetricsTest`（5 例）、`GatewaySessionUseCaseMetricsTest`（3 例） |
| 网关 Prometheus 出口（200 + `jvm_memory_used_bytes` + `application` 标签 + 登录计数） | `GatewayPrometheusEndpointTest`（2 例） |

---

## 5. 错误码索引

本周**不新增**错误码：越权复用既有 `FORBIDDEN`，未登录复用 `UNAUTHORIZED`，
未知路径复用 `NOT_FOUND`。网关指标不影响任何业务错误映射。
