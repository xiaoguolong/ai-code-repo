# Week 10 接口文档 — Human In The Loop（患者风险 Workflow）

> Base URL：`http://localhost:8084`
> 响应信封：`{ "code": "...", "message": "...", "data": ... }`

---

## 1. 启动 Workflow（扩展第 9 周）

### POST /api/v1/workflows/patient-risk/runs

- **LOW/MEDIUM**：与第 9 周相同，同步返回 `status=COMPLETED` 与完整 report
- **HIGH**：暂停于人工审核，`status=PENDING_APPROVAL`，`report` 为空

请求：`{ "patientId": "P001" }`

**HIGH 待审响应 200**

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "workflowId": "8f2a1b3c-4d5e-4f6a-9b0c-1d2e3f4a5b6c",
    "status": "PENDING_APPROVAL",
    "patientId": "P001",
    "patient": { "patientId": "P001", "name": "张三", "age": 62, "gender": "male", "diagnosis": "2 型糖尿病" },
    "metrics": { "systolic": 162, "diastolic": 98, "fastingGlucose": 8.6, "hba1c": 7.9 },
    "riskLevel": "HIGH",
    "riskLabel": "高风险",
    "justification": "收缩压 162 达到 160 阈值",
    "escalated": false,
    "report": "",
    "model": "deepseek-chat",
    "usage": { "promptTokens": 0, "completionTokens": 0, "totalTokens": 0 }
  }
}
```

新增字段：`status`（`COMPLETED` / `PENDING_APPROVAL` / `REJECTED`）

---

## 2. 查询 Workflow 状态

### GET /api/v1/workflows/patient-risk/runs/{workflowId}

返回与 POST /runs 相同结构的 `data`（按当前 checkpoint 状态）。

| 状态码 | 场景 |
|--------|------|
| 200 | 找到运行记录 |
| 404 | 未知 workflowId |

---

## 3. 人工审核恢复

### POST /api/v1/workflows/patient-risk/runs/{workflowId}/resume

请求：

```json
{ "approved": true }
```

| approved | 结果 status | 说明 |
|----------|-------------|------|
| true | COMPLETED | 继续 escalate → 生成 report |
| false | REJECTED | 终止，不生成 report |

**审核通过 200**

```json
{
  "code": "SUCCESS",
  "data": {
    "workflowId": "...",
    "status": "COMPLETED",
    "escalated": true,
    "report": "……",
    "usage": { "promptTokens": 10, "completionTokens": 20, "totalTokens": 30 }
  }
}
```

| 状态码 | code | 场景 |
|--------|------|------|
| 404 | WORKFLOW_NOT_FOUND | 未知 workflowId |
| 409 | WORKFLOW_NOT_PENDING | 非待审状态调用 resume |
| 400 | VALIDATION_ERROR | 缺少 approved 字段 |

---

## 4. 与第 9 周差异

| 维度 | 第 9 周 | 第 10 周 |
|------|---------|----------|
| HIGH 风险 | 同步 escalate + report | 暂停 → 人工审核 → resume |
| 响应 status | 无（隐含完成） | 显式 COMPLETED / PENDING / REJECTED |
| checkpoint | 无 | MemorySaver |
