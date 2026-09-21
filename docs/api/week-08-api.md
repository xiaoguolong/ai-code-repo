# Week 08 接口文档 — spring-ai-alibaba-agent（Spring AI Alibaba Graph Agent）

> Base URL：`http://localhost:8084`
> 响应信封：`{ "code": "...", "message": "...", "data": ... }`
> 成功时 `code=SUCCESS`，`data` 为业务数据；失败时 `data=null`

---

## 1. 运行 Graph Agent 任务

### POST /api/v1/framework/agents/runs

提交任务后，由 Spring AI Alibaba Graph 编排 `agent →(条件)→ tools → agent` 循环：`agent` 节点经 Spring AI（OpenAI 兼容端点指向 DeepSeek）调用模型并携带工具定义；模型要求工具时进入 `tools` 节点执行并把结果回填；模型给出最终答案时结束。

可用工具（演示数据）：

| 工具名 | 说明 | 参数 |
|--------|------|------|
| `PatientLookupTool` | 按患者编号查询患者基础信息 | `patientId`（string，必填） |
| `HealthMetricTool` | 按患者编号查询最新健康指标（血压、血糖、糖化血红蛋白） | `patientId`（string，必填） |

#### 请求参数

| 字段 | 类型 | 必填 | 限制 | 说明 |
|------|------|------|------|------|
| task | string | **是** | 非空白，最大 8000 字符 | 用户任务描述 |

#### 请求示例

```bash
curl -X POST http://localhost:8084/api/v1/framework/agents/runs \
  -H "Content-Type: application/json" \
  -d '{ "task": "分析患者 P001 的健康指标，给出随访建议" }'
```

#### 成功响应 200

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "taskId": "3f0c1d2e-4a5b-4c6d-8e9f-0a1b2c3d4e5f",
    "answer": "患者张三（P001）血压 148/92、空腹血糖 8.6、糖化血红蛋白 7.9，均未达标，建议调整降压与降糖方案并门诊随访。",
    "steps": [
      {
        "stepNo": 1,
        "toolName": "HealthMetricTool",
        "arguments": "{\"patientId\":\"P001\"}",
        "observation": "{\"patientId\":\"P001\",\"systolic\":148,\"diastolic\":92,\"fastingGlucose\":8.6,\"hba1c\":7.9}"
      }
    ],
    "totalSteps": 1,
    "model": "deepseek-chat",
    "usage": {
      "promptTokens": 210,
      "completionTokens": 160,
      "totalTokens": 370
    }
  }
}
```

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

## 2. MCP 工具暴露（SSE）

`spring-ai-starter-mcp-server-webmvc` 将 `McpToolCallbackProvider` 注册的两个 demo 工具通过 MCP 暴露：

| 项 | 值 |
|----|----|
| SSE 握手端点 | `GET /sse` |
| 消息端点 | `POST /mcp/message` |
| 服务名 | `patient-tools-mcp` |
| 暴露工具 | `PatientLookupTool`、`HealthMetricTool` |

> 用支持 MCP 的客户端（如 Claude Desktop 等）连接 `http://localhost:8084/sse` 即可发现并调用上述工具。本模块只做服务端暴露，未接远端 MCP Client。

---

## 3. 模型通道与适配器切换

本模块默认 `framework.model-provider=spring-ai`，模型经 Spring AI 的 OpenAI 兼容通道对接 DeepSeek：

| 配置 | 说明 | 默认 |
|------|------|------|
| `SPRING_AI_OPENAI_BASE_URL` / `spring.ai.openai.base-url` | Spring AI OpenAI 兼容端点，**只写主机名，不带 `/v1`** | `https://api.deepseek.com` |
| `spring.ai.openai.api-key` | 密钥，仅环境变量 `LLM_API_KEY` | 空（未配置则模型调用 502） |
| `spring.ai.openai.chat.options.model` | 模型名，环境变量 `LLM_MODEL` | `deepseek-chat` |
| `framework.model-provider` | `spring-ai`（Spring AI 适配器）/ `openai-compatible`（第 1 周 RestClient 适配器） | `spring-ai` |
| `framework.agent.max-iterations` | 模型带工具循环上限 | 5 |

> 环境变量模板见 `apps/spring-ai-alibaba-agent/.env.example`：复制为 `.env` 后填写（`.env` 已被 `.gitignore` 忽略，不入库）。

---

## 4. HTTP 状态码汇总

| 状态码 | 含义 | 触发场景 |
|--------|------|----------|
| 200 | 成功 | Graph Agent 正常收敛返回最终答案 |
| 400 | 请求参数错误 | 任务空白、长度超限（Bean Validation / 用例层校验） |
| 500 | 服务器内部错误 | Agent 超迭代、模型输出空白、未预期异常 |
| 502 | 上游服务错误 | LLM API 调用失败、工具执行失败 |
