# Week 30 接口文档 — 企业知识库页

> 本周**不新增后端接口**。企业侧沿用既有 `/api/v1/auth/*`、`/api/v1/knowledge-bases/*`；
> 平台侧无改动。通用信封见 enterprise `ApiResponse`（`code/message/data`）。

## 1. 基地址与鉴权（与平台不同）

| 项 | 值 |
|----|----|
| 浏览器路径 | `/ent/api/`（仅 API；`/ent/knowledge` 是 SPA 路由，nginx 不反代） |
| 鉴权头 | **`satoken: <token>`**（enterprise `sa-token.token-name`） |
| token 存储 | `sessionStorage["aicode_enterprise_token"]` |
| 登录 | `POST /ent/api/v1/auth/login`（SSO，账号同平台） |

> **不要**用平台 `Authorization: Bearer` 调企业接口，会 401。

## 2. 接口

### 2.1 企业登录

`POST /ent/api/v1/auth/login`

```json
{"username":"admin","password":"admin123"}
```

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {"token": "…", "user": {"id": 4, "username": "admin"}}
}
```

| 情况 | 状态码 |
|------|--------|
| 成功 | 200 |
| 凭据错 | 401 |
| SSO 身份源不可用 | 503 |
| 同名本地账号冲突 | 409 |

### 2.2 登出 / 当前用户

- `POST /ent/api/v1/auth/logout`（`satoken`）
- `GET /ent/api/v1/auth/me`（`satoken`）→ `{id,username}`

### 2.3 知识库

| 动作 | 方法 + 路径 | 请求/响应 |
|------|-------------|-----------|
| 列表 | `GET /ent/api/v1/knowledge-bases` | `data: [{id,name,description,createdAt}]` |
| 创建 | `POST /ent/api/v1/knowledge-bases` | `{name(≤255),description(≤1000)}` → 201，`{id,name,description,createdAt}` |
| 详情 | `GET /ent/api/v1/knowledge-bases/{id}` | 同上 |
| 更新 | `PUT /ent/api/v1/knowledge-bases/{id}` | `{name,description}` |
| 删除 | `DELETE /ent/api/v1/knowledge-bases/{id}` | `data=null`；被引用/有文档可能 409 |

### 2.4 文档

| 动作 | 方法 + 路径 | 请求/响应 |
|------|-------------|-----------|
| 列表 | `GET /ent/api/v1/knowledge-bases/{id}/documents` | `data: [{documentId,name,chunkCount,createdAt}]` |
| 文本入库 | `POST /ent/api/v1/knowledge-bases/{id}/documents` | `{name(≤255),content(≤100000)}` → `{documentId,name,chunkCount}` |
| 删除 | `DELETE /ent/api/v1/knowledge-bases/{id}/documents/{docId}` | `data=null` |

> 入库是**纯文本**；PDF/OCR 走 enterprise 的另一条链路（`/api/v1/files` + `/api/v1/ocr`），
> 本周页面不做。

## 3. 错误语义

| 情况 | 状态码 | 前端行为 |
|------|--------|----------|
| 未登录/`satoken` 失效 | 401 | 清企业 token，显示企业登录卡 |
| 非本人知识库 | 404（不泄露存在性） | 显示 message，刷新列表 |
| name/content 空白或超长 | 400 | 表单提示 + 后端 message |
| 删除有文档的库 | 409（若后端拒绝） | 提示后刷新 |
| enterprise 不可用 | 502/503 | 提示企业服务不可用 |

## 4. 与平台知识库的关系

```text
企业页：建库 → 入库（enterprise 8081）
平台页：导入绑定 → 手动对账 → 知识库问答（平台 8084）
```

- 企业页删除库后，平台绑定在下一次对账会变 `MISSING`（Week 27）；
- 平台不代理企业的建库/上传，权限与数据归属仍在 enterprise。
