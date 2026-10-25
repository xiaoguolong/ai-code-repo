# Week 24 接口文档 — Agent 2 运营分析 + Agent 4 随访

> 通用约定见 `docs/api/week-16-api.md`：扁平信封（`code` / `message` / `data` / `traceId`）、
> `Authorization: Bearer <token>`、错误码取 `ApiErrorCode` 枚举。
> 本周**不新增错误码**：复用 `VALIDATION_ERROR` / `UNAUTHORIZED` / `FORBIDDEN` /
> `NOT_FOUND` / `PLATFORM_CONFLICT` / `PLATFORM_AGENT_DISABLED`。

## 1. Agent 调度（复用既有端点）

### 1.1 生成运营日报

`POST /api/v1/platform/agents/operations-analysis/runs`

权限：**admin**（运营日报含整租户数据；operator 调用返回 `FORBIDDEN`）。

请求：

```json
{
  "input": {
    "windowDays": 7,
    "task": "生成最近 7 天运营日报"
  }
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `input.windowDays` | int | 否 | 统计窗口天数，默认 7，范围 1–30；越界 `VALIDATION_ERROR` |
| `input.task` | string | 否 | 任务原文，默认「生成最近 N 天运营日报」 |

响应 `data`（执行记录）：

```json
{
  "executionId": "0f0c...",
  "agentKey": "operations-analysis",
  "agentType": "OPERATIONS_ANALYSIS",
  "status": "COMPLETED",
  "tenantKey": "default",
  "model": "deepseek-chat",
  "totalTokens": 812,
  "output": {
    "reportType": "DAILY",
    "windowStart": "2026-10-18T09:00:00Z",
    "windowEnd": "2026-10-25T09:00:00Z",
    "windowDays": 7,
    "summary": {
      "totalRuns": 23,
      "completed": 18,
      "failed": 2,
      "pendingApproval": 2,
      "rejected": 1,
      "running": 0,
      "successRate": 0.9,
      "failureRate": 0.1,
      "promptTokens": 12000,
      "completionTokens": 8000,
      "totalTokens": 20000,
      "totalCostUsd": 0.012,
      "costConfigured": true,
      "truncated": false
    },
    "agents": [
      {"agentKey": "patient-analysis", "runs": 19, "failures": 2, "tokens": 18000},
      {"agentKey": "operations-analysis", "runs": 4, "failures": 0, "tokens": 2000}
    ],
    "anomalies": [
      {
        "code": "APPROVAL_BACKLOG",
        "level": "HIGH",
        "message": "待审审批 21 条，超过阈值 20",
        "value": 21.0,
        "threshold": 20.0
      }
    ],
    "report": "## 运营概览\n...",
    "degraded": false,
    "task": "生成最近 7 天运营日报"
  }
}
```

> `successRate + failureRate` 的分母是 `completed + failed`，不含 `pendingApproval/rejected/running`。
> `costConfigured=false` 表示价目表里没有对应模型，`totalCostUsd` 只统计已配置模型的部分。

### 1.2 生成随访计划

`POST /api/v1/platform/agents/followup/runs`

权限：operator / admin（受 RBAC 的 `platform_role_agent` 约束）。

请求：

```json
{
  "input": {
    "patientId": "P002",
    "task": "为 P002 生成随访计划"
  }
}
```

响应 `data.output`：

```json
{
  "patientId": "P002",
  "riskLevel": "HIGH",
  "planText": "## 随访计划\n1. 3 天后复测血压...",
  "degraded": false,
  "taskIds": ["f1...", "f2...", "f3..."],
  "tasks": [
    {"taskId": "f1...", "title": "复测血压与症状", "dueAt": "2026-10-28T09:00:00Z", "priority": "HIGH"},
    {"taskId": "f2...", "title": "复查 HbA1c/空腹血糖", "dueAt": "2026-11-08T09:00:00Z", "priority": "HIGH"},
    {"taskId": "f3...", "title": "门诊复诊", "dueAt": "2026-11-24T09:00:00Z", "priority": "HIGH"}
  ],
  "steps": [
    {"stepNo": 1, "agent": "patient_lookup", "summary": "loaded patient P002"},
    {"stepNo": 2, "agent": "metric_lookup", "summary": "loaded metrics"},
    {"stepNo": 3, "agent": "risk_analysis", "summary": "risk=HIGH"},
    {"stepNo": 4, "agent": "plan_generation", "summary": "tasks=3 degraded=false"}
  ],
  "totalSteps": 4
}
```

> 任务条目由规则决定（`FollowUpPlanner`），模型只写 `planText`；模型不可用时 `degraded=true` 且文本为模板。

## 2. 随访任务

### 2.1 任务列表

`GET /api/v1/platform/followup-tasks?status=PENDING&patientId=P002&userId=&limit=20`

| 参数 | 必填 | 说明 |
|------|------|------|
| `status` | 否 | `PENDING`（默认）/ `COMPLETED` / `CANCELLED`；非法值 400 |
| `patientId` | 否 | 按患者过滤 |
| `userId` | 否 | 仅 admin 可用；非 admin 传他人 403 |
| `limit` | 否 | 默认 20，上限 100 |

响应：

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "total": 2,
    "tasks": [
      {
        "taskId": "f1...",
        "tenantKey": "default",
        "userId": 2,
        "patientId": "P002",
        "source": "APPROVAL",
        "approvalId": "a1...",
        "executionId": "e1...",
        "title": "复测血压与症状",
        "detail": "3 天后复测血压并记录症状变化",
        "priority": "HIGH",
        "status": "PENDING",
        "dueAt": "2026-10-28T09:00:00Z",
        "createdAt": "2026-10-25T09:00:00Z",
        "updatedAt": "2026-10-25T09:00:00Z",
        "completedAt": null,
        "note": "",
        "overdue": false
      }
    ]
  },
  "traceId": "..."
}
```

