# Week 32 API：PDF/OCR 入库作业与节点视图

> 服务：enterprise（8081），前端经 nginx `/ent/api` 反代。所有接口要求企业端 `satoken` 头。
> 基线：Week 31；新增迁移 `V8__ingest_job.sql`。

## 1. 建入库作业

```http
POST /api/v1/knowledge-bases/{kbId}/ingest-jobs
Content-Type: multipart/form-data
satoken: <enterprise-token>
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `file` | file | PDF/图片，≤50MB；文件名必须有扩展名（pdf/png/jpg/jpeg） |

**202 Accepted**

```json
{"code":"SUCCESS","message":"OK","data":{"jobId":"7f3c…","status":"PENDING","fileName":"指南.pdf","duplicate":false}}
```

- `duplicate=true`：同一知识库下相同内容（sha256）已存在，直接返回原作业，不新建文件/文档；
- 队列满 → **429** `TOO_MANY_REQUESTS`；
- 知识库不属于当前用户 → **403**；不存在 → **404**；空文件 → **400**。

## 2. 作业列表

```http
GET /api/v1/knowledge-bases/{kbId}/ingest-jobs?limit=20
```

```json
{"data":[{"jobId":"7f3c…","fileName":"指南.pdf","status":"SUCCEEDED","pageCount":20,
  "chunkCount":143,"documentId":"ingest-7f3c…","errorMessage":"","createdAt":"2026-11-01T02:00:00Z","finishedAt":"2026-11-01T02:02:11Z"}]}
```

## 3. 作业详情（节点视图）

```http
GET /api/v1/ingest-jobs/{jobId}
```

```json
{"data":{
  "jobId":"7f3c…","fileName":"指南.pdf","status":"SUCCEEDED","pageCount":20,"chunkCount":143,
  "documentId":"ingest-7f3c…","errorMessage":"","createdAt":"…","finishedAt":"…",
  "steps":[
    {"stepNo":1,"stepKey":"upload","status":"SUCCEEDED","durationMs":5,
     "inputSummary":"file=指南.pdf，hash=…","outputSummary":"fileId=…","promptVersion":"","promptText":"",
     "promptTokens":0,"completionTokens":0,"errorMessage":""},
    {"stepNo":2,"stepKey":"ocr","status":"SUCCEEDED","durationMs":15230,
     "inputSummary":"file=指南.pdf","outputSummary":"页数=20，字符=24133"},
    {"stepNo":3,"stepKey":"clean","status":"SUCCEEDED","durationMs":8100,
     "inputSummary":"ocrChars=24133","outputSummary":"清洗后字符=23880，模型=deepseek-chat",
     "promptVersion":"v1","promptText":"你是医疗文档清洗助手……","promptTokens":1200,"completionTokens":800},
    {"stepNo":4,"stepKey":"chunk","status":"SUCCEEDED","durationMs":4,"outputSummary":"块数=143"},
    {"stepNo":5,"stepKey":"embed","status":"SUCCEEDED","durationMs":2600,"outputSummary":"向量数=143"},
    {"stepNo":6,"stepKey":"index","status":"SUCCEEDED","durationMs":180,"outputSummary":"documentId=ingest-7f3c…，块数=143"}
  ]}}
```

字段约定：

- `stepKey`：`upload|ocr|clean|chunk|embed|index`；**只有 `clean` 有 `promptText`**，其它步骤为空串；
- `promptText` 为渲染后的 prompt 原文，**永久保存在 `ingest_job_step.prompt_text`**；
- prompt 可见范围：知识库 owner + admin；其他用户 → 403；
- `status`：`PENDING|RUNNING|SUCCEEDED|FAILED|CANCELLED`；`SKIPPED` 只出现在步骤上
  （重试且已有 OCR 文本 / clean 关闭 / 取消后未执行的步骤）。

## 4. 重试

```http
POST /api/v1/ingest-jobs/{jobId}/retry
```

- 仅 `FAILED` 可重试，否则 **409** `CONFLICT`；
- 已有 `ocr_markdown` 时 OCR 步骤记 `SKIPPED`，不重复识别；
- 索引使用确定性 documentId（`ingest-<jobId>`），重试先删后建，不会产生重复文档。

## 5. 取消

```http
POST /api/v1/ingest-jobs/{jobId}/cancel
```

- 仅 `PENDING/RUNNING` 可取消，否则 **409**；
- 置 `CANCELLED`；执行线程在**当前步骤结束后**停止，后续步骤不再执行；
- 已成功/已取消的作业不会被改回其它状态。

## 6. 配置

| 配置 | 默认 | 说明 |
|------|------|------|
| `ingest.clean-enabled` | `true` | `false` 时跳过 LLM 清洗（OCR 原文直接切片，省 token） |
| `ingest.max-pages` | `200` | OCR 页数上限，超过直接失败 |
| `ingest.worker-count` | `2` | 异步线程数 |
| `ingest.queue-capacity` | `64` | 排队上限，满 429 |

## 7. 错误码

| HTTP | code | 场景 |
|------|------|------|
| 400 | `VALIDATION_ERROR` | 空文件/文件名空白 |
| 401 | `UNAUTHORIZED` | 未登录 |
| 403 | `FORBIDDEN` | 知识库/作业不属于当前用户 |
| 404 | `NOT_FOUND` | 知识库或作业不存在 |
| 409 | `CONFLICT` | 非失败作业重试 / 终态作业取消 |
| 429 | `TOO_MANY_REQUESTS` | 入库队列已满 |
| 502 | `CHAT_MODEL_ERROR` / `EMBEDDING_ERROR` / `OCR_ERROR` | 上游失败（作业落 FAILED） |
## 7. 查看文档分块（Week 32 增补）

```http
GET /api/v1/knowledge-bases/{kbId}/documents/{documentId}/chunks
```

```json
{"data":[{"chunkIndex":0,"content":"## 一、普通感冒...","chars":500}]}
```

- 按 `chunk_index` 升序；只返回正文与字符数，不返回向量；
- 知识库不属于当前用户 → 403；文档不在该库 → 404。

## 8. 只重清洗（不重跑 OCR）

```http
POST /api/v1/ingest-jobs/{jobId}/reprocess
```

- 用已保存的 `ocr_markdown` 重新执行 clean→chunk→embed→index，**不调用 OCR**（省配额）；
- 提示词升级后修复历史文档；没有 OCR 文本 → 409；
- 索引先删旧切片再删文档（外键顺序），重建后 `documentId` 不变。