# Week 07 接口文档 — patient-agent（Agent Memory）

> Base URL：`http://localhost:8083`
> 响应信封：`{ "code": "...", "message": "...", "data": ... }`
> 成功时 `code=SUCCESS`，`data` 为业务数据；失败时 `data=null`

---

## 1. 运行 Agent 任务（带记忆）

### POST /api/v1/agents/runs

提交任务后，Agent 先加载同一 `sessionId` 的历史对话（短期记忆），并语义检索相关历史任务（长期记忆），把两者作为上下文注入后再进入原生 Function Calling 循环；执行完成后写回短期记忆并保存本次「任务 + 结论」到长期记忆。

可用工具：`PatientTool`（患者信息）、`ReportTool`（检查报告）、`HealthDataTool`（健康指标）、`OrderTool`（医嘱）。

#### 请求参数

| 字段 | 类型 | 必填 | 限制 | 说明 |
|------|------|------|------|------|
| sessionId | string | 否 | 最大 100 字符 | 会话标识；为空时服务端生成并回传，携带同一值即获得多轮上下文 |
| task | string | **是** | 非空白，最大 8000 字符 | 用户提出的任务描述 |

#### 请求示例

```bash
# 第一轮：不带 sessionId，服务端生成
curl -X POST http://localhost:8083/api/v1/agents/runs \
  -H "Content-Type: application/json" \
  -d '{ "task": "评估患者 P001 的出院风险，结合检查报告、健康指标和医嘱" }'

# 第二轮：带上一步返回的 sessionId，延续上下文
curl -X POST http://localhost:8083/api/v1/agents/runs \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "5b1f0c2e-...",
    "task": "那他的血压和血糖控制得怎么样？"
  }'
```

#### 成功响应 200

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "taskId": "f1a2b3c4-5d6e-4f7a-9b8c-0d1e2f3a4b5c",
    "sessionId": "5b1f0c2e-8a41-4c1d-9c2f-1a2b3c4d5e6f",
    "answer": "患者张三（P001）近期血压 158/95、空腹血糖 8.6，均未达标，建议调整降压与降糖方案并门诊随访。",
    "steps": [
      {
        "stepNo": 1,
        "toolName": "PatientTool",
        "arguments": "{\"patientId\":\"P001\"}",
        "observation": "{\"id\":\"P001\",\"name\":\"张三\",\"age\":68,\"gender\":\"男\",\"diagnosis\":\"高血压、2 型糖尿病\"}"
      }
    ],
    "totalSteps": 1,
    "recalledMemories": 2,
    "model": "deepseek-chat",
    "usage": {
      "promptTokens": 180,
      "completionTokens": 220,
      "totalTokens": 400
    }
  }
}
```

> `sessionId` 为本次会话标识，请在下一次调用回传以复用上下文；`recalledMemories` 为本次语义召回的历史任务条数（0 表示无相关历史）。

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
| 400 | 请求参数错误 | 任务空白、sessionId 超长（Bean Validation / 用例层校验） |
| 500 | 服务器内部错误 | Agent 超迭代、模型输出空白、未预期异常 |
| 502 | 上游服务错误 | LLM API 调用失败、工具执行失败 |

---

## 3. 记忆行为说明

| 记忆类型 | 作用域 | 存储 | 生效方式 |
|----------|--------|------|----------|
| 短期记忆 | 同一 `sessionId` | `memory`（进程内，默认）或 `redis` | 下一轮自动加载最近 `memory.max-messages` 条 user/assistant 消息 |
| 长期记忆 | 全局（单用户演示） | `memory`（进程内向量） | 每次任务语义检索最相关的 `memory.long-term-top-k` 条历史「任务 + 结论」注入上下文 |

- 生产启用 Redis：`MEMORY_SHORT_TERM_PROVIDER=redis`，并配置 `spring.data.redis.*`。
- 语义检索质量依赖 Embedding：默认 `embedding.provider=hashing`（离线确定性），生产切 `openai` 提升召回效果。

## 4. 字段校验规则

```text
sessionId : 可选，最大 100 字符
task      : 必填，去空白后非空，最大 8000 字符
```