### 2.2 任务详情

`GET /api/v1/platform/followup-tasks/{taskId}`

本人或 admin；他人 403、不存在 404。

### 2.3 完成任务

`POST /api/v1/platform/followup-tasks/{taskId}/complete`

```json
{"note": "已复测 138/86，患者无不适"}
```

- `note` 可空，最长 500；
- 成功 `data.status=COMPLETED` + `completedAt`；
- 已是终态 → **409 `PLATFORM_CONFLICT`**（重复完成/已取消）；
- 审计 `FOLLOWUP_TASK_COMPLETE`。

### 2.4 取消任务

`POST /api/v1/platform/followup-tasks/{taskId}/cancel`

```json
{"reason": "患者已转诊至上级医院"}
```

- 语义同完成：原子迁移，已终态 409；
- 审计 `FOLLOWUP_TASK_CANCEL`。

### 2.5 站内提醒

`GET /api/v1/platform/followup-tasks/reminders?days=7`

| 参数 | 必填 | 说明 |
|------|------|------|
| `days` | 否 | 未来多少天内到期，默认 7、上限 30；越界 400 |

响应：

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "days": 7,
    "overdue": [{"taskId": "f9...", "patientId": "P002", "title": "复测血压", "dueAt": "2026-10-24T09:00:00Z", "overdueDays": 1}],
    "dueSoon": [{"taskId": "f1...", "patientId": "P002", "title": "复测血压与症状", "dueAt": "2026-10-28T09:00:00Z", "overdueDays": 0}]
  },
  "traceId": "..."
}
```

- 只返回 `PENDING`；`overdue` 按 `dueAt` 升序，`dueSoon` 按 `dueAt` 升序；
- `overdueDays` 为逾期天数（未逾期为 0）。

## 3. 审批联动的输出增量

第 23 周的 `POST /approvals/{approvalId}/decision`（批准）响应 `data` 新增：

```json
{
  "approvalId": "a1...",
  "executionId": "e1...",
  "approvalStatus": "APPROVED",
  "executionStatus": "COMPLETED",
  "memoryId": "m1...",
  "followUpTaskIds": ["f1...", "f2...", "f3..."]
}
```

- `followUpTaskIds` 可能为空（任务生成失败，只 WARN + `FOLLOWUP_TASK_CREATE/FAILURE` 审计）；
- 拒绝时该字段为 `[]`。

## 4. 运维/观测增量

| 项 | 变化 |
|----|------|
| `GET /actuator/prometheus` | 新增 `approval_pending_count`（Gauge） |
| Grafana `aicode-platform-overview` | 失败率分母改为 `COMPLETED|FAILED`；新增成功率面板 |
| Prometheus 告警 | 新增 `AiCodePendingApprovalBacklog`（>20 持续 30m） |
| 审计事件 | `FOLLOWUP_TASK_CREATE` / `FOLLOWUP_TASK_COMPLETE` / `FOLLOWUP_TASK_CANCEL` |
