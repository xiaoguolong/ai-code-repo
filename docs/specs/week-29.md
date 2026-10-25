# Spec: Week 29 — 最小可用前端（平台 8 页 + 用户管理）

> 来源：`docs/specs/week-29-candidates.md`（已定稿：范围 B / `apps/web` / Ant Design Vue）。
> 本周把 1–28 周的 API 变成**不用 Postman**的最小可用 Web UI；不做企业知识库页与 SSE（第 30 周）。

## Objective

第 29 周末，admin / operator / viewer 在浏览器里完成：

```text
登录 → 工作台 → 选 Agent 运行 → 看执行详情/引用 → 高风险审批 → 随访
     → 知识库绑定/对账/问答 → 用户停用/恢复（admin）→ 刷新/退出
```

## Tech Stack

- Vue 3.5 + Vite + TypeScript + Pinia + Vue Router
- Ant Design Vue 4.x（`unplugin-vue-components` + `AntDesignVueResolver` 按需）
- axios（统一信封/401/错误码）、dayjs
- 测试：Vitest + @vue/test-utils + jsdom（纯函数/组件冒烟）
- 部署：`dist/` → `/opt/ai-code/web`，nginx 同域反代 `/api` → 8084

## Commands

```powershell
# 依赖（仓库外 node/pnpm，见 load_workspace_dependencies）
node <...>/pnpm.mjs --dir apps/web install
node <...>/pnpm.mjs --dir apps/web run typecheck
node <...>/pnpm.mjs --dir apps/web run test
node <...>/pnpm.mjs --dir apps/web run build
```

## Boundaries

- **Always**：响应只认 `code === "SUCCESS"`；401 清会话回登录；token 存 sessionStorage；
  模型输出按文本/Markdown 安全渲染（禁 `v-html` 原样）；所有时间 UTC → 本地展示。
- **Ask first**：改后端契约、加新依赖、动 nginx/安全组。
- **Never**：把 token/密码写日志；跳过类型检查；直接跨域调 8084（必须同域 nginx）。

## 一、页面与路由

| # | 路由 | 页面 | 角色 | 关键 API |
|---|------|------|------|----------|
| 1 | `/login` | 登录 | 公开 | `POST /auth/login` |
| 2 | `/` | 工作台 | 全部 | executions / approvals/reminders / followup reminders |
| 3 | `/agents/:agentKey` | Agent 运行 | operator/admin | `GET /agents`、`POST /agents/{key}/runs` |
| 4 | `/executions` | 执行记录 | 全部（按权限） | `GET /executions` |
| 5 | `/executions/:id` | 执行详情 | 同上 | `GET /executions/{id}` |
| 6 | `/approvals` | 审批 | 全部（按权限） | list/get/decision/reminders |
| 7 | `/followups` | 随访 | 全部（按权限） | list/complete/cancel/reminders |
| 8 | `/knowledge` | 知识库 | 全部；写操作 admin | bindings / import / reconcile / QA |
| 9 | `/users` | 用户管理 | admin | `GET /users`、disable/enable |

## 二、交互要点

- **登录**：错误/限流提示；成功后 `GET /auth/me` 恢复用户；退出调 `/auth/logout` 并清 sessionStorage。
- **工作台**：待审数、超时提醒、逾期随访、最近执行；点击跳对应页。
- **Agent 运行**：按 agentKey 渲染动态表单：
  - `patient-analysis`：patientId（P001/P002）、task
  - `operations-analysis`：windowDays（7/14/30）、task
  - `knowledge-qa`：knowledgeBaseId（来自绑定）、question、task
  - `followup`：patientId、task
  - 同步 200；180s 超时；运行中禁重复提交；结果展示状态/执行 ID/摘要/跳详情。
