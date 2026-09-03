# Week 03 接口文档 — RAG 知识库 + 文档 OCR

> Base URL：`http://localhost:8080`  
> 响应信封：`{ "code": "...", "message": "...", "data": ... }`  
> 成功时 `code=SUCCESS`，`data` 为业务数据；失败时 `data=null`

---

## 1. 上传文档（解析 → 切片 → 向量化 → 入库）

### POST /api/v1/documents

#### 请求参数

| 字段 | 类型 | 必填 | 限制 | 说明 |
|------|------|------|------|------|
| name | string | **是** | 非空白，最大 200 字符 | 文档名称 |
| content | string | **是** | 非空白，最大 100000 字符 | 文档正文（纯文本） |

#### 请求示例

```bash
curl -X POST http://localhost:8080/api/v1/documents \
  -H "Content-Type: application/json" \
  -d '{
    "name": "员工手册",
    "content": "请假流程：员工提交申请后由主管审批。\n报销流程：提交发票后由财务审核。"
  }'
```

#### 成功响应 200

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "documentId": "550e8400-e29b-41d4-a716-446655440000",
    "name": "员工手册",
    "chunkCount": 4
  }
}
```

#### 失败响应 400

```json
{
  "code": "VALIDATION_ERROR",
  "message": "name 不能为空",
  "data": null
}
```

---

## 2. 查询文档列表

### GET /api/v1/documents

#### 请求示例

```bash
curl http://localhost:8080/api/v1/documents
```

#### 成功响应 200

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "documents": [
      {
        "documentId": "550e8400-e29b-41d4-a716-446655440000",
        "name": "员工手册",
        "createdAt": "2026-09-03T00:00:00Z"
      }
    ]
  }
}
```

---

## 3. RAG 问答（检索增强）

### POST /api/v1/rag/chats

#### 请求参数

| 字段 | 类型 | 必填 | 限制 | 说明 |
|------|------|------|------|------|
| question | string | **是** | 非空白，最大 8000 字符 | 用户问题 |
| topK | int | 否 | 1 ~ 50 | 检索条数，缺省用配置默认 4 |

#### 请求示例

```bash
curl -X POST http://localhost:8080/api/v1/rag/chats \
  -H "Content-Type: application/json" \
  -d '{
    "question": "公司请假流程是什么？",
    "topK": 4
  }'
```

#### 成功响应 200

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "answer": "员工提交申请后由主管审批。",
    "sources": [
      {
        "chunkIndex": 0,
        "content": "请假流程：员工提交申请后由主管审批。",
        "score": 0.95
      }
    ]
  }
}
```

> 知识库为空时 `sources` 返回 `[]`，模型按 rag 模板提示回答「资料中没有相关信息」。

#### 失败响应

**400 - 参数校验失败**

```json
{
  "code": "VALIDATION_ERROR",
  "message": "question 不能为空",
  "data": null
}
```

**502 - 向量化服务失败**

```json
{
  "code": "EMBEDDING_ERROR",
  "message": "向量化服务不可用",
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

## 4. OCR 识别（图片 / PDF → 文本）

### POST /api/v1/ocr/upload（上传本地文件，multipart）

#### 请求参数

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | file | **是** | 本地图片或 PDF 文件，类型按扩展名推断（.pdf 为 PDF，其余为图像） |

#### 请求示例

```bash
curl -X POST http://localhost:8080/api/v1/ocr/upload \
  -F "file=@./卓睦鸟人工智能MDT产品发布操作规范.pdf"
```

#### 成功响应 200

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "text": "识别出的正文文本（markdown）..."
  }
}
```

### POST /api/v1/ocr（URL 或 Base64，JSON）

#### 请求参数

| 字段 | 类型 | 必填 | 限制 | 说明 |
|------|------|------|------|------|
| file | string | **是** | 非空白 | 图片/PDF 的 URL 或 Base64 |
| fileType | string | 否 | pdf / image | 文件类型；URL 可不传，Base64 必传 |

#### 请求示例

```bash
curl -X POST http://localhost:8080/api/v1/ocr \
  -H "Content-Type: application/json" \
  -d '{"file":"https://example.com/doc.pdf","fileType":"pdf"}'
```

#### 失败响应

**400 - 空文件 / 非法 fileType**

```json
{
  "code": "VALIDATION_ERROR",
  "message": "file must not be empty",
  "data": null
}
```

**502 - OCR 服务失败**

```json
{
  "code": "OCR_ERROR",
  "message": "OCR 识别服务不可用",
  "data": null
}
```

---

## 5. HTTP 状态码汇总

| 状态码 | 含义 | 触发场景 |
|--------|------|----------|
| 200 | 成功 | OCR / 上传 / 列表 / RAG 问答 |
| 400 | 请求参数错误 | Bean Validation 失败、用例层空白校验、非法 fileType |
| 500 | 服务器内部错误 | 未预期异常 |
| 502 | 模型 / 向量化 / OCR 网关错误 | Embedding、LLM、OCR 调用失败 |

---

## 6. 字段校验规则

```text
name      : 必填，去空白后非空，最大 200 字符
content   : 必填，去空白后非空，最大 100000 字符
question  : 必填，去空白后非空，最大 8000 字符
topK      : 可选，1~50 整数，缺省用配置 rag.default-top-k（默认 4）
file      : 必填，非空白（URL/Base64）；上传文件非空
fileType  : 可选，pdf / image
```

---

## 7. 典型调用流程示例

```bash
# 0. 上传本地 PDF 做 OCR
curl -X POST http://localhost:8080/api/v1/ocr/upload \
  -F "file=@./卓睦鸟人工智能MDT产品发布操作规范.pdf"

# 1. 上传知识文档
curl -X POST http://localhost:8080/api/v1/documents \
  -H "Content-Type: application/json" \
  -d '{"name":"员工手册","content":"请假流程：员工提交申请后由主管审批。"}'

# 2. 查看已入库文档
curl http://localhost:8080/api/v1/documents

# 3. 基于知识库提问
curl -X POST http://localhost:8080/api/v1/rag/chats \
  -H "Content-Type: application/json" \
  -d '{"question":"公司请假流程是什么？","topK":4}'
```
