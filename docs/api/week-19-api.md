# API：Week 19 — 网关接入 API（agent-gateway）+ 平台自检扩展

> 网关默认端口 **8080**；平台仍为 **8084**。
> 仓库统一响应信封见 `docs/code-implementation-spec.md` 5.6；网关是**独立进程**，
> 但信封形状**逐字段相同**（`code` / `message` / `data` / `traceId`），由契约测试锁死。

---

## 1. 鉴权与链路约定（所有接口共用）

| 项 | 约定 |
|----|------|
| 认证 | `Authorization: Bearer <token>`；`<token>` 是**平台 token**（网关登录接口返回） |
| 兼容写法 | `Authorization: <token>`（无 `Bearer` 前缀，网关同样接受） |
| 链路 ID | 请求头 `X-Trace-Id`（可选）；响应头 **原样回显**；缺失时服务端生成 32 位 hex |
| W3C 上下文 | 响应同时回写 `traceparent`；请求带合法 `traceparent` 时优先采纳其 trace-id |
| 错误信封 | `{"code":"<枚举名>","message":"<中文>","data":null,"traceId":"<链路ID>"}` |
| 状态码 | 由错误码决定（400/401/403/502/500），**禁止一律 200** |

### 1.1 网关错误码（`GatewayErrorCode`）

| code | HTTP | 含义 |
|------|------|------|
| `VALIDATION_ERROR` | 400 | 入参校验失败 |
| `UNAUTHORIZED` | 401 | 未携带会话 / 会话无效或过期 |
| `FORBIDDEN` | 403 | 会话有效但无权访问（上游 403 透传） |
| `GATEWAY_UPSTREAM_ERROR` | 502 | 上游不可达、超时、返回非法报文 |
| `UPSTREAM_SERVER_ERROR` | 502 | 上游 5xx（收敛为网关侧失败） |
| `INTERNAL_ERROR` | 500 | 未预期异常（堆栈只进服务端日志） |

> 上游 4xx 时**透传**平台的业务码与状态（例如平台 401 `UNAUTHORIZED` → 网关也返回 401 `UNAUTHORIZED`），
> 便于调用方沿用平台契约；上游 5xx 一律收敛为 502，不把上游 500 透给客户端。

---

## 2. `POST /api/v1/gateway/sessions` — 登录并建立网关联会话

**鉴权**：免登录（白名单）。网关经 Feign 调平台 `POST /api/v1/platform/auth/login`。

请求：

```json
{ "username": "admin", "password": "admin123" }
```

成功 200：

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "sessionToken": "0fdb08fa-…-…",
    "tokenName": "Authorization",
    "userId": 1,
    "username": "admin",
    "roleKey": "admin",
    "expiresInSeconds": 1800
  },
  "traceId": "week19-e2e-login"
}
```

| 字段 | 说明 |
|------|------|
| `sessionToken` | **就是平台 token**（见 `docs/specs/week-19.md` 一、的设计说明）：客户端后续请求直接把它放在 `Authorization: Bearer` 里 |
| `tokenName` | 客户端应使用的头名（`Authorization`） |
| `expiresInSeconds` | 网关侧会话登记有效期（`gateway.session.ttl-seconds`，默认 1800） |

失败：

| 场景 | HTTP | code |
|------|------|------|
| 用户名/密码为空 | 400 | `VALIDATION_ERROR` |
| 平台判定凭据错误 | 401 | `UNAUTHORIZED`（透传） |
| 平台不可达 / 超时 | 502 | `GATEWAY_UPSTREAM_ERROR` |
| 平台 5xx | 502 | `UPSTREAM_SERVER_ERROR` |

> 响应体**不含**平台 token 之外的任何凭据字段（`platformToken` 字段不存在，契约测试断言）。

---

## 3. `DELETE /api/v1/gateway/sessions/current` — 登出

**鉴权**：需要会话。删除网关侧会话登记（幂等：会话不存在也返回成功）。

成功 200：`{"code":"SUCCESS","message":"OK","data":null,"traceId":"…"}`

未带会话：401 `UNAUTHORIZED`。

---

## 4. `GET /api/v1/gateway/status` — 网关接入自检

**鉴权**：需要会话。只读，**不含任何密钥或平台 token**。

成功 200：

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "application": "agent-gateway",
    "routes": {
      "platformUri": "http://localhost:8084",
      "platformProxyPath": "/api/v1/platform",
      "gatewayApiPath": "/api/v1/gateway"
    },
    "sessions": {
      "store": "redis",
      "activeSessions": 1,
      "ttlSeconds": 1800,
      "catalogCached": 1,
      "catalogTtlSeconds": 30
    },
    "tracing": {
      "traceId": "week19-e2e",
      "traceparent": "00-b983da1ead5ec3d9b3c9a7f1bc4115a1-253c31a3c4b24684-01",
      "headerName": "X-Trace-Id",
      "inboundTraceId": "week19-e2e"
    },
    "skywalkingAgent": false
  },
  "traceId": "week19-e2e"
}
```

