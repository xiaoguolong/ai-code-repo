# Week 29 候选计划：最小可用前端（Web UI）

> **状态：已排期（2026-10-25 决定）**。第 28 周保持「TD-06 平台用户停用 + P2 类拆分」，
> 第 29 周做本计划；周初把本文件收敛为执行版 `week-29.md`（含 Commands / Boundaries / Success Criteria）。
>
> 背景：第 1–27 周全部是 API + Postman 交付，页面一直作为「前端仓库到位后」的触发项
> （见 `week-22.md` 第 7 项、`week-24.md` 不做项、`week-27.md` 第 1.2 节）。
> 第 27 周收口后，登录 / 4 个 Agent / 执行记录 / 审批 + 提醒 / 随访 / 知识库对账的后端能力已齐，
> 具备做「可用页面」的条件。

## 1. 目标（什么叫“可使用”）

第 29 周结束时，**不用 Postman**，在浏览器里完成医疗 SaaS 平台的日常闭环：

```text
登录 → 选 Agent → 运行 → 看结果/引用 → HIGH 待审 → 批准/拒绝
     → 看随访任务 → 知识库导入/对账 → 超时提醒 → 退出/刷新
```

“可使用”的硬标准：

1. admin / operator / viewer 三种角色都能登录，菜单与按钮按角色收敛；
2. 4 个 Agent（患者分析 / 运营日报 / 知识库问答 / 随访）都能从页面发起并看到结果；
3. 高风险审批能在页面批准/拒绝，重复决策显示 409 中文提示；
4. 知识库绑定列表能看 `syncStatus`、能导入/对账，问答能展示 citations；
5. 随访任务能完成/取消，审批与随访的站内提醒有入口/角标；
6. 刷新不丢登录、退出清会话、401 自动回登录页、429/403/404 有中文提示；
7. 部署在云端**同域**（nginx 反代），无 CORS；静态产物可回滚。

## 2. 范围（P0 页面）

| # | 路由 | 页面 | 角色 | 关键能力 |
|---|------|------|------|----------|
| 1 | `/login` | 登录 | 全部 | 平台账号登录；错误/限流提示；登录后回跳 |
| 2 | `/` | 工作台 | 全部 | Agent 卡片、最近执行、待审数、超时提醒角标 |
| 3 | `/agents/:agentKey` | Agent 运行 | operator/admin | 动态表单（patientId/task/question/knowledgeBaseId）、运行、结果、跳执行详情 |
| 4 | `/executions` | 执行记录 | 全部（按权限） | 状态/Agent/时间筛选、分页、token/traceId |
| 5 | `/executions/:id` | 执行详情 | 同上 | 状态、耗时、token、输出（报告/日报/随访计划/引用表）、审批状态 |
| 6 | `/approvals` | 审批 | 全部（按权限） | 待办/历史/超时三视图、详情、批准/拒绝、409 |
| 7 | `/followups` | 随访 | 全部（按权限） | 任务列表/状态筛选、完成/取消、逾期/将到期提醒 |
| 8 | `/knowledge` | 知识库绑定与问答 | 全部（写操作 admin） | 绑定列表 + `syncStatus`、导入、手动对账、删除、问答 + citations |
| 9 | `/ent/knowledge` | 企业知识库管理（P0.5） | admin | 建库/列表、纯文本入库（name+content）、文档列表/删除 |

**P0.5 说明**：第 9 页走 enterprise 8081（经 nginx `/ent` 前缀），解决「知识库从哪来」；
不做 PDF/OCR 上传（当前 enterprise 入库接口是 JSON `name + content`，PDF 走 OCR 是后续项）。

**P1（有余量再做）**：运营日报独立页、审计日志只读、租户/角色只读、监控概览嵌入。

## 3. 明确不做

- **SSE 流式**：长答案先同步 200 + loading/超时提示；流式放第 30 周（前端到位后再做）；
- 科室级知识库授权（P1 #2）、用户停用 TD-06 的页面（第 28 周只做后端语义）；
- 企业 PDF/OCR 上传、文档预览、知识库编辑；
- 设计系统/暗色模式/i18n/移动端深度适配（只保证 390px 宽度可用）；
- 前端 CI/CD、E2E 自动化（第 29 周手工验收，CI 触发条件另排）；
- 租户自助开通、计费、短信/邮件设置。

