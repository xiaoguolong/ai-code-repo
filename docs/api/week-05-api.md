# Week 05 接口文档 — patient-agent（ReAct Agent）

> Base URL：`http://localhost:8082`  
> 响应信封：`{ "code": "...", "message": "...", "data": ... }`  
> 成功时 `code=SUCCESS`，`data` 为业务数据；失败时 `data=null`

---

## 1. 运行 Agent 任务（ReAct 循环）

### POST /api/v1/agents/runs

用户提交任务后，Agent 进入「思考 → 行动 → 观察」循环多轮迭代，直到产出最终答案；同步返回最终答案 + 完整步骤轨迹 + 汇总 Token 用量。

#### 请求参数

| 字段 | 类型 | 必填 | 限制 | 说明 |
|------|------|------|------|------|
| task | string | **是** | 非空白，最大 8000 字符 | 用户提出的任务描述 |

#### 请求示例

```bash
curl -X POST http://localhost:8082/api/v1/agents/runs \
  -H "Content-Type: application/json" \
  -d '{
    "task": "评估患者张三的出院风险，并给出随访建议"
  }'
```

#### 成功响应 200

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "taskId": "f1a2b3c4-5d6e-4f7a-9b8c-0d1e2f3a4b5c",
    "answer": "患者当前指标基本稳定，建议两周内复诊随访。",
    "steps": [
      {
        "stepNo": 1,
        "thought": "先收集患者已有指标信息",
        "action": "整理已知血压与血糖数据",
        "observation": "已获得血压 150/95、血糖 8.5"
      },
      {
        "stepNo": 2,
        "thought": "基于指标评估风险等级",
        "action": "对照风险标准进行判断",
        "observation": "属于中风险，需随访"
      }
    ],
    "totalSteps": 2,
    "model": "deepseek-chat",
    "usage": {
      "promptTokens": 120,
      "completionTokens": 340,
      "totalTokens": 460
    }
  }
}
```

> `steps` 为 Agent 每个迭代的完整轨迹；`usage` 为多轮调用汇总后的 Token 用量。

#### 失败响应

**400 - 参数校验失败**

```json
{
  "code": "VALIDATION_ERROR",
  "message": "task 不能为空",
  "data": null
}
```

**500 - Agent 未在限定步数内完成**

```json
{
  "code": "AGENT_LOOP_EXCEEDED",
  "message": "Agent 未能在限定步数内完成",
  "data": null
}
```

**500 - Agent 执行失败（如模型输出空白）**

```json
{
  "code": "AGENT_EXECUTION_ERROR",
  "message": "Agent 执行失败",
  "data": null
}
```

**502 - 模型调用失败**

```json
{
  "code": "CHAT_MODEL_ERROR",
  "message": "模型调用失败",
  "data": null
}
```

---

## 2. HTTP 状态码汇总

| 状态码 | 含义 | 触发场景 |
|--------|------|----------|
| 200 | 成功 | Agent 正常收敛返回最终答案 |
| 400 | 请求参数错误 | 任务空白（Bean Validation / 用例层校验） |
| 500 | 服务器内部错误 | Agent 超迭代、模型输出空白、未预期异常 |
| 502 | 模型网关错误 | LLM API 调用失败 |

---

## 3. 字段校验规则

```text
task : 必填，去空白后非空，最大 8000 字符
```

---

## 4. 典型调用流程示例

```bash
# 1. 简单任务（单步收敛）
curl -X POST http://localhost:8082/api/v1/agents/runs \
  -H "Content-Type: application/json" \
  -d '{"task":"评估患者张三的出院风险，并给出随访建议"}'

# 2. 复杂任务（观察多步 Thought/Action/Observation 轨迹）
curl -X POST http://localhost:8082/api/v1/agents/runs \
  -H "Content-Type: application/json" \
  -d '{"task":"已知患者血压 150/95、血糖 8.5、有糖尿病史，请分析风险并给出就医建议"}'
```
