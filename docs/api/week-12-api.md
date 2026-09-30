# API: Week 12 — Agent 平台 V1

Base URL：`http://localhost:8084`  
前缀：`/api/v1/platform`

## Agent

### GET /agents

列出已注册 Agent（含 seed）。

### POST /agents

```json
{
  "agentKey": "custom-agent",
  "name": "自定义 Agent",
  "description": "说明",
  "agentType": "FRAMEWORK_REACT"
}
```

### GET /agents/{agentKey}

### PUT /agents/{agentKey}/config

```json
{
  "enabled": true,
  "maxIterations": 5,
  "temperature": 0.0,
  "model": "deepseek-chat"
}
```

## Tool

### GET /tools

### POST /tools

```json
{
  "toolKey": "PatientLookupTool",
  "toolName": "PatientLookupTool",
  "description": "按患者编号查询"
}
```

### GET /tools/{toolKey}

## Workflow

### GET /workflows

### POST /workflows

```json
{
  "workflowKey": "patient-risk",
  "name": "患者风险分析",
  "description": "固定 Workflow + HITL",
  "boundAgentKey": "patient-risk"
}
```

### GET /workflows/{workflowKey}

## Execution

### GET /executions

查询执行记录列表（按 startedAt 降序）。

### GET /executions/{executionId}

### POST /agents/{agentKey}/runs

经平台调度 Agent 并写入执行记录。

**FRAMEWORK_REACT** 示例：

```json
{ "input": { "task": "查询 P001 的健康指标" } }
```

**PATIENT_RISK_WORKFLOW / MEDICAL_ASSISTANT** 示例：

```json
{ "input": { "patientId": "P001", "task": "可选" } }
```

响应 `data` 含 `executionId`、`status`、`output`、`usage` 等。

## Errors

| HTTP | code | 场景 |
|------|------|------|
| 404 | PLATFORM_NOT_FOUND | agent/tool/workflow/execution 不存在 |
| 409 | PLATFORM_CONFLICT | agentKey 重复注册 |
| 400 | VALIDATION_ERROR | 参数非法 / Agent 已禁用 |
| 502 | CHAT_MODEL_ERROR / TOOL_EXECUTION_ERROR | 运行时失败（记录 status=FAILED） |

## Seed（启动后可用）

| agentKey | agentType |
|----------|-----------|
| framework-react | FRAMEWORK_REACT |
| patient-risk | PATIENT_RISK_WORKFLOW |
| medical-assistant | MEDICAL_ASSISTANT |

| toolKey | toolName |
|---------|----------|
| PatientLookupTool | PatientLookupTool |
| HealthMetricTool | HealthMetricTool |

| workflowKey | boundAgentKey |
|-------------|---------------|
| patient-risk-workflow | patient-risk |
| medical-assistant-workflow | medical-assistant |
