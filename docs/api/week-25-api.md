# Week 25 接口文档 — 知识库 Agent 3（包装 enterprise RAG）

> 平台通用信封/错误码见 `docs/api/week-16-api.md`；enterprise 侧沿用其 `ApiResponse`。
> 本周**不新增错误码**：未绑定用 `PLATFORM_NOT_FOUND`，上游失败用 `TOOL_EXECUTION_ERROR`（502）。

## 1. enterprise 内部只读端点（8081）

> 仅供平台调用；不走 Sa-Token，由 `X-Internal-Token` 守卫（未配置即 403）。
> 令牌与平台 `PLATFORM_KNOWLEDGE_INTERNAL_TOKEN` 一致。

### 1.1 知识库清单（导入用）

`GET /api/v1/internal/knowledge/bases?platformUserId=2`

```http
X-Internal-Token: <token>
```

响应：

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "enterpriseUserId": 7,
    "bases": [
      {"knowledgeBaseId": 11, "name": "慢病指南", "description": "内部资料", "documentCount": 2}
    ]
  }
}
```

| 情况 | 状态码 |
|------|--------|
| 令牌缺失/错误/未配置 | 403 `FORBIDDEN` |
| 平台用户未建 SSO 映射 | 404 `NOT_FOUND` |
| 正常 | 200 |

### 1.2 检索切片（不生成答案）

`POST /api/v1/internal/knowledge/search`

```json
{
  "enterpriseUserId": 7,
  "knowledgeBaseId": 11,
  "query": "糖尿病随访要点",
  "topK": 4
}
```

响应：

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "enterpriseUserId": 7,
    "knowledgeBase": {"knowledgeBaseId": 11, "name": "慢病指南", "description": "内部资料"},
    "hits": [
      {
        "documentId": "D001",
        "documentName": "糖尿病指南.pdf",
        "chunkIndex": 2,
        "content": "HbA1c 每 3 个月复查",
        "score": 0.91
      }
    ]
  }
}
```

| 参数 | 必填 | 说明 |
|------|------|------|
| `enterpriseUserId` | 是 | 知识库归属的企业本地用户 |
| `knowledgeBaseId` | 是 | 知识库 ID |
| `query` | 是 | 问题（≤8000 字符） |
| `topK` | 否 | 1–50；缺省用 `rag.default-top-k` |

| 情况 | 状态码 |
|------|--------|
| 知识库不存在或不属于该企业用户 | 404 `NOT_FOUND`（不泄露存在性） |
| `query` 空白 | 400 `VALIDATION_ERROR` |
| 向量库未就绪（未装 pgvector） | 500（平台侧映射为 502） |

## 2. 平台侧：知识库绑定（8084）

### 2.1 从 enterprise 导入（仅 admin）

`POST /api/v1/platform/knowledge-bindings/import`

以**管理员自己的 SSO 映射身份**导入其名下知识库，幂等 upsert。

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "tenantKey": "default",
    "enterpriseUserId": 7,
    "imported": 1,
    "updated": 0,
    "items": [
      {"bindingId": "b1...", "knowledgeBaseId": 11, "name": "慢病指南",
       "description": "内部资料", "enabled": true, "createdAt": "2026-10-25T10:00:00Z"}
    ]
  }
}
```

| 情况 | 状态码 |
|------|--------|
| 非 admin | 403 `FORBIDDEN` |
| 管理员未建 SSO 映射 | 502 `TOOL_EXECUTION_ERROR`（上游 404） |
| enterprise 不可用/令牌未配 | 502 `TOOL_EXECUTION_ERROR` |

### 2.2 列出本租户绑定（登录即可）

`GET /api/v1/platform/knowledge-bindings`

```json
{"code": "SUCCESS", "message": "OK",
 "data": {"total": 1, "items": [{"bindingId": "b1...", "knowledgeBaseId": 11,
          "name": "慢病指南", "description": "内部资料", "enabled": true,
          "createdAt": "2026-10-25T10:00:00Z"}]},
 "traceId": "..."}
```

### 2.3 删除绑定（仅 admin，租户内）

`DELETE /api/v1/platform/knowledge-bindings/{bindingId}`

- 成功 200 `data=null`；不存在或不属于本租户 404；非 admin 403。

## 3. 平台侧：知识库问答 Agent

`POST /api/v1/platform/agents/knowledge-qa/runs`

权限：operator / admin（需 `knowledge-qa` Agent 与 `KnowledgeSearchTool` 授权）。

请求：

```json
{
  "input": {
    "knowledgeBaseId": 11,
    "question": "糖尿病患者的随访频率是多少？",
    "task": "知识库问答"
  }
}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| `knowledgeBaseId` | 是 | 必须是本租户已绑定且启用的知识库 |
| `question` | 是 | 非空，≤ `platform.knowledge.max-question-chars`（默认 4000） |
| `task` | 否 | 任务原文（仅用于记录） |

响应 `data`（执行记录）：

```json
{
  "executionId": "e1...",
  "agentKey": "knowledge-qa",
  "agentType": "KNOWLEDGE_QA",
  "status": "COMPLETED",
  "tenantKey": "default",
  "model": "deepseek-chat",
  "totalTokens": 730,
  "output": {
    "knowledgeBaseId": 11,
    "knowledgeBaseName": "慢病指南",
    "question": "糖尿病患者的随访频率是多少？",
    "answer": "建议每 3 个月复查 HbA1c [糖尿病指南.pdf#2]。",
    "citations": [
      {"documentId": "D001", "documentName": "糖尿病指南.pdf", "chunkIndex": 2,
       "content": "HbA1c 每 3 个月复查", "score": 0.91}
    ],
    "hitCount": 1,
    "degraded": false,
    "steps": [
      {"stepNo": 1, "step": "knowledge_search", "summary": "hits=1 kb=慢病指南"},
      {"stepNo": 2, "step": "answer_generation", "summary": "degraded=false"}
    ],
    "totalSteps": 2,
    "task": "知识库问答"
  }
}
```

| 情况 | 状态码 / 语义 |
|------|----------------|
| 知识库未绑定/已停用 | 404 `PLATFORM_NOT_FOUND`（**不调用模型**） |
| `question` 空白/超长、`knowledgeBaseId` 非法 | 400 `VALIDATION_ERROR` |
| viewer 调用 | 403 `FORBIDDEN` |
| enterprise 不可用/令牌未配/pgvector 缺失 | 502 `TOOL_EXECUTION_ERROR`；执行记录 `FAILED` |
| 检索 0 命中 | 200，`answer` 明确「未检索到」，`hitCount=0`，不调模型 |
| 模型不可用 | 200，`answer` 为原文摘录，`degraded=true` |

## 4. 输出与合规

- `citations[].content` 是切片原文，输出前经过平台 `Guardrail` 脱敏；
- 执行记录/审计/日志**不写完整知识库正文**（只写摘要与计数）；
- 审计动作：Agent 运行沿用 `AGENT_RUN`；工具调用走 `tool.call` span/指标，不新增审计事件。
