# Week 29 接口文档 — 前端调用映射

> 本周**不新增后端接口**：页面只组合 Week 16–28 的既有 API。
> 通用信封/错误码见 `docs/api/week-16-api.md`；后端契约见各周 API 文档。

## 1. 基地址与鉴权

| 项 | 值 |
|----|----|
| 浏览器基地址 | `/`（nginx 同域） |
| API 前缀 | `/api` → 平台 8084 |
| 鉴权头 | `Authorization: Bearer <token>` |
| token 存储 | `sessionStorage["aicode_platform_token"]` |
| 超时 | 180s（Agent 同步执行） |

## 2. 页面 ↔ 接口

| 页面 | 接口 | 备注 |
|------|------|------|
| 登录 | `POST /api/v1/platform/auth/login` | `{username,password}` → token |
| 会话恢复 | `GET /api/v1/platform/auth/me` | 401 → 回登录 |
| 退出 | `POST /api/v1/platform/auth/logout` | 失败也清本地 |
| 工作台 | `GET /executions`、`GET /approvals?status=PENDING`、`GET /approvals/reminders`、`GET /followup-tasks/reminders` | 四次并发，单次失败不阻塞其它卡片 |
| Agent 目录 | `GET /api/v1/platform/agents` | 4 个 Agent |
| Agent 运行 | `POST /agents/{agentKey}/runs` | `{input:{...}}` |
| 执行列表 | `GET /executions` | 前端按状态/Agent 过滤 |
| 执行详情 | `GET /executions/{executionId}` | `outputJson` 二次解析 |
| 审批列表 | `GET /approvals?status=&limit=100` | PENDING/APPROVED/REJECTED |
| 审批提醒 | `GET /approvals/reminders?limit=50` | `thresholdHours` + `overdueHours` |
| 审批详情 | `GET /approvals/{approvalId}` | 超时列表点击时补拉 |
| 审批决策 | `POST /approvals/{approvalId}/decision` | `{approved,comment}`；409 已决策 |
| 随访列表 | `GET /followup-tasks?status=&limit=100` | PENDING/COMPLETED/CANCELLED |
| 随访详情 | `GET /followup-tasks/{taskId}` | 提醒行点击补拉 |
| 随访完成/取消 | `POST /followup-tasks/{taskId}/complete|cancel` | 备注/原因 ≤500；409 |
| 随访提醒 | `GET /followup-tasks/reminders?days=7` | overdue + dueSoon |
| 知识库绑定 | `GET /knowledge-bindings` | `syncStatus` / `lastSyncedAt` |
| 导入/对账/删除 | `POST /import`、`POST /reconcile`、`DELETE /{id}` | 仅 admin |
| 知识库问答 | `POST /agents/knowledge-qa/runs` | `{knowledgeBaseId,question}`；citations |
| 用户列表 | `GET /users` | 仅 admin |
| 停用/恢复 | `POST /users/{id}/disable|enable` | 停用自己 400 |

## 3. Agent 入参

| agentKey | 入参 |
|----------|------|
| `patient-analysis` | `patientId`（必填）、`task`（可选） |
| `operations-analysis` | `windowDays`（1–30，默认 7）、`task`（可选） |
| `knowledge-qa` | `knowledgeBaseId`（必填，已绑定）、`question`（必填）、`task`（可选） |
| `followup` | `patientId`（必填）、`task`（可选） |

## 4. 错误展示

| HTTP / code | 前端行为 |
|-------------|----------|
| 401 / `UNAUTHORIZED` | 清 token、回 `/login?redirect=...` |
| 403 / `FORBIDDEN` | 中文提示「没有权限」，不跳转 |
| 404 / `PLATFORM_NOT_FOUND` | 显示后端 message（如未绑定知识库） |
| 409 / `PLATFORM_CONFLICT` | 提示「状态已变化」并刷新列表 |
| 429 | 提示「尝试过于频繁，请稍后再试」 |
| 502 / `TOOL_EXECUTION_ERROR` | 提示上游不可用；执行详情看 `errorMessage` |

> 前端不解析后端堆栈；只展示信封 `message` + 兜底中文。

## 5. 尚未使用（第 30 周）

- `/ent/` 前缀：企业登录、知识库 CRUD、文本入库、文档列表；
- SSE：知识库问答流式（当前同步 200）。
