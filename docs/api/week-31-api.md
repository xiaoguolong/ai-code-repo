# Week 31 接口文档 — SSE 流式执行

> 只新增一个端点；请求体与同步端点 `POST /agents/{agentKey}/runs` 完全一致。
> 通用信封见 `docs/api/week-16-api.md`（`code/message/data/traceId`）。

## 1. 端点

`POST /api/v1/platform/agents/{agentKey}/runs/stream`

| 项 | 值 |
|----|----|
| 鉴权 | `Authorization: Bearer <platform token>`（Sa-Token，与同步端点一致） |
| 请求头 | `Content-Type: application/json`、`Accept: text/event-stream, application/json` |
| 请求体 | `{"input":{...}}`（知识库问答 `knowledgeBaseId`+`question`；患者分析 `patientId`+可选 `task`） |
| 成功 | `200` + `text/event-stream`，事件见第 3 节 |
| 支持范围 | **仅** `knowledge-qa`、`patient-analysis`；其它 Agent → 400 |

> `Accept` 必须同时包含 `application/json`：预检失败要能回 JSON 信封；
> 只声明 `text/event-stream` 时部分客户端会把 4xx 当 406。

支持流式的 Agent 由 Agent 类型决定（`KNOWLEDGE_QA` / `PATIENT_ANALYSIS`），不是按 key 白名单。

## 2. 预检错误（HTTP 状态 + JSON 信封，不是 200-流内报错）

| 场景 | HTTP | code |
|------|------|------|
| 未登录/过期 | 401 | `UNAUTHORIZED` |
| 无权运行该 Agent / 数据域越权 | 403 | `FORBIDDEN` |
| Agent 不存在 | 404 | `PLATFORM_NOT_FOUND` |
| Agent 不支持流式 / 入参非法 / Guardrail 拦截 | 400 | `VALIDATION_ERROR` / `GUARDRAIL_VIOLATION` |
| 流式并发已满（默认 4） | 429 | `RATE_LIMITED` |
| 流式开关关闭 | 503 | `STREAM_DISABLED` |

```json
{"code":"VALIDATION_ERROR","message":"agent does not support streaming: medical-assistant","data":null,"traceId":"..."}
```

429/503/404 都发生在**建执行记录之前**：不会留下"排队中"的 RUNNING 记录。

## 3. 事件

响应头：`Content-Type: text/event-stream;charset=UTF-8`、`Cache-Control: no-cache`、
`X-Accel-Buffering: no`。每 15s 一行注释心跳 `: ping`。

### 3.1 `meta`（第一条）

```text
event:meta
data: {"executionId":"c1f0…","agentKey":"knowledge-qa","startedAt":"2026-11-01T03:00:00Z","traceId":"…"}
```

此时执行记录已落 `RUNNING`；前端可显示执行 ID 或跳详情。

### 3.2 `delta`（0..N 条，顺序保证）

```text
event:delta
data: {"text":"## 答案\n糖尿病患者的随访频率建议为每 3 个月一次"}
```

已按 32 字符 carry 做 PII 掩码；空增量不会下发。

### 3.3 `final`（成功终态，权威结果）

```text
event:final
data: {"code":"SUCCESS","message":"OK","data":{ …ExecutionRecordResponse… },"traceId":"…"}
```

`data` 与同步端点返回**完全同结构**（`executionId/status/outputJson/model/usage/…`）；
`status` 为 `COMPLETED` 或 `PENDING_APPROVAL`（HIGH 风险患者分析，`outputJson` 里带 `approvalId`）。
前端收到后应**覆盖**预览文本。

### 3.4 `error`（执行期失败）

```text
event:error
data: {"code":"CHAT_MODEL_ERROR","message":"模型调用失败","traceId":"…","executionId":"…"}
```

此时记录已落 `FAILED`。注意：HTTP 状态已经是 200，错误只在流内表达。

### 3.5 取消

- 用户点"停止" → 前端 `AbortController.abort()`，连接关闭；
- 服务端在下一次 `send`/心跳失败时感知，`takeWhile` 退订模型，记录落 `CANCELLED`；
- 不发送 `final`/`error`（客户端已经走了）；
- 记录 `errorMessage="cancelled by client"`，不计失败率。

## 4. curl 示例

```bash
TOKEN=$(curl -s -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' \
  http://127.0.0.1:8084/api/v1/platform/auth/login | python3 -c 'import json,sys;print(json.load(sys.stdin)["data"]["token"])')

# 知识库问答（直连平台）
curl -N -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream, application/json' \
  -d '{"input":{"knowledgeBaseId":1,"question":"糖尿病患者的随访频率是多少？"}}' \
  http://127.0.0.1:8084/api/v1/platform/agents/knowledge-qa/runs/stream

# 患者分析（HIGH → final 为 PENDING_APPROVAL）
curl -N -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream, application/json' \
  -d '{"input":{"patientId":"P002"}}' \
  http://127.0.0.1:8084/api/v1/platform/agents/patient-analysis/runs/stream

# 走 nginx（验证未被缓冲：首字应与直连接近）
curl -N … http://127.0.0.1/api/v1/platform/agents/knowledge-qa/runs/stream
```

## 5. 前端契约

- 用 `fetch + ReadableStream`，**不用** `EventSource`：后者不能 POST、不能带 `Authorization` 头
  （令牌只能塞 query，会进访问日志）；
- 解析器必须容忍任意分帧（一次 read 可能半帧/多帧）、CRLF、注释行、多行 `data:`；
- `401` 统一清会话回登录；`404/405/429/503` 且**尚未收到 meta**时，可回退一次同步端点；
  收到 meta 之后不再回退（避免重复执行）。

## 6. 限制与已知边界

| 项 | 现状 |
|---|---|
| 断线续传 | 不支持（无 `Last-Event-ID`/事件重放）；断连即取消 |
| 多实例 | 不支持流跨实例（单实例部署）；扩容前需广播/粘性会话 |
| OpenAI 兼容适配器 | 未实现增量解析：切换 `FRAMEWORK_MODEL_PROVIDER=openai-compatible` 时退化为单 chunk |
| 流式 usage | 依赖 `stream-usage: true`；厂商不返回末包 usage 时为 0 |
| PII 预览 | 32 字符 carry；超长邮箱 local part 跨块可能漏掩前 2 字符 |
| 并发 | 默认 4 条流；超出 429，不排队 |
| 超时 | 300s；超时按取消处理 |
