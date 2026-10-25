# Week 23 API — 患者分析 HITL + 长期记忆生命周期

> 服务：`spring-ai-alibaba-agent`（8084）
> 统一信封：`{code, message, data, traceId}`；失败时 `data=null`，`traceId` 与响应头 `X-Trace-Id` 一致。
> 除 `POST /api/v1/platform/auth/verify` 外，本文接口均需登录：`Authorization: Bearer <token>`。

## 0. 变更总览

| # | Method | Path | 类型 |
|---|--------|------|------|
| 1 | POST | `/api/v1/platform/agents/patient-analysis/runs` | 行为变更：HIGH → `PENDING_APPROVAL` |
| 2 | GET | `/api/v1/platform/approvals` | 新增 |
| 3 | GET | `/api/v1/platform/approvals/{approvalId}` | 新增 |
| 4 | POST | `/api/v1/platform/approvals/{approvalId}/decision` | 新增 |
| 5 | DELETE | `/api/v1/platform/memories/{memoryId}` | 新增 |
| 6 | POST | `/api/v1/platform/memories/purge-expired` | 新增 |
| 7 | POST | `/api/v1/platform/memories/rebuild-embeddings` | 新增 |
| 8 | GET | `/api/v1/platform/roles?tenantKey=` | 行为扩展：按租户过滤 |
| 9 | GET | `/api/v1/admin/sso-links`（8081） | 新增（运维令牌） |
| 10 | POST | `/api/v1/admin/sso-links`（8081） | 新增（建链/重建） |
| 11 | DELETE | `/api/v1/admin/sso-links/{platformUserId}`（8081） | 新增（解绑，可选删本地用户） |

执行状态新增：`PENDING_APPROVAL`（待人工审核）、`REJECTED`（人工拒绝）。原有
`RUNNING` / `COMPLETED` / `FAILED` 语义不变。

---

## 1. 运行患者分析（行为变更）

`POST /api/v1/platform/agents/patient-analysis/runs`

```json
{
  "input": {
    "patientId": "P002",
    "task": "为患者生成慢病风险分析报告"
  }
}
```

### 1.1 HIGH 风险：200 + 待审

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "executionId": "8b1e...",
    "userId": 2,
    "agentKey": "patient-analysis",
    "agentType": "PATIENT_ANALYSIS",
    "status": "PENDING_APPROVAL",
    "inputJson": "{\"patientId\":\"P002\",\"task\":\"...\"}",
    "outputJson": "{\"patientId\":\"P002\",\"patient\":{...},\"metrics\":{...},\"reports\":[...],\"riskLevel\":\"HIGH\",\"justification\":\"收缩压 168 达到 160 阈值\",\"report\":\"...\",\"degraded\":false,\"task\":\"...\",\"approvalId\":\"ap-7c2f...\",\"approvalStatus\":\"PENDING\",\"memoryId\":null,\"steps\":[...],\"totalSteps\":5}",
    "model": "deepseek-chat",
    "usage": {"promptTokens": 412, "completionTokens": 388, "totalTokens": 800},
    "errorMessage": "",
    "startedAt": "2026-10-25T02:10:00Z",
    "finishedAt": null
  },
  "traceId": "..."
}
```

要点：

- `status=PENDING_APPROVAL`，`finishedAt=null`（尚未终态）；
- `outputJson.approvalId` 是审批任务 ID；`memoryId=null`（**没有写随访记忆**）；
- 报告正文已生成（审核人可据此判断），报告里 `degraded` 语义与第 21 周一致。

### 1.2 中/低风险：200 + COMPLETED（与第 21 周一致）

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "executionId": "9c2f...",
    "status": "COMPLETED",
    "outputJson": "{\"patientId\":\"P001\",\"riskLevel\":\"MEDIUM\",\"report\":\"...\",\"memoryId\":\"m-3a...\",\"totalSteps\":5}",
    "finishedAt": "2026-10-25T02:11:31Z"
  },
  "traceId": "..."
}
```

### 1.3 `outputJson` 字段增量

| 字段 | 出现时机 | 说明 |
|------|----------|------|
| `task` | 始终 | 归一化后的任务原文（批准后写记忆复用同一份） |
| `approvalId` | `PENDING_APPROVAL` / 已完成审批 | 审批任务 ID |
| `approvalStatus` | 有审批时 | `PENDING` / `APPROVED` / `REJECTED` |
| `memoryId` | 中低风险 / 批准后 | 长期记忆 ID；待审时为 `null` |

