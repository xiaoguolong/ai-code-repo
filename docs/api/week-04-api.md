# Week 04 接口文档 — enterprise-knowledge-agent（登录 / 知识库 / 文档 / 问答 / 历史 / Token / 文件 / OCR）

> Base URL：`http://localhost:8081`  
> 响应信封：`{ "code": "...", "message": "...", "data": ... }`  
> 成功时 `code=SUCCESS`，`data` 为业务数据；失败时 `data=null`  
> 鉴权：Sa-Token，登录后需在请求头携带 `satoken: <token>`（`/auth/register`、`/auth/login`、`/files/{fileId}` 除外）

---

## 1. 注册与登录

### POST /api/v1/auth/register（公开）

| 字段 | 类型 | 必填 | 限制 | 说明 |
|------|------|------|------|------|
| username | string | **是** | 非空白，最大 64 | 用户名 |
| password | string | **是** | 6 ~ 128 | 密码（服务端加盐 md5 哈希，不回显） |

```bash
curl -X POST http://localhost:8081/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"pminspect","password":"pass123"}'
```

成功响应 **201**：

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": { "userId": 1, "username": "pminspect" }
}
```

失败响应 **409**（用户名已存在）：

```json
{ "code": "CONFLICT", "message": "username already exists", "data": null }
```

### POST /api/v1/auth/login（公开）

```bash
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"pminspect","password":"pass123"}'
```

成功响应 **200**：

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "token": "5a9c6b1e-...-patient-agent-token",
    "user": { "userId": 1, "username": "pminspect" }
  }
}
```

> 后续所有需登录接口在请求头加 `satoken: <token>`。

失败响应 **401**（用户名或密码错误）：

```json
{ "code": "UNAUTHORIZED", "message": "用户名或密码错误", "data": null }
```

### POST /api/v1/auth/logout（登录）

注销当前 token，返回 200 `{ "code":"SUCCESS","message":"OK","data":null }`。

### GET /api/v1/auth/me（登录）

```bash
curl http://localhost:8081/api/v1/auth/me -H "satoken: {{token}}"
```

```json
{ "code": "SUCCESS", "message": "OK", "data": { "userId": 1, "username": "pminspect" } }
```

---

## 2. 知识库管理（登录）

### POST /api/v1/knowledge-bases

| 字段 | 类型 | 必填 | 限制 | 说明 |
|------|------|------|------|------|
| name | string | **是** | 非空白，最大 255 | 知识库名 |
| description | string | 否 | 最大 1000 | 描述 |

```bash
curl -X POST http://localhost:8081/api/v1/knowledge-bases \
  -H "Content-Type: application/json" -H "satoken: {{token}}" \
  -d '{"name":"测试知识库","description":"Postman 调试用"}'
```

成功响应 **201**：

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": { "id": 1, "name": "测试知识库", "description": "Postman 调试用", "createdAt": "2026-09-04T00:00:00Z" }
}
```

### GET /api/v1/knowledge-bases

列出当前用户的知识库，返回 `data` 为数组（同单个结构）。

### GET /api/v1/knowledge-bases/{id}

详情；非本人返回 **403** `{"code":"FORBIDDEN","message":"无权访问该资源","data":null}`。

### PUT /api/v1/knowledge-bases/{id}

更新 name/description，返回更新后的对象。

### DELETE /api/v1/knowledge-bases/{id}

删除（级联文档与切片），返回 200 `data:null`。

---

## 3. 文档管理（登录）

### POST /api/v1/knowledge-bases/{kbId}/documents

| 字段 | 类型 | 必填 | 限制 | 说明 |
|------|------|------|------|------|
| name | string | **是** | 非空白，最大 255 | 文档名 |
| content | string | **是** | 非空白，最大 100000 | 正文（纯文本，切片向量化入库） |

```bash
curl -X POST http://localhost:8081/api/v1/knowledge-bases/1/documents \
  -H "Content-Type: application/json" -H "satoken: {{token}}" \
  -d '{"name":"产品说明","content":"企业知识库助手是一款基于检索增强生成的应用..."}'
```

成功响应 **200**：

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": { "documentId": "uuid", "name": "产品说明", "chunkCount": 4 }
}
```

### GET /api/v1/knowledge-bases/{kbId}/documents

返回 `data` 数组：

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": [
    { "documentId": "uuid", "name": "产品说明", "chunkCount": 4, "createdAt": "2026-09-04T00:00:00Z" }
  ]
}
```

### DELETE /api/v1/knowledge-bases/{kbId}/documents/{docId}

删除文档，返回 200 `data:null`。

---

## 4. 知识库问答与历史（登录）

### POST /api/v1/knowledge-bases/{kbId}/chats

| 字段 | 类型 | 必填 | 限制 | 说明 |
|------|------|------|------|------|
| question | string | **是** | 非空白，最大 8000 | 问题 |
| sessionId | string | 否 | — | 会话标识，空则自动生成 |
| topK | int | 否 | 1 ~ 50 | 检索条数，缺省用配置默认 4 |

```bash
curl -X POST http://localhost:8081/api/v1/knowledge-bases/1/chats \
  -H "Content-Type: application/json" -H "satoken: {{token}}" \
  -d '{"question":"企业知识库助手能做什么？","topK":4}'
