# Week 06 接口文档 — patient-agent（Tool Calling）

> Base URL：`http://localhost:8082`  
> 响应信封：`{ "code": "...", "message": "...", "data": ... }`  
> 成功时 `code=SUCCESS`，`data` 为业务数据；失败时 `data=null`

---

## 1. 运行 Agent 任务（原生 Function Calling）

### POST /api/v1/agents/runs

用户提交任务后，Agent 进入原生 Function Calling 循环：模型选工具 → 执行工具 → 回填观察 → 再推理，直到给出最终答案；同步返回最终答案 + 完整工具调用轨迹 + 汇总 Token 用量。

可用工具：`PatientTool`（患者信息）、`ReportTool`（检查报告）、`HealthDataTool`（健康指标）、`OrderTool`（医嘱）。

#### 请求参数

| 字段 | 类型 | 必填 | 限制 | 说明 |
|------|------|------|------|------|
| task | string | **是** | 非空白，最大 8000 字符 | 用户提出的任务描述 |

#### 请求示例

```bash
curl -X POST http://localhost:8082/api/v1/agents/runs \
  -H "Content-Type: application/json" \
  -d '{
    "task": "查询患者 P001 的信息和最近检查报告，评估出院风险"
  }'
```

#### 成功响应 200

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "taskId": "f1a2b3c4-5d6e-4f7a-9b8c-0d1e2f3a4b5c",
    "answer": "患者张三（P001），68 岁男性，诊断高血压、2 型糖尿病。近期血压 158/95、空腹血糖 8.6，心电图提示偶发室性早搏，建议门诊随访调整用药。",
    "steps": [
      {
        "stepNo": 1,
        "toolName": "PatientTool",
        "arguments": "{\"patientId\":\"P001\"}",
        "observation": "{\"id\":\"P001\",\"name\":\"张三\",\"age\":68,\"gender\":\"男\",\"diagnosis\":\"高血压、2 型糖尿病\"}"
      },
      {
        "stepNo": 2,
        "toolName": "ReportTool",
        "arguments": "{\"patientId\":\"P001\"}",
        "observation": "[{\"id\":\"R001\",\"patientId\":\"P001\",\"type\":\"血常规\",\"summary\":\"白细胞偏高，提示感染\",\"issuedAt\":\"2026-09-01\"}]"
      }
    ],
    "totalSteps": 2,
    "model": "deepseek-chat",
    "usage": {
      "promptTokens": 180,
      "completionTokens": 420,
      "totalTokens": 600
    }
  }
}
```

> `steps` 为 Agent 每次工具调用的完整轨迹（工具名 + 参数 + 观察结果）；`usage` 为多轮模型调用汇总后的 Token 用量。

#### 失败响应

**400 - 参数校验失败**

```json
{ "code": "VALIDATION_ERROR", "message": "task 不能为空", "data": null }
```

**500 - Agent 未在限定步数内完成**

```json
{ "code": "AGENT_LOOP_EXCEEDED", "message": "Agent 未能在限定步数内完成", "data": null }
```

**500 - Agent 执行失败（如模型输出空白）**

```json
{ "code": "AGENT_EXECUTION_ERROR", "message": "Agent 执行失败", "data": null }
```

**502 - 模型调用失败**

```json
{ "code": "CHAT_MODEL_ERROR", "message": "模型调用失败", "data": null }
```

**502 - 工具执行失败（未知工具/参数非法）**

```json
{ "code": "TOOL_EXECUTION_ERROR", "message": "工具执行失败", "data": null }
```

---

## 2. HTTP 状态码汇总

| 状态码 | 含义 | 触发场景 |
|--------|------|----------|
| 200 | 成功 | Agent 正常收敛返回最终答案 |
| 400 | 请求参数错误 | 任务空白（Bean Validation / 用例层校验） |
| 500 | 服务器内部错误 | Agent 超迭代、模型输出空白、未预期异常 |
| 502 | 上游服务错误 | LLM API 调用失败、工具执行失败 |

---

## 3. 字段校验规则

```text
task : 必填，去空白后非空，最大 8000 字符
```

---

## 4. 典型调用流程示例

```bash
# 1. 查询患者信息
curl -X POST http://localhost:8082/api/v1/agents/runs \
  -H "Content-Type: application/json" \
  -d '{"task":"查询患者 P001 的基本信息"}'

# 2. 组合多个数据源（患者 + 报告 + 指标 + 医嘱）
curl -X POST http://localhost:8082/api/v1/agents/runs \
  -H "Content-Type: application/json" \
  -d '{"task":"评估患者 P001 的出院风险，结合检查报告、健康指标和医嘱给出随访建议"}'

# 3. 查询不存在的患者（模型应如实说明）
curl -X POST http://localhost:8082/api/v1/agents/runs \
  -H "Content-Type: application/json" \
  -d '{"task":"查询患者 P999 的健康指标"}'
```