- **执行详情**：`outputJson` 二次解析；报告/答案/引用表/随访任务/异常规则分别渲染；关联审批。
- **审批**：待办/历史/超时三视图；批准/拒绝必须填意见（可空但提示）；409 提示「已被他人决策」并刷新。
- **随访**：状态筛选；完成/取消；409 提示并刷新；提醒视图展示逾期/将到期。
- **知识库**：绑定表展示 `syncStatus`（UNKNOWN/ACTIVE/MISSING）+ 最后对账；admin 导入/对账/删除；问答展示 citations。
- **用户管理**：停用/恢复；停用自己后端 400；停用后行状态即时刷新。

## 三、前端结构

```text
apps/web/
├── index.html / vite.config.ts / tsconfig.json / package.json
└── src/
    ├── main.ts / App.vue / styles.css
    ├── router/index.ts
    ├── stores/auth.ts
    ├── api/http.ts / api/types.ts / api/platform.ts
    ├── utils/{format.ts,permissions.ts}
    ├── layouts/AppLayout.vue
    ├── components/{StatusTag.vue,PageHeader.vue,ErrorAlert.vue}
    └── views/{Login,Dashboard,AgentRun,Executions,ExecutionDetail,Approvals,Followups,Knowledge,Users}.vue
```

## 四、Success Criteria

- [x] `pnpm typecheck` 0 错误；`pnpm test` 11 例全绿；`pnpm build` 产出 `dist/`；
- [x] admin/operator/viewer 三角色菜单与按钮正确收敛；
- [x] operator 跑 patient-analysis（P002 HIGH）、followup、knowledge-qa（citations≥1）；
- [x] admin 审批拒绝成功；用户列表/停用/恢复页可用（停用后端语义由 Week 28 单测覆盖）；
- [x] viewer 只读：无用户管理菜单；直访 Agent 路由被守卫拦回工作台；
- [x] 错误码统一中文映射（401/403/404/409/429/502），409 提示后刷新；
- [x] 刷新保持登录（`/auth/me`）；退出清 sessionStorage；
- [x] 云端同域：`/` 200、`/api` 反代登录 200、无 CORS；公网 `http://HOST/` 200；
- [x] 静态可回滚（`web.prev` + 备份目录）；
- [x] 文档：Spec/架构/接口映射/部署/实现日志/README/日志索引；后端 990 用例不受影响。

### 4.1 交付结果（2026-10-25）

| 门禁 | 结果 |
|------|------|
| 前端类型检查 | `vue-tsc --noEmit` 0 错误 |
| 前端单测 | **11** 例全绿（3 文件） |
| 生产构建 | 成功（`dist/`，入口 220KB/gzip 80KB） |
| 本地 UI 冒烟 | **PASS=7 FAIL=0**（Edge headless，无 console error） |
| 云端部署版冒烟 | **PASS=7 FAIL=0**（经 nginx `/api`） |
| operator 专项 | knowledge-qa `COMPLETED`（1567 tokens）、followup 完成、审批拒绝成功 |
| 云端入口 | nginx 1.18；`http://HOST/` 200；`/api` 登录 200 |
| 后端回归 | 本周未改 Java；Week 28 根聚合 **990** 全绿 |

## 五、风险

| 风险 | 缓解 |
|------|------|
| 同步 Agent 30–60s | axios 180s + loading + 禁重复提交 |
| 模型输出 XSS | 文本渲染/安全 Markdown，禁原样 `v-html` |
| nginx 未装/80 未放行 | 优先本机 `127.0.0.1/` 验收；安全组另配 |
| 三角色测试数据 | 用种子 admin/operator/viewer；P002 为 HIGH 演示患者 |
| 5 天排期 | 先平台 9 页；企业页/SSE 第 30 周 |

## 六、ADR

| 决策 | 选择 | 理由 |
|------|------|------|
| 前端框架 | Vue3 + Vite + Ant Design Vue | 已定；表单/表格成熟 |
| 状态管理 | Pinia | 轻量，auth/badge 足够 |
| token 存储 | sessionStorage | 关闭标签即失效，降低 XSS 窗口 |
| 跨域 | nginx 同域 `/api` | 不放开 CORS；限流按真实 IP |
| SSE | 不做 | 第 30 周；同步 200 已可用 |
| 企业页 | 不做 | 范围 B；第 30 周 |