### 1.4 失败

| 场景 | HTTP | code |
|------|------|------|
| `patientId` 空白 | 400 | `VALIDATION_ERROR` |
| 无 Agent 权限 / patientId 越权 | 403 | `FORBIDDEN` |
| Agent 未注册 | 404 | `PLATFORM_NOT_FOUND` |
| Agent 被禁用 | 400 | `AGENT_DISABLED` |
| 任务含注入指令 | 400 | `GUARDRAIL_VIOLATION` |
| 工具执行失败 | 502 | `TOOL_EXECUTION_ERROR` |

---

## 2. 审批待办

### 2.1 列表

`GET /api/v1/platform/approvals?status=PENDING&limit=20`

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|------|------|------|
| `status` | string | 否 | `PENDING` | `PENDING` / `APPROVED` / `REJECTED`；非法值 400 |
| `limit` | int | 否 | 20 | 1–100；越界被收敛 |

- 普通用户只看自己的审批；admin 看全部；
- 按 `created_at DESC` 排序。

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "items": [
      {
        "approvalId": "ap-7c2f...",
        "executionId": "8b1e...",
        "agentKey": "patient-analysis",
        "patientId": "P002",
        "riskLevel": "HIGH",
        "task": "为患者生成慢病风险分析报告",
        "status": "PENDING",
        "comment": "",
        "createdAt": "2026-10-25T02:10:00Z",
        "decidedAt": null
      }
    ],
    "total": 1
  },
  "traceId": "..."
}
```

> 列表不返回报告正文（体积大且含患者隐私）；需要正文时按 `executionId` 调
> `GET /api/v1/platform/executions/{executionId}`。

### 2.2 详情

`GET /api/v1/platform/approvals/{approvalId}`

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "approvalId": "ap-7c2f...",
    "executionId": "8b1e...",
    "agentKey": "patient-analysis",
    "patientId": "P002",
    "riskLevel": "HIGH",
    "task": "为患者生成慢病风险分析报告",
    "status": "PENDING",
    "comment": "",
    "createdAt": "2026-10-25T02:10:00Z",
    "decidedAt": null
  },
  "traceId": "..."
}
```

权限：本人或 admin；其他人 403；不存在 404。

### 2.3 决策

`POST /api/v1/platform/approvals/{approvalId}/decision`

```json
{
  "approved": true,
  "comment": "报告已核对，同意进入随访"
}
```

| 字段 | 类型 | 必填 | 约束 |
|------|------|------|------|
| `approved` | boolean | 是 | `true` 批准 / `false` 拒绝 |
| `comment` | string | 否 | ≤ 500 字符；拒绝时建议填写原因 |

批准（200）：

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "approvalId": "ap-7c2f...",
    "executionId": "8b1e...",
    "status": "APPROVED",
    "decidedBy": 2,
    "comment": "报告已核对，同意进入随访",
    "decidedAt": "2026-10-25T02:20:00Z",
    "executionStatus": "COMPLETED",
    "memoryId": "m-91d2..."
  },
  "traceId": "..."
}
```

拒绝（200）：

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "approvalId": "ap-7c2f...",
    "executionId": "8b1e...",
    "status": "REJECTED",
    "decidedBy": 2,
    "comment": "指标需复测后再评估",
    "decidedAt": "2026-10-25T02:20:00Z",
    "executionStatus": "REJECTED",
    "memoryId": null
  },
  "traceId": "..."
}
```

失败：

| 场景 | HTTP | code |
|------|------|------|
| 未登录 | 401 | `UNAUTHORIZED` |
| 非本人且非 admin | 403 | `FORBIDDEN` |
| approvalId 不存在 | 404 | `PLATFORM_NOT_FOUND` |
| 已决策（非 PENDING） | 409 | `PLATFORM_CONFLICT` |
| `approved` 缺失 | 400 | `VALIDATION_ERROR` |

> 决策**不调用模型、不重新查工具**：只把已生成的报告写入长期记忆并迁移状态。
> 并发决策由 `UPDATE ... WHERE status='PENDING'` 保证只有一个成功。

---

## 3. 记忆生命周期

### 3.1 删除

`DELETE /api/v1/platform/memories/{memoryId}`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `userId` | long | 否 | 仅 admin 可指定他人；普通用户传他人 → 403 |