```

成功响应 **200**：

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "sessionId": "uuid",
    "answer": "企业知识库助手是一款基于检索增强生成的应用...",
    "sources": [
      { "chunkIndex": 0, "content": "企业知识库助手是一款...", "score": 0.95 }
    ]
  }
}
```

> 知识库为空时 `sources` 返回 `[]`，模型按 rag 模板提示回答。

### GET /api/v1/chats/{sessionId}/messages

```bash
curl http://localhost:8081/api/v1/chats/{{sessionId}}/messages -H "satoken: {{token}}"
```

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": [
    { "role": "USER", "content": "企业知识库助手能做什么？" },
    { "role": "ASSISTANT", "content": "企业知识库助手是一款..." }
  ]
}
```

---

## 5. Token 统计（登录）

### GET /api/v1/token-stats

```bash
curl http://localhost:8081/api/v1/token-stats -H "satoken: {{token}}"
```

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "requestCount": 3,
    "promptTokens": 120,
    "completionTokens": 340,
    "totalTokens": 460
  }
}
```

---

## 6. 文件上传与访问

### POST /api/v1/files（登录，multipart）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | file | **是** | 任意本地文件 |

```bash
curl -X POST http://localhost:8081/api/v1/files \
  -H "satoken: {{token}}" -F "file=@./sample.png"
```

成功响应 **200**：

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "fileId": "uuid",
    "url": "http://localhost:8081/api/v1/files/uuid",
    "originalName": "sample.png",
    "contentType": "image/png",
    "sizeBytes": 10240
  }
}
```

### GET /api/v1/files/{fileId}（公开）

直接返回文件字节流（供页面 `<img>` / 下载使用），不套信封。

---

## 7. OCR 识别（登录）

### POST /api/v1/ocr（URL 或 Base64）

| 字段 | 类型 | 必填 | 限制 | 说明 |
|------|------|------|------|------|
| file | string | **是** | 非空白 | 图片/PDF 的 URL 或 Base64 |
| fileType | string | 否 | pdf / image | URL 可不传（服务端推断）；Base64 建议传 |

```bash
curl -X POST http://localhost:8081/api/v1/ocr \
  -H "Content-Type: application/json" -H "satoken: {{token}}" \
  -d '{"file":"https://example.com/doc.pdf","fileType":"pdf"}'
```

成功响应 **200**：

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": { "text": "识别出的 markdown 正文（图片占位符已替换为访问路径）" }
}
```

### POST /api/v1/ocr/upload（multipart）

```bash
curl -X POST http://localhost:8081/api/v1/ocr/upload \
  -H "satoken: {{token}}" -F "file=@./sample.pdf"
```

响应同上 `{ "data": { "text": "..." } }`。

---

## 8. HTTP 状态码汇总

| 状态码 | 含义 | 触发场景 |
|--------|------|----------|
| 200 | 成功 | 登录 / 列表 / 详情 / 问答 / 上传 / OCR / 删除 |
| 201 | 创建成功 | 注册、建知识库 |
| 400 | 请求参数错误 | Bean Validation、空文件 |
| 401 | 未登录 / 认证失败 | 未带 satoken、用户名或密码错误 |
| 403 | 越权 | 访问他人知识库/文档/会话 |
| 404 | 资源不存在 | 知识库/文档/会话不存在 |
| 409 | 冲突 | 用户名已存在 |
| 500 | 服务器内部错误 | 未预期异常 |
| 502 | 网关错误 | 模型 / 向量化 / OCR / 文件存储失败 |

---

## 9. 字段校验规则

```text
username     : 必填，非空白，最大 64（注册）
password     : 注册必填 6~128；登录必填非空白
知识库 name  : 必填，非空白，最大 255；description 最大 1000
文档 name    : 必填，非空白，最大 255；content 必填非空白，最大 100000
question     : 必填，非空白，最大 8000
topK         : 可选，1~50 整数，缺省用配置 rag.default-top-k（默认 4）
ocr file     : 必填，非空白；fileType 可选 pdf / image
上传 file    : 非空
```

---

## 10. 典型调用流程示例

```bash
# 1. 注册 + 登录拿 token
curl -X POST http://localhost:8081/api/v1/auth/register -H "Content-Type: application/json" -d '{"username":"u1","password":"pass123"}'
curl -X POST http://localhost:8081/api/v1/auth/login -H "Content-Type: application/json" -d '{"username":"u1","password":"pass123"}'

# 2. 建知识库
curl -X POST http://localhost:8081/api/v1/knowledge-bases -H "Content-Type: application/json" -H "satoken: TOKEN" -d '{"name":"kb1"}'

# 3. 上传文档
curl -X POST http://localhost:8081/api/v1/knowledge-bases/1/documents -H "Content-Type: application/json" -H "satoken: TOKEN" -d '{"name":"doc1","content":"..."}'

# 4. 问答
curl -X POST http://localhost:8081/api/v1/knowledge-bases/1/chats -H "Content-Type: application/json" -H "satoken: TOKEN" -d '{"question":"..."}'

# 5. Token 统计
curl http://localhost:8081/api/v1/token-stats -H "satoken: TOKEN"
```
