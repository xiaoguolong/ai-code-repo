# API: Week 11 — Multi Agent 医疗助手

Base URL：`http://localhost:8084`  
Content-Type：`application/json`

## POST /api/v1/multi-agent/medical-assistant/runs

启动 Supervisor 多 Agent 流程，同步返回完整结果。

### Request

```json
{
  "patientId": "P001",
  "task": "为患者进行综合分析并生成随访计划"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `patientId` | string | 是 | 患者 ID |
| `task` | string | 否 | 任务描述；缺省为内置中文默认句 |

### Response 200

```json
{
  "code": "SUCCESS",
  "message": "ok",
  "data": {
    "runId": "550e8400-e29b-41d4-a716-446655440000",
    "patientId": "P001",
    "task": "为患者 P001 进行综合分析并生成随访计划",
    "patient": {
      "patientId": "P001",
      "name": "张三",
      "age": 62,
      "gender": "male",
      "diagnosis": "2 型糖尿病"
    },
    "metrics": {
      "systolic": 148,
      "diastolic": 92,
      "fastingGlucose": 8.6,
      "hba1c": 7.9
    },
    "riskLevel": "MEDIUM",
    "riskLabel": "中风险",
    "justification": "…",
    "report": "…",
    "followUpPlan": "…",
    "model": "deepseek-chat",
    "usage": {
      "promptTokens": 20,
      "completionTokens": 40,
      "totalTokens": 60
    },
    "steps": [
      { "stepNo": 1, "agentName": "supervisor", "summary": "route=DATA" },
      { "stepNo": 2, "agentName": "data_agent", "summary": "loaded patient and metrics" }
    ]
  }
}
```

### Errors

| HTTP | code | 场景 |
|------|------|------|
| 400 | INVALID_REQUEST | `patientId` 空白 |
| 502 | CHAT_MODEL_ERROR | LLM 调用失败 |
| 502 | TOOL_EXECUTION_ERROR | 工具执行失败 |

## 配置

| 配置项 | 说明 | 默认 |
|--------|------|------|
| `llm.model` | 模型名 | `deepseek-chat` |
| `llm.temperature` | 采样温度 | `0.0` |
| `llm.max-tokens` | 最大 token | `512` |

Prompt 文件：

- `apps/spring-ai-alibaba-agent/src/main/resources/prompts/medical-report-v1.txt`
- `apps/spring-ai-alibaba-agent/src/main/resources/prompts/medical-followup-v1.txt`