成功 200：

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "memoryId": "m-91d2...",
    "deleted": 1,
    "tenantKey": "default",
    "userId": 2
  },
  "traceId": "..."
}
```

失败：不存在或不属于该范围 → 404 `PLATFORM_NOT_FOUND`（**不区分**「不存在」与「别人的」）；
非 admin 指定他人 → 403；未登录 → 401。

### 3.2 清理过期

`POST /api/v1/platform/memories/purge-expired?limit=100`

仅 admin。`limit` 默认 100、1–500。

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "deleted": 7,
    "limit": 100
  },
  "traceId": "..."
}
```

语义：删除 `expires_at IS NOT NULL AND expires_at <= now` 的记录；`ttl-days<=0` 写入的记录
`expires_at=null`，永不被清理。每日 03:30 的定时任务执行同一逻辑（只记日志，不写审计）。

### 3.3 重建向量

`POST /api/v1/platform/memories/rebuild-embeddings?limit=100`

仅 admin。`limit` 默认 100、1–500。

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "scanned": 2,
    "rebuilt": 2,
    "failed": 0,
    "currentModel": "hashing:default:1024"
  },
  "traceId": "..."
}
```

- 候选：`embedding_model` 为空，或与当前 `provider:model:dimension` 不同；
- 逐条重新 embedding `task`；单条失败只计入 `failed`，不中断整批；
- 重建后该记录的语义检索立即使用新向量。

### 3.4 查询（既有，行为微调）

`GET /api/v1/platform/memories?patientId=P001&query=血糖控制&limit=5`

响应形状不变；增量行为：**过期记录不再出现在列表与检索结果中**。

---

## 4. 批次 2：租户角色 / 执行记录租户 / enterprise 运维

### 4.1 角色按租户查询（8084）

`GET /api/v1/platform/roles?tenantKey=hospital-b`

- `tenantKey` 可空：空则返回全部租户角色（管理员总览）；
- `GET /api/v1/platform/roles/{roleKey}?tenantKey=hospital-b` 查单租户角色，空则 default；
- 响应新增 `tenantKey` 字段：

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": [
    {
      "tenantKey": "hospital-b",
      "roleKey": "operator",
      "displayName": "运营人员",
      "admin": false,
      "allowedAgentKeys": ["patient-risk", "medical-assistant", "patient-analysis"],
      "allowedToolKeys": ["PatientLookupTool", "HealthMetricTool", "ReportLookupTool"],
      "allowedPatientIds": ["P001", "P002"]
    }
  ],
  "traceId": "..."
}
```

> 不同租户的 `operator` 是两份独立副本，授权互不影响；V7 之前只有全局角色，
> 升级后 default 与 hospital-b 各一份。

### 4.2 执行记录新增 `tenantKey`（8084）

`GET /api/v1/platform/executions` / `GET /api/v1/platform/executions/{executionId}` 的 `data` 新增：

```json
{ "executionId": "...", "userId": 104, "tenantKey": "hospital-b", "status": "COMPLETED" }
```

`tenantKey` 由服务端从 `platform_user` 推导，不接受客户端传入；V7 迁移会按 `user_id` 回填旧记录。

### 4.3 enterprise SSO 映射运维（8081，A1 运维令牌）

所有端点要求请求头 `X-Admin-Token: <ENTERPRISE_ADMIN_TOKEN>`；**未配置令牌时一律 403**。
不走 Sa-Token 登录；enterprise 只监听回环。

#### 列表

`GET /api/v1/admin/sso-links?limit=50`

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "items": [
      { "platformUserId": 2, "enterpriseUserId": 1, "tenantKey": "default",
        "linkedAt": "2026-10-24T10:00:00Z" }
    ],
    "total": 1
  }
}
```

#### 建链 / 重建

`POST /api/v1/admin/sso-links`

```json
{ "platformUserId": 2, "enterpriseUserId": 1, "tenantKey": "default" }
```

- 幂等：同一对已建链直接返回；
- 平台用户已链到别的本地用户 → 先解旧链再建新链（重建）；
- 本地用户已被别的平台用户占用 → 409 `CONFLICT`，不静默抢占；
- 本地用户不存在 → 404 `NOT_FOUND`。

#### 解绑（两种语义）

`DELETE /api/v1/admin/sso-links/{platformUserId}?deleteLocalUser=false`

| `deleteLocalUser` | 行为 | 下次 SSO 登录 |
|-------------------|------|----------------|
| `false`（默认） | 只删映射，本地用户保留 | 本地同名用户仍在 → **409**（预期，文档写清） |
| `true` | 先删映射，再删本地 `app_user` | 可重新自动开户 → **200** |

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "platformUserId": 2,
    "enterpriseUserId": 1,
    "unlinked": true,
    "localUserDeleted": true
  }
}
```

