# Week 27 接口文档 — 知识库运营化（对账 + 审批提醒）

> 平台通用信封/错误码见 `docs/api/week-16-api.md`。本周**不新增错误码**：
> 对账/提醒均沿用 `SUCCESS` / `FORBIDDEN` / `PLATFORM_NOT_FOUND` / `TOOL_EXECUTION_ERROR`。

## 1. enterprise 内部端点（8081，仅平台调用）

### 1.1 知识库清单（两种模式，二选一）

```http
GET /api/v1/internal/knowledge/bases?platformUserId=2
GET /api/v1/internal/knowledge/bases?enterpriseUserId=7
X-Internal-Token: <token>
```

| 参数 | 必填 | 说明 |
|------|------|------|
| `platformUserId` | 二选一 | 按平台用户实时 SSO 映射查（**导入**场景，Week 25 语义不变） |
| `enterpriseUserId` | 二选一 | 按企业本地用户查（**对账**场景，Week 27 新增） |

响应（两种模式同形状）：

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "enterpriseUserId": 7,
    "bases": [
      {"knowledgeBaseId": 11, "name": "慢病指南", "description": "内部资料", "documentCount": 2}
    ]
  }
}
```

| 情况 | 状态码 |
|------|--------|
| 令牌缺失/错误/未配置 | 403 `FORBIDDEN` |
| 两个参数都给或都不给 | 400 `VALIDATION_ERROR` |
| `platformUserId` 未建 SSO 映射 | 404 `NOT_FOUND` |
| `enterpriseUserId` 对应本地用户不存在 | 404 `NOT_FOUND` |
| 正常（含 0 个知识库） | 200 |

> 平台对账把 404 视为**该组失败**（`failed++`），不会把该组绑定标记为「已删除」——
> 用户被删与知识库被删是两件事。

## 2. 平台侧：知识库绑定（8084）

### 2.1 列表（新增同步字段）

`GET /api/v1/platform/knowledge-bindings`

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "total": 1,
    "items": [
      {
        "bindingId": "b1...",
        "knowledgeBaseId": 11,
        "name": "慢病指南",
        "description": "内部资料",
        "enabled": true,
        "syncStatus": "ACTIVE",
        "lastSyncedAt": "2026-10-25T18:15:00Z",
        "createdAt": "2026-10-25T10:00:00Z"
      }
    ]
  },
  "traceId": "..."
}
```

| `syncStatus` | 含义 | 可检索 |
|--------------|------|--------|
| `UNKNOWN` | 导入后尚未对账（V9 旧行默认） | 是 |
| `ACTIVE` | 最近一次对账企业侧存在 | 是 |
| `MISSING` | 最近一次对账企业侧已删除 | **否**（问答 404） |

`lastSyncedAt` 为空表示尚未成功对账过。

### 2.2 手动对账（仅 admin，仅本租户）

`POST /api/v1/platform/knowledge-bindings/reconcile`