## 4. 技术选型与部署形态

| 项 | 选择 | 理由 |
|----|------|------|
| 框架 | **Vue 3 + Vite + TypeScript** | 生态成熟、构建快；与后端无耦合 |
| UI | **Element Plus** | 表格/表单/对话框齐全，5 天能出可用页面 |
| 状态 | Pinia（auth + 全局提醒角标） | 轻量，够用 |
| HTTP | axios 封装：`Authorization: Bearer`、统一拆信封、错误码映射 | 对齐 `docs/api/week-16-api.md` |
| 路由 | Vue Router + 全局守卫（无 token → `/login`） | 401 统一处理 |
| 仓库位置 | **建议 `apps/web`（本仓库内）** | 与后端同仓、部署脚本同源；待确认 |
| 构建 | `npm run build` → `dist/` → 上传 `/opt/ai-code/web/` | 静态站点，可整目录回滚 |
| 反代 | 复用 `deploy/app/nginx/aicode.conf.example`：`/` → 静态、`/api` → 8084、`/ent` → 8081 | 同域、无 CORS；打开 `trust-forwarded-for` |
| 鉴权 | 平台 token 与 enterprise token 分开存（sessionStorage） | 两套 Sa-Token 会话，避免串号 |

> 不引入 Node 服务端：纯静态 SPA，nginx 托管即可；本地开发用 Vite proxy 指向 8084/8081。

## 5. 页面 ↔ API 映射

### 5.1 平台（`/api` → 8084）

| 页面 | 方法 + 路径 | 说明 |
|------|-------------|------|
| 登录 | `POST /api/v1/platform/auth/login` | `{username,password}` → `data.token` |
| 会话 | `GET /api/v1/platform/auth/me`、`POST /api/v1/platform/auth/logout` | 刷新恢复/退出 |
| 工作台 | `GET /api/v1/platform/agents`、`GET /api/v1/platform/executions?limit=`、`GET /api/v1/platform/approvals/reminders` | 卡片 + 最近执行 + 角标 |
| Agent 运行 | `POST /api/v1/platform/agents/{agentKey}/runs` | body `{input:{...}}`；同步 200 |
| 执行列表 | `GET /api/v1/platform/executions?status=&limit=` | 状态/Agent 筛选 |
| 执行详情 | `GET /api/v1/platform/executions/{executionId}` | `outputJson` 二次解析（报告/日报/随访计划/citations） |
| 审批 | `GET /api/v1/platform/approvals?status=`、`GET /{id}`、`POST /{id}/decision` | 409 = 已被他人决策 |
| 超时提醒 | `GET /api/v1/platform/approvals/reminders?limit=` | `overdueHours` 角标/列表 |
| 随访 | `GET /api/v1/platform/followup-tasks?status=&patientId=`、`GET /{id}`、`POST /{id}/complete`、`POST /{id}/cancel` | 重复操作 409 |
| 随访提醒 | `GET /api/v1/platform/followup-tasks/reminders?days=` | 逾期 + 未来 N 天 |
| 知识库 | `GET /api/v1/platform/knowledge-bindings`、`POST /import`、`POST /reconcile`、`DELETE /{id}` | 仅 admin 可导入/对账/删除 |
| 知识库问答 | `POST /api/v1/platform/agents/knowledge-qa/runs` | 结果里展示 `citations` |

### 5.2 enterprise（`/ent` → 8081，路径前缀由 nginx 去掉 `/ent`）

| 页面 | 方法 + 路径 | 说明 |
|------|-------------|------|
| 企业登录 | `POST /api/v1/auth/login` | SSO 开启时用平台账号；token 单独存 |
| 知识库 | `GET/POST /api/v1/knowledge-bases`、`GET/PUT/DELETE /{id}` | 建库/列表/删除 |
| 文档 | `GET /api/v1/knowledge-bases/{kbId}/documents`、`POST`（`{name,content}`）、`DELETE /{docId}` | 纯文本入库；解析/切片/向量由后端做 |

> 统一响应信封：`code/message/data/traceId`；前端只认 `code === "SUCCESS"`，其它按 `message` 展示。

## 6. 五天排期（2026-11-02 ~ 11-06，按实际周次调整）