- 映射不存在 → 404；
- `deleteLocalUser=true` 但本地用户仍被文档/会话引用 → 409，提示“只解绑”，不级联删业务数据。

#### 错误码

| 场景 | HTTP | code |
|------|------|------|
| 令牌未配置/缺失/错误 | 403 | `FORBIDDEN` |
| 本地用户/映射不存在 | 404 | `NOT_FOUND` |
| 本地用户被其他平台用户占用 / 不能删 | 409 | `CONFLICT` |
| 参数非法（ID ≤ 0） | 400 | `VALIDATION_ERROR` |

> **平台删用户级联暂缓**：平台当前没有“删除用户”API，强行删会撞
> `execution_record` / `platform_approval` 外键；先由上面 enterprise 运维端点解绑。
> 待平台用户生命周期（停用/删除）产品语义确定后再补。

---

## 5. 审计事件增量

| action | result | detail | 触发 |
|--------|--------|--------|------|
| `APPROVAL_SUBMIT` | `SUCCESS` | `executionId=... approvalId=...` | HIGH 风险生成审批任务 |
| `APPROVAL_DECIDE` | `SUCCESS` / `DENIED` | `approvalId=... executionId=... approved=true/false` | 人工批准/拒绝 |
| `MEMORY_DELETE` | `SUCCESS` / `DENIED` | `memoryId=... tenantKey=... userId=...` | 删除记忆 |
| `MEMORY_PURGE` | `SUCCESS` / `FAILURE` | `deleted=N limit=N` | 手工清理过期 |
| `MEMORY_REBUILD` | `SUCCESS` / `FAILURE` | `scanned=N rebuilt=N failed=N` | 重建向量 |

审计 detail 不含任务/报告正文，避免患者隐私进审计表。

---

## 6. curl 速查

```bash
BASE=http://localhost:8084
TOKEN=<login token>

# 1) HIGH 风险 → 待审
curl -s -X POST $BASE/api/v1/platform/agents/patient-analysis/runs \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"input":{"patientId":"P002"}}' | jq '.data | {status, executionId, outputJson}'

# 2) 待办
curl -s "$BASE/api/v1/platform/approvals?status=PENDING" \
  -H "Authorization: Bearer $TOKEN" | jq '.data.items'

# 3) 批准
curl -s -X POST $BASE/api/v1/platform/approvals/<approvalId>/decision \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"approved":true,"comment":"同意进入随访"}' | jq '.data'

# 4) 删除一条记忆
curl -s -X DELETE "$BASE/api/v1/platform/memories/<memoryId>" \
  -H "Authorization: Bearer $TOKEN" | jq '.data'

# 5) 清理过期（admin）
curl -s -X POST "$BASE/api/v1/platform/memories/purge-expired" \
  -H "Authorization: Bearer $TOKEN" | jq '.data'

# 6) 重建向量（admin）
curl -s -X POST "$BASE/api/v1/platform/memories/rebuild-embeddings?limit=100" \
  -H "Authorization: Bearer $TOKEN" | jq '.data'

# 7) 角色按租户查（批次 2）
curl -s "$BASE/api/v1/platform/roles?tenantKey=hospital-b" \
  -H "Authorization: Bearer $TOKEN" | jq '.data'

# 8) enterprise SSO 运维（8081；批次 2，令牌来自 ENTERPRISE_ADMIN_TOKEN）
EBASE=http://localhost:8081
ADMIN_TOKEN=<ENTERPRISE_ADMIN_TOKEN>
curl -s "$EBASE/api/v1/admin/sso-links?limit=50" -H "X-Admin-Token: $ADMIN_TOKEN" | jq '.data'
curl -s -X POST "$EBASE/api/v1/admin/sso-links" \
  -H "X-Admin-Token: $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"platformUserId":2,"enterpriseUserId":1,"tenantKey":"default"}' | jq '.data'
# 只解绑（本地用户保留；下次 SSO 登录 409 属预期）
curl -s -X DELETE "$EBASE/api/v1/admin/sso-links/2" -H "X-Admin-Token: $ADMIN_TOKEN" | jq '.data'
# 解绑并删本地用户（下次可重新开户）
curl -s -X DELETE "$EBASE/api/v1/admin/sso-links/2?deleteLocalUser=true" \
  -H "X-Admin-Token: $ADMIN_TOKEN" | jq '.data'
```
