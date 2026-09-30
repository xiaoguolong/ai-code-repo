# API: Week 15 — Agent Evaluation

Base URL：`http://localhost:8084`  
鉴权：与 Week 13 相同（需 `satoken` Header）  
Postman：`docs/postman/week-15.postman_collection.json`（导入后先跑「0. 登录」）

## 评估数据集

### GET /api/v1/platform/eval/datasets

列出预置测试集摘要。

响应示例：

```json
{
  "code": "SUCCESS",
  "data": [
    {
      "datasetKey": "guardrail-regression",
      "name": "Guardrail 回归集",
      "category": "GUARDRAIL",
      "caseCount": 3
    }
  ]
}
```

### GET /api/v1/platform/eval/datasets/{datasetKey}

返回数据集详情（用例摘要，不含 fixture 全文）。

## 批量评估

### POST /api/v1/platform/eval/datasets/{datasetKey}/runs

对指定测试集批量执行并自动评分。

| 数据集 | 执行方式 |
|--------|----------|
| guardrail-regression | 仅调用 Guardrail 校验 input |
| rag-keyword-smoke / prompt-format-smoke | fixture 输出 + 规则评分 |
| AGENT 类（含 agentKey） | 调用 PlatformAgentRunner |

权限：对数据集中涉及的每个 `agentKey` 需具备 Run 权限（admin 放行全部）。

请求示例：

```bash
curl -X POST http://localhost:8084/api/v1/platform/eval/datasets/guardrail-regression/runs \
  -H "satoken: <token>"
```

响应示例：

```json
{
  "code": "SUCCESS",
  "data": {
    "runId": "uuid",
    "userId": 1,
    "datasetKey": "guardrail-regression",
    "category": "GUARDRAIL",
    "passedCount": 3,
    "totalCount": 3,
    "passRate": 1.0,
    "startedAt": "2026-09-30T06:00:00Z",
    "finishedAt": "2026-09-30T06:00:01Z",
    "caseResults": [
      {
        "caseId": "inject-en-01",
        "passed": true,
        "score": 1.0,
        "message": "guardrail blocked: prompt injection detected in field: task",
        "actualSnippet": "prompt injection detected in field: task"
      }
    ]
  }
}
```

### GET /api/v1/platform/eval/runs/{runId}

查询评估报告（本人或 admin）。

## 评分准则

| criterionType | 说明 |
|---------------|------|
| EXACT_MATCH | 实际输出与 expected 一致 |
| CONTAINS_ALL | 实际输出包含 keywords 全部项 |
| REGEX | 实际输出匹配 pattern |
| NOT_CONTAINS | 实际输出不含 forbidden 任一项 |
| GUARDRAIL_BLOCKED | 输入应触发 GUARDRAIL_VIOLATION |

## 预置数据集

| datasetKey | category | 用途 |
|------------|----------|------|
| guardrail-regression | GUARDRAIL | Week 14 注入攻击回归 |
| rag-keyword-smoke | RAG | RAG 关键词覆盖（fixture） |
| prompt-format-smoke | PROMPT | 结构化输出 / 禁用词（fixture） |