| 字段 | 说明 |
|------|------|
| `routes.platformProxyPath` | 路由前缀的**声明**（本周不启用透传代理，见 Spec 与实现日志的取舍说明） |
| `sessions.activeSessions` | 当前 Redis 里 `gw:session:*` 的条目数（用 Lua SCAN 统计，不用 `KEYS`） |
| `skywalkingAgent` | 由**类加载探测**得出（`org.apache.skywalking.apm.toolkit.trace.TraceContext` 是否可加载），不依赖配置项 |

---

## 5. `GET /api/v1/gateway/agents` — 读平台 Agent 目录（Feign + Redis 缓存）

**鉴权**：需要会话。经 Feign 调平台 `GET /api/v1/platform/agents`。

查询参数：

| 参数 | 默认 | 说明 |
|------|------|------|
| `refresh` | `false` | `true` 时跳过缓存强制回源 |

成功 200：

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": [
    { "agentKey": "medical-assistant", "name": "医疗助手", "description": "…",
      "agentType": "MEDICAL_ASSISTANT", "enabled": true, "model": "deepseek-v4-pro",
      "maxIterations": 5, "registeredAt": "2026-…" }
  ],
  "traceId": "week19-e2e"
}
```

行为约定：

- 命中缓存 → **不再打平台**（`platformClient.listCount` 不变，单测锁死）；
- 缓存内容损坏 → 视为未命中并回源（不报错）；
- **Redis 不可用时业务仍成功**（缓存不是单点，只失去缓存）；
- 平台不可达 → 502 `GATEWAY_UPSTREAM_ERROR`（绝不拿缓存冒充答案）。

---

## 6. `POST /api/v1/gateway/agents/{agentKey}/runs` — 经网关执行 Agent

**鉴权**：需要会话。经 Feign 调平台 `POST /api/v1/platform/agents/{agentKey}/runs`，入参语义与平台一致。

请求：

```json
{ "input": { "patientId": "P001", "task": "请生成一份简短的健康评估报告" } }
```

- 也接受扁平写法 `{ "patientId": "P001" }`（缺少 `input` 键时整体作为入参）；
- 网关会把入参**归一**为平台契约的 `{"input": {...}}`，避免套成 `{"input":{"input":{…}}}`。

成功 200：

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "execution": {
      "executionId": "7f5f49f1-150f-4029-a811-14ad75e006b4",
      "userId": 1,
      "agentKey": "medical-assistant",
      "agentType": "MEDICAL_ASSISTANT",
      "status": "COMPLETED",
      "inputJson": "{\"patientId\":\"P001\",…}",
      "outputJson": "{…}",
      "model": "deepseek-v4-pro",
      "usage": { "promptTokens": 798, "completionTokens": 2675, "totalTokens": 3473 },
      "errorMessage": "",
      "startedAt": "2026-10-06T…Z",
      "finishedAt": "2026-10-06T…Z"
    }
  },
  "traceId": "week19-e2e"
}
```

> 平台执行记录字段**原样透传**（网关不解析业务字段），平台加字段不需要网关同步发布。

其他行为：

| 场景 | HTTP | code |
|------|------|------|
| `agentKey` 为空 | 400 | `VALIDATION_ERROR`（不打上游） |
| 平台 403（数据域越权） | 403 | `FORBIDDEN`（透传） |
| 平台 404 | 502 | `GATEWAY_UPSTREAM_ERROR` |
| 平台 5xx / 超时 | 502 | `UPSTREAM_SERVER_ERROR` / `GATEWAY_UPSTREAM_ERROR` |

**幂等键**：执行前用 Redis `SETNX` 写 `gw:idem:<userId>:<agentKey>:<traceId>`（TTL 300s，可配），
本周只记录命中情况（`acquired=true|false`），**不拒绝重复请求** —— 拒绝语义需与平台确认幂等边界（第 20 周）。