| 天 | 交付 | 验收 |
|----|------|------|
| D1 | 脚手架（Vite/TS/Router/Pinia/Element Plus）、登录、布局/菜单/角色、axios 封装、Vite proxy | 登录/刷新/退出；401 回登录；菜单按角色收敛 |
| D2 | 工作台、Agent 目录/动态表单、运行与 loading/超时、执行列表/详情（outputJson 渲染） | 4 个 Agent 都能跑；HIGH 显示待审；详情可回看 |
| D3 | 审批（待办/历史/超时、批准/拒绝、409）、随访（列表/完成/取消/提醒）、角标 | 主流程闭环；重复决策/完成有中文提示 |
| D4 | 知识库绑定（导入/对账/syncStatus/删除）、问答 citations、企业知识库管理页 | 建库→入库→绑定→问答带引用；对账后状态刷新 |
| D5 | 云端部署（`dist` → `/opt/ai-code/web`、nginx 同域、`trust-forwarded-for`）、三角色回归、修复 | 同域无 CORS；三角色主流程通过；可回滚 |

> 前端工作量集中在 D1/D2；若 D4 的企业页来不及时，先交付平台 8 页（范围 B），
> 企业建库/入库继续用脚本，第 30 周补。

## 7. 验收标准（第 29 周门禁）

- [ ] `npm run build` 无 TS 错误；产物在 Chrome/Edge 最新版可运行；
- [ ] admin：导入/对账知识库、批准/拒绝审批、看全部执行；
- [ ] operator：跑 patient-analysis（P002 → HIGH → 待审）、followup、knowledge-qa（citations≥1）；
- [ ] viewer：只读；点运行按钮 403 有中文提示；
- [ ] 409（重复决策/重复完成）、429（登录限流）、404（未绑定知识库）各有可读提示；
- [ ] 审批超时提醒在 `/approvals` 有角标/列表，`thresholdHours=24` 展示正确；
- [ ] 刷新页面会话保持；退出后回登录页且 API 401；
- [ ] 云端同域部署：`http(s)://<host>/` 打开，`/api` 与 `/ent` 反代正常，无 CORS 报错；
- [ ] 静态目录可回滚（保留上一版 `web.prev`）；
- [ ] 不把 token/密码写进日志或 localStorage 明文（MVP 用 sessionStorage，XSS 风险在文档登记）。

## 8. 风险

| 风险 | 影响 | 缓解 |
|------|------|------|
| Agent 同步耗时 30–60s | 页面像卡死/网关超时 | axios 180s 超时 + loading + 禁止重复提交；SSE 第 30 周 |
| 报告/答案含 HTML | XSS | 一律按文本/Markdown 安全渲染，禁 `v-html` 原样输出 |
| 两套 token 串号 | 平台/企业互相覆盖 | 分 key 存（`platform_token` / `enterprise_token`），axios 按 base 选 |
| 企业 SSO 登录路径 | 企业页登录失败 | 先做平台页；企业页用 `/auth/login` 联调，失败则 D4 降级为范围 B |
| 同域部署未生效 | CORS/401 | 必须走 nginx；本地用 Vite proxy，禁止直连跨域调试上线 |
| 5 天做不完 | 页面半成品 | 先保平台 8 页；企业页/运营页作为 P1 顺延 |
| 前端无 CI | 发布靠手工 | 第 29 周手工 `build + scp`；CI 触发条件（多环境/多人）另排 |

## 9. 待确认（周初定）

1. **仓库位置**：`apps/web`（建议）还是独立前端仓库？
2. **UI 库**：Element Plus（建议）还是 Ant Design Vue？
3. **企业知识库页是否进 P0**：进（端到端可用，6 天风险）还是降级为脚本（5 天稳）。
4. **登录页是否合并企业登录**：一个入口双 token，还是平台/企业两个入口？
5. **SSE 确认顺延第 30 周**：第 29 周只做同步 200。

## 10. 与前后周的关系

| 周 | 主题 | 说明 |
|----|------|------|
| 28 | 用户生命周期与结构 | TD-06 停用 + P2 #1 类拆分（不变） |
| **29** | **最小可用前端** | 本计划；平台 8 页 +（可选）企业知识库页；同域 nginx |
| 30+ | 按触发条件 | SSE 流式、科室级知识库授权、日报导出、HIS/LIS、前端 CI |

> 第 29 周结束后，平台的“演示/试用”不再依赖 Postman；后续 SSE 与科室授权都在这个 UI 上增量。