无请求体。对当前管理员所在租户的全部绑定按「企业归属人」分组调用企业清单。

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "tenantKey": "default",
    "reconciledAt": "2026-10-25T18:15:00Z",
    "groups": 1,
    "checked": 2,
    "updated": 1,
    "missing": 1,
    "unchanged": 0,
    "failed": 0,
    "unbound": 0
  },
  "traceId": "..."
}
```

| 字段 | 说明 |
|------|------|
| `groups` | 实际调用企业清单的次数（= 不同的 `enterpriseUserId` 数） |
| `checked` | 本次检查的绑定条数 |
| `updated` | 名称/描述/状态发生变化并落库的条数 |
| `missing` | 本次被标记 `MISSING` 的条数（**不删除行**） |
| `unchanged` | 企业侧存在且元数据未变的条数（仍会刷新 `lastSyncedAt`） |
| `failed` | 调用企业清单失败的分组内绑定数（状态保持不变） |
| `unbound` | 企业侧存在但本租户未绑定的知识库数（只统计，不自动绑定） |

| 情况 | 状态码 |
|------|--------|
| 非 admin | 403 `FORBIDDEN` |
| 本租户无绑定 | 200，`groups=0/checked=0` |
| enterprise 不可用 / 令牌未配 | 200，`failed>0`（**不是 502**：对账是批量任务，单组失败不使整次失败） |

### 2.3 导入 / 删除（语义不变，导入补齐同步字段）

- `POST /api/v1/platform/knowledge-bindings/import`：导入后绑定为 `syncStatus=ACTIVE`、
  `lastSyncedAt=now`（刚从企业侧读到，等价于一次成功对账）。
- `DELETE /api/v1/platform/knowledge-bindings/{bindingId}`：不变。

## 3. 平台侧：审批待办超时提醒

### 3.1 站内提醒视图（登录即可）

`GET /api/v1/platform/approvals/reminders?limit=20`

- 普通用户：只看自己发起的待审；admin：看全部待审（与 `GET /approvals` 同口径）。
- 只返回 `created_at <= now - thresholdHours`（默认 24h）的 `PENDING` 任务。
- `limit` 默认 20、上限 100。

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "thresholdHours": 24,
    "total": 1,
    "items": [
      {
        "approvalId": "a1...",
        "executionId": "e1...",
        "agentKey": "patient-analysis",
        "patientId": "P002",
        "riskLevel": "HIGH",
        "createdAt": "2026-10-24T09:00:00Z",
        "overdueHours": 33
      }
    ]
  },
  "traceId": "..."
}
```

| 情况 | 状态码 |
|------|--------|
| 未登录 | 401 `UNAUTHORIZED` |
| `limit` 非法（<0 / 超上限） | 按上下限夹取，不报错 |
| 无超时待办 | 200，`total=0` |

> 提醒是**只读视图**；调度任务另外把「已提醒」记账到 `platform_approval_reminder`，
> **不会**改变审批状态，也不会自动拒绝。

### 3.2 提醒调度（系统行为，非 HTTP）

- 默认每小时第 5 分钟执行（`0 5 * * * *`），`PLATFORM_APPROVAL_REMINDER_ENABLED=false` 可关；
- 阈值 `PLATFORM_APPROVAL_REMINDER_THRESHOLD_HOURS`（默认 24）、
  冷却 `PLATFORM_APPROVAL_REMINDER_COOLDOWN_HOURS`（默认 12）、
  单次上限 `PLATFORM_APPROVAL_REMINDER_LIMIT`（默认 200）；
- 每条提醒写审计 `APPROVAL_REMIND`，detail 只含 `approvalId` 与 `overdueHours`，不含患者正文。

## 4. 指标

| Prometheus 导出名 | 类型 | 标签 | 说明 |
|-------------------|------|------|------|
| `knowledge_search_duration_seconds_count/_sum/_bucket` | Timer | `knowledge_outcome="SUCCESS|FAILURE"` | 平台侧检索耗时（含上游调用） |
| `knowledge_hit_count_total` | Counter | 无 | 累计命中切片数（amount = 本次命中数） |
| `knowledge_reconcile_count_total` | Counter | `knowledge_result="updated|missing|unchanged|failed"` | 对账结果分布 |
| `knowledge_binding_missing_count` | Gauge | 无 | 当前 `MISSING` 绑定数 |
| `approval_reminder_count_total` | Counter | 无 | 累计下发的超时提醒数 |

`knowledge.search.duration` 已加入 `management.metrics.distribution.percentiles-histogram`
与 `maximum-expected-value: 5s`，Grafana 可直接 `histogram_quantile` 算 P95。

## 5. 审计事件

| `action` | 触发 | `resource` | `detail` |
|----------|------|------------|----------|
| `KNOWLEDGE_RECONCILE` | admin 手动对账 | `knowledge-binding:reconcile` | `groups/checked/updated/missing/failed/unbound` |
| `APPROVAL_REMIND` | 提醒调度命中一条待办 | `approval:{approvalId}` | `approvalId=... overdueHours=...` |

> 调度对账**不写审计**（每日一条系统噪声无价值，日志 + 指标已可追溯）；
> 手动对账必须写，便于回答「谁在什么时候改了绑定状态」。