---

## 7. 平台侧新增：`GET /api/v1/platform/observability/skywalking-status`

**鉴权**：需要登录（同 Week 17/18 的自检接口）。**不回显任何密钥**（类型上无密钥字段）。

成功 200：

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "enabled": true,
    "bridgeAvailable": true,
    "serviceName": "ai-code-platform",
    "backendService": "192.168.132.128:11800",
    "skywalkingTraceId": "117d9ce3d0344ac893889e2f2cc9e9c4.77.17912615625790001",
    "skywalkingSpanId": 0,
    "traceId": "aa8df2254f3b3af5d7ed05c1bb7146d1",
    "otelTraceId": "aa8df2254f3b3af5d7ed05c1bb7146d1",
    "correlationTag": "aicode.trace_id",
    "correlated": true
  },
  "traceId": "aa8df2254f3b3af5d7ed05c1bb7146d1"
}
```

| 字段 | 含义 |
|------|------|
| `enabled` | `observability.skywalking.enabled`（应用侧手动埋点开关）**且**装饰器确实装配 |
| `bridgeAvailable` | Toolkit 是否可用（= Agent 是否真的挂上了）。**Toolkit 在 classpath 上不算挂载**：判据是 Agent 注入的 `skywalking.*` 系统属性 |
| `serviceName` / `backendService` | 自检展示用的 `SW_AGENT_NAME` / `SW_AGENT_COLLECTOR_BACKEND_SERVICES` |
| `skywalkingTraceId` / `skywalkingSpanId` | 本次请求在 SkyWalking 里的 ID（Base64 segmentId）；未启用或非请求线程时为空串 / `-1` |
| `traceId` | 业务链路 ID（与响应头 `X-Trace-Id` 一致） |
| `otelTraceId` | OTel（W3C）traceId，与 Langfuse 对齐 |
| `correlationTag` | 跨后端关联标签名（`aicode.trace_id`） |
| `correlated` | 两套链路 ID 是否**都非空**（即两套系统都记录了本次请求）。注意：SkyWalking traceId 与 W3C traceId **不可能相等**，故这里判断的是「都有」，不是「相等」 |

错误：未登录 401 `UNAUTHORIZED`。

### 7.1 与既有自检接口的关系（三后端并存）

| 接口 | 回答的问题 |
|------|-----------|
| `GET /api/v1/platform/observability/trace-context` | 业务 traceId / OTel traceId / spanId / `traceparent` / Langfuse 深链（Week 17/18） |
| `GET /api/v1/platform/observability/langfuse-status` | Langfuse 开关、端点、正文采集、Prompt 管理、trace 维度（Week 18） |
| `GET /api/v1/platform/observability/skywalking-status` | **本周新增**：SkyWalking 开关、OAP 地址、Agent 是否挂载、两套 ID 与关联标签 |
| `GET /api/v1/platform/observability/llm-cost-summary` | 按模型聚合 token 与成本（Week 18，管理员） |

---

## 8. 契约测试索引

| 契约 | 测试 |
|------|------|
| 信封形状 / 状态码 / traceId 回显 | `GatewayApiContractTest`（12 例） |
| 网关两条鉴权路径（自有用例 + 代理路由） | `GatewayAuthFilterTest`(5) / `GatewayAuthGlobalFilterTest`(4) |
| 链路 ID 派生与平台一致 | `GatewayTraceIdsTest`(7) / `TraceIdWebFilterTest`(5) |
| 上游错误映射（4xx 透传 / 5xx 收敛） | `FeignPlatformClientAdapterTest`(10) |
| Feign 编码器前置条件（`HttpMessageConverters`） | `PlatformFeignConfigurationTest`(3) |
| 缓存语义与降级 | `GatewayAgentUseCaseTest`(11) |
| 自检字段（不含密钥） | `GatewayStatusUseCaseTest`(4) / `PlatformObservabilitySkyWalkingTest`(4) / `SkyWalkingStatusResponseTest`(2) |

---

## 9. 错误码索引（网关）

新增错误码必须同步本表与 `GatewayErrorCode` 枚举，并在 `GatewayApiContractTest` 补一条用例。
（当前枚举见 1.1；成功码固定 `SUCCESS`，不在枚举内。）
