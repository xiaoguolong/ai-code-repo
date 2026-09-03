# Week 02 接口文档 — spring-ai-demo

> Base URL：`http://localhost:8080`  
> 响应信封：`{ "data": ..., "error": { "code": "...", "message": "..." } }`  
> 成功时只返回 `data`，失败时只返回 `error`

---

## 1. 发起聊天

### POST /api/v1/chats

#### 请求参数

| 字段 | 类型 | 必填 | 限制 | 说明 |
|------|------|------|------|------|
| sessionId | string | 否 | 最大 64 字符 | 会话标识，空则自动生成 |
| message | string | **是** | 非空白，最大 8000 字符 | 用户输入 |
| template | string | 否 | `A-Za-z0-9._-` | 模板名，空则用默认 `system` |
| responseFormat | string | 否 | `text` / `json` | 输出格式，默认 `text` |

#### 请求示例

```bash
curl -X POST http://localhost:8080/api/v1/chats \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "session-001",
    "message": "你好",
    "template": "system",
    "responseFormat": "text"
  }'
```

#### 成功响应 200

```json
{
  "data": {
    "sessionId": "session-001",
    "messageId": "550e8400-e29b-41d4-a716-446655440000",
    "content": "你好！有什么可以帮你的？",
    "usage": {
      "promptTokens": 12,
      "completionTokens": 34,
      "totalTokens": 46
    },
    "payload": null
  }
}
```

#### JSON 模式响应 200

```bash
curl -X POST http://localhost:8080/api/v1/chats \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "session-001",
    "message": "用 JSON 介绍你自己",
    "template": "json",
    "responseFormat": "json"
  }'
```

```json
{
  "data": {
    "sessionId": "session-001",
    "messageId": "...",
    "content": "{\"name\":\"AI\",\"language\":\"Chinese\"}",
    "usage": {
      "promptTokens": 15,
      "completionTokens": 20,
      "totalTokens": 35
    },
    "payload": {
      "name": "AI",
      "language": "Chinese"
    }
  }
}
```

#### 失败响应

**400 - 参数校验失败**

```json
{
  "error": {
    "code": "validation_error",
    "message": "message must not be blank"
  }
}
```

**400 - 模板不存在**

```json
{
  "error": {
    "code": "validation_error",
    "message": "prompt template not found: unknown"
  }
}
```

**422 - JSON 解析失败**

```json
{
  "error": {
    "code": "structured_output_error",
    "message": "model output is not valid JSON object: ..."
  }
}
```

**502 - 模型调用失败**

```json
{
  "error": {
    "code": "chat_model_error",
    "message": "LLM call failed"
  }
}
```

---

## 2. 查询会话历史消息

### GET /api/v1/chats/{sessionId}/messages

#### 路径参数

| 字段 | 类型 | 说明 |
|------|------|------|
| sessionId | string | 会话标识 |

#### 请求示例

```bash
curl http://localhost:8080/api/v1/chats/session-001/messages
```

#### 成功响应 200

```json
{
  "data": [
    { "role": "user", "content": "你好" },
    { "role": "assistant", "content": "你好！有什么可以帮你的？" }
  ]
}
```

---

## 3. 查询 Prompt 模板列表

### GET /api/v1/prompts

#### 请求示例

```bash
curl http://localhost:8080/api/v1/prompts
```

#### 成功响应 200

```json
{
  "data": {
    "prompts": [
      { "name": "coder", "version": "v1" },
      { "name": "json", "version": "v1" },
      { "name": "qa", "version": "v1" },
      { "name": "system", "version": "v1" },
      { "name": "translator", "version": "v1" }
    ]
  }
}
```

#### 可用模板列表

| 模板名 | 角色 | 说明 | 典型用法 |
|--------|------|------|----------|
| `system` | 默认助手 | 通用问答，跟随用户语言 | `template: "system"` |
| `json` | 结构化输出 | 要求模型返回单个 JSON 对象 | `template: "json"` + `responseFormat: "json"` |
| `translator` | 翻译 | 中英互译，只输出译文 | `template: "translator"` |
| `coder` | 代码助手 | 输出可编译的 Java 代码片段 | `template: "coder"` |
| `qa` | 企业知识库 | 基于已知知识回答，不编造 | `template: "qa"` |

#### 不同角色调用示例

```bash
# 翻译角色
curl -X POST http://localhost:8080/api/v1/chats \
  -H "Content-Type: application/json" \
  -d '{"message":"把这句话翻译成英文：企业级 AI Agent","template":"translator"}'

# 代码助手角色
curl -X POST http://localhost:8080/api/v1/chats \
  -H "Content-Type: application/json" \
  -d '{"message":"用 Java 17 写单例","template":"coder"}'

# 知识库角色
curl -X POST http://localhost:8080/api/v1/chats \
  -H "Content-Type: application/json" \
  -d '{"message":"我们公司的请假流程是什么？","template":"qa"}'
```

---

## 4. 查询 Token 累计

### GET /api/v1/token-stats

#### 请求示例

```bash
curl http://localhost:8080/api/v1/token-stats
```

#### 成功响应 200

```json
{
  "data": {
    "requestCount": 3,
    "promptTokens": 36,
    "completionTokens": 102,
    "totalTokens": 138
  }
}
```

---

## 5. HTTP 状态码汇总

| 状态码 | 含义 | 触发场景 |
|--------|------|----------|
| 200 | 成功 | 所有 GET / 正常 POST |
| 400 | 请求参数错误 | Bean Validation 失败、模板不存在 |
| 422 | 语义错误 | JSON 结构化输出无法解析 |
| 500 | 服务器内部错误 | 未预期异常 |
| 502 | 模型网关错误 | LLM API 调用失败 |

---

## 6. 字段校验规则

```text
sessionId        : 可选，最大 64 字符
message          : 必填，去重后非空，最大 8000 字符
template         : 可选，只允许字母、数字、点、横线
responseFormat   : 可选，只能是 text 或 json
```

---

## 7. 典型调用流程示例

```bash
# 1. 查看可用模板
curl http://localhost:8080/api/v1/prompts

# 2. 发起第一轮对话
curl -X POST http://localhost:8080/api/v1/chats \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"s1","message":"我叫张三"}'

# 3. 同一会话第二轮（带上下文）
curl -X POST http://localhost:8080/api/v1/chats \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"s1","message":"我叫什么名字"}'

# 4. 查看历史
curl http://localhost:8080/api/v1/chats/s1/messages

# 5. 查看 Token 统计
curl http://localhost:8080/api/v1/token-stats
```
