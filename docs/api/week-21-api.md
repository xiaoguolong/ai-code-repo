# Week 21 API — 医疗 SaaS 起步（租户 / SSO / 患者分析 Agent / 记忆）

> 平台端口 **8084**，enterprise **8081**，patient-agent **8083（deprecated）**。
> 响应信封见 `docs/code-implementation-spec.md` 5.6：`{code, message, data, traceId}`，
> 失败时 `data=null`，`traceId` 与响应头 `X-Trace-Id` 一致。平台错误码唯一来源 `ApiErrorCode`。

## 变更一览

| # | 接口 | 变更 |
|---|------|------|
| 1 | `POST /api/v1/platform/auth/verify`（8084） | **新增**：只校验凭据并返回身份，**不建会话**（enterprise SSO 用） |
| 2 | `POST /api/v1/platform/auth/login`（8084） | 响应 `data` 增加 `tenantKey`（兼容扩展） |
| 3 | `GET /api/v1/platform/auth/me`（8084） | 响应增加 `tenantKey` / `tenantName` |
| 4 | `GET /api/v1/platform/tenants`（8084） | **新增**：租户列表，仅管理员 |
| 5 | `GET /api/v1/platform/memories`（8084） | **新增**：按租户+用户隔离的长期记忆查询 / 语义检索 |
| 6 | `POST /api/v1/platform/agents/patient-analysis/runs`（8084） | **新增**：Agent 1（走既有执行记录接口，形状不变） |
| 7 | `POST /api/v1/auth/login`（8081） | 行为随 `sso.enabled` 切换，**请求/响应形状不变** |
| 8 | patient-agent 全部接口（8083） | 响应增加 `Deprecation` / `Sunset` / `Link` 三个头（业务不变） |

---

## 1. 平台身份校验（新增）

`POST http://localhost:8084/api/v1/platform/auth/verify`

用途：给 enterprise 做 SSO 身份源。**不创建会话**（调用方不需要、也拿不到平台 token），
且**免登录**（已加入 Sa-Token 白名单；真机验收时漏配白名单导致此处永远 401，见 Spec 的缺陷登记）。

请求：

```json
{ "username": "operator", "password": "operator123" }
```

成功 200：

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "userId": 2,
    "username": "operator",
    "roleKey": "operator",
    "tenantKey": "default"
  },
  "traceId": "week21-verify-001"
}
```

失败：

| 场景 | HTTP | code | 说明 |
|------|------|------|------|
| 用户名/密码错误 | 401 | `UNAUTHORIZED` | 不区分「用户不存在」与「密码错误」 |
| 租户 `SUSPENDED` | 401 | `UNAUTHORIZED` | 对外不暴露「租户被停用」 |
| 用户名或密码空白 | 400 | `VALIDATION_ERROR` | Bean Validation |

审计：成功写 `LOGIN`（detail 含 `sso=true`），失败写 `LOGIN_FAILED`。

```bash
curl -s -X POST http://localhost:8084/api/v1/platform/auth/verify \
  -H "Content-Type: application/json" -H "X-Trace-Id: week21-verify-001" \
  -d '{"username":"operator","password":"operator123"}'
```

---

## 2. 平台登录 / 当前用户（响应扩展）

`POST /api/v1/platform/auth/login`

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": { "token": "...", "userId": 2, "username": "operator",
            "roleKey": "operator", "tenantKey": "default" },
  "traceId": "..."
}
```

`GET /api/v1/platform/auth/me`

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": { "userId": 2, "username": "operator", "roleKey": "operator",
            "tenantKey": "default", "tenantName": "默认租户" },
  "traceId": "..."
}
```

> 兼容性：只**新增**字段，既有客户端解析不受影响；Week 13–20 的登录用例断言不变。

---

## 3. 租户列表（新增，仅管理员）

`GET http://localhost:8084/api/v1/platform/tenants`

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": [
    { "tenantKey": "default",    "displayName": "默认租户",   "status": "ACTIVE", "createdAt": "2026-10-24T08:00:00Z" },
    { "tenantKey": "hospital-b", "displayName": "演示医院B", "status": "ACTIVE", "createdAt": "2026-10-24T08:00:00Z" }
  ],
  "traceId": "..."
}
```

| 场景 | HTTP | code |
|------|------|------|
| 未登录 | 401 | `UNAUTHORIZED` |
| 非管理员（operator/viewer） | 403 | `FORBIDDEN` |
| 管理员 | 200 | `SUCCESS` |

---

## 4. 长期记忆查询（新增）

`GET http://localhost:8084/api/v1/platform/memories`

| 参数 | 必填 | 说明 |
|------|------|------|
| `query` | 否 | 语义检索文本；缺省则按时间倒序返回 |
| `patientId` | 否 | 只查某患者会话的记忆（`session_id`） |
| `limit` | 否 | 返回条数，默认 5，上限 20 |
| `userId` | 否 | **仅 admin** 可查他人；非 admin 传了返回 403 |

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "tenantKey": "default",
    "userId": 2,
    "items": [
      {
        "memoryId": "m-8f1c...",
        "patientId": "P001",
        "task": "为患者 P001 生成分析报告",
        "answer": "患者血糖控制不佳，建议 2 周内复诊……",
        "score": 0.42,
        "createdAt": "2026-10-24T08:05:00Z"
      }
    ]
  },
  "traceId": "..."
}
```

约定：

- **scope 由服务端推导**（`tenant_key` 来自 `platform_user`），请求里没有任何 tenant 参数；
- 响应**不含 embedding**；`score` 在无 `query` 时为 `null`；
- 非 admin 指定 `userId` → 403 `FORBIDDEN`；admin 指定的用户不存在 → 404 `PLATFORM_NOT_FOUND`；
- 隔离语义：同租户不同用户、同用户不同租户都查不到对方数据（单测覆盖）。

```bash
TOKEN=$(curl -s -X POST http://localhost:8084/api/v1/platform/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"operator","password":"operator123"}' | jq -r .data.token)

curl -s "http://localhost:8084/api/v1/platform/memories?patientId=P001&query=血糖&limit=3" \
  -H "Authorization: Bearer $TOKEN"
```

---

## 5. 患者分析 Agent（Agent 1，新增 Agent 注册）

`POST http://localhost:8084/api/v1/platform/agents/patient-analysis/runs`

请求（走 Week 13 的既有执行接口，`input` 由 Agent 自行解释）：

```json
{
  "input": {
    "patientId": "P001",
    "task": "为患者生成一份慢病风险分析报告"
  }
}
```

成功 200（`data` 为执行记录，形状与 Week 13–16 完全一致）：

> **Week 23 行为变更**：HIGH 风险不再直接 `COMPLETED`，而是返回
> `status=PENDING_APPROVAL` + `outputJson.approvalId`，且不写随访记忆；
> 批准/拒绝接口见 `docs/api/week-23-api.md`。中/低风险仍为 `COMPLETED`。

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "executionId": "3f0e...",
    "userId": 2,
    "agentKey": "patient-analysis",
    "agentType": "PATIENT_ANALYSIS",
    "status": "COMPLETED",
    "inputJson": "{\"patientId\":\"P001\",\"task\":\"...\"}",
    "outputJson": "{\"patientId\":\"P001\",\"patient\":{...},\"reportCount\":2,\"reports\":[...],\"metrics\":{...},\"riskLevel\":\"HIGH\",\"justification\":\"...\",\"report\":\"...\",\"degraded\":false,\"memoryId\":\"m-8f1c...\",\"steps\":[...],\"totalSteps\":5}",
    "model": "deepseek-chat",
    "promptTokens": 412,
    "completionTokens": 388,
    "totalTokens": 800,
    "errorMessage": "",
    "startedAt": "2026-10-24T08:05:00Z",
    "finishedAt": "2026-10-24T08:05:31Z"
  },
  "traceId": "..."
}
```

`outputJson` 的字段语义：

| 字段 | 说明 |
|------|------|
| `patient` / `metrics` / `reports` | 患者基础信息 / 健康指标 / 检查报告列表 |
| `riskLevel` / `justification` | `LOW` / `MEDIUM` / `HIGH` + 规则依据 |
| `report` | 分析报告正文 |
| `degraded` | `true` = 模型不可用，正文是规则模板（**不冒充模型输出**） |
| `memoryId` | 写入的长期记忆 ID；写失败为 `null` |
| `steps` / `totalSteps` | 步骤轨迹（`stepNo` / `agent` / `summary`） |

失败：

| 场景 | HTTP | code |
|------|------|------|
| `patientId` 空白 | 400 | `VALIDATION_ERROR` |
| 无该 Agent 权限 / patientId 越权 | 403 | `FORBIDDEN` |
| Agent 未注册 | 404 | `PLATFORM_NOT_FOUND` |
| Agent 被禁用 | 400 | `AGENT_DISABLED` |
| 任务含注入指令 | 400 | `GUARDRAIL_VIOLATION` |
| 工具执行失败 | 502 | `TOOL_EXECUTION_ERROR` |

```bash
curl -s -X POST http://localhost:8084/api/v1/platform/agents/patient-analysis/runs \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"input":{"patientId":"P001"}}' | jq '.data | {status, totalTokens, outputJson}'
```

---

## 6. enterprise 登录（8081，SSO 开关）

`POST http://localhost:8081/api/v1/auth/login` —— **形状与第 4 周完全一致**。

```json
{ "username": "operator", "password": "operator123" }
```

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": { "token": "本地会话 token", "user": { "id": 12, "username": "operator" } }
}
```

| 场景 | `sso.enabled=false`（默认） | `sso.enabled=true` |
|------|------------------------------|--------------------|
| 凭据正确 | 本地校验 → 200 | 平台 `verify` → 建链/开户 → 200 |
| 凭据错误 | 401 `UNAUTHORIZED` | 401 `UNAUTHORIZED` |
| 平台不可达 / 5xx | — | **503 `AUTH_SSO_UNAVAILABLE`** |
| 平台用户首次登录、本地已有同名用户且未建链 | — | **409 `CONFLICT`** |

配置（模块 `.env`，详见 `apps/enterprise-knowledge-agent/.env.example`）：

```properties
SSO_ENABLED=false
SSO_PLATFORM_BASE_URL=http://localhost:8084
SSO_PLATFORM_TIMEOUT_MS=3000
SSO_AUTO_PROVISION=true
```

> 安全：enterprise **不保存平台 token**；平台 `verify` 也不建会话。SSO 只解决「身份从哪来」，
> 授权仍由 enterprise 自己的会话 + 平台 RBAC 各自负责。

---

## 7. patient-agent（8083，deprecated）

所有响应新增：

```text
Deprecation: true
Sunset: Wed, 31 Dec 2026 23:59:59 GMT
Link: <http://localhost:8084/api/v1/platform/agents/patient-analysis/runs>; rel="successor-version"
```

业务响应体不变（不破坏第 5–7 周客户端）。迁移路径：

| 旧（8083） | 新（8084） |
|------------|------------|
| `POST /api/v1/agents/runs` `{sessionId, task}` | `POST /api/v1/platform/agents/patient-analysis/runs` `{"input":{"patientId":"P001"}}` |
| 会话记忆（`sessionId`） | `platform_memory`（`session_id = patientId`，租户+用户隔离） |
| 无鉴权 | Sa-Token 登录 + RBAC + 数据域 |

```bash
curl -s -i -X POST http://localhost:8083/api/v1/agents/runs \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"s1","task":"查询患者 P001"}' | findstr /i "deprecation sunset link"
```

---

## 8. 错误码（本周新增）

| code | HTTP | 出现位置 | 含义 |
|------|------|----------|------|
| `AUTH_SSO_UNAVAILABLE` | 503 | enterprise 8081 | 平台身份源不可达（不伪装成 401） |

平台侧未新增错误码：租户停用复用 `UNAUTHORIZED`，越权复用 `FORBIDDEN`，
用户不存在复用 `PLATFORM_NOT_FOUND`（与 Week 16 的 `ApiErrorCode` 契约一致）。
