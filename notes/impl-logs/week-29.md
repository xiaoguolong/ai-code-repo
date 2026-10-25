# Week 29 实现日志 — 最小可用前端（apps/web）

> 来源：`docs/specs/week-29.md`（由 `week-29-candidates.md` 收敛）；架构：
> `docs/architecture/week-29-architecture.md`；接口映射：`docs/api/week-29-api.md`；
> 部署：`docs/deploy/week-29-web.md`。

## 1. 本周目标

把 1–28 周的 API 变成**不用 Postman** 的 Web UI：登录、工作台、4 个 Agent、执行记录/详情、
审批、随访、知识库绑定/问答、用户停用/恢复；同域部署到云端 nginx。

## 2. 边界

**做了**：

- `apps/web`：Vue3 + Vite + TS + Pinia + Vue Router + Ant Design Vue 4.x；
- 9 个页面 + 布局/角色菜单/角标 + 统一 HTTP/错误/401；
- 11 个 Vitest 用例；typecheck/build；
- Edge headless 冒烟 7/7（本地 dev + 云端部署版）；
- 云端 nginx 1.18 安装、静态站点 `/opt/ai-code/web`、`/api` 反代、`trust-forwarded-for=true`；
- 公网 `http://HOST/` 实测 200。

**没做**：

- 企业知识库管理页、`/ent` 页面（第 30 周；反代已预留）；
- SSE 流式（第 30 周；当前同步 200）；
- TLS/域名、前端 CI、Playwright 多浏览器矩阵；
- 科室级授权、审计/监控页（后续）。

## 3. 架构

见 `docs/architecture/week-29-architecture.md`。一句话：

> 浏览器只访问 nginx；SPA 静态 + `/api` 同域反代；前端 `views → api → http` 分层，
> auth store + 路由守卫管理会话，角色只做展示收敛（权限仍看后端 403）。

## 4. 新增清单

| 类别 | 文件 |
|------|------|
| 工程 | `package.json` / `pnpm-lock.yaml` / `vite.config.ts` / `tsconfig.json` / `index.html` |
| 入口 | `src/main.ts` / `App.vue` / `styles.css` / `env.d.ts` |
| API | `src/api/http.ts`（信封/错误/401）、`platform.ts`、`types.ts` |
| 会话 | `src/stores/auth.ts`、`src/router/index.ts` |
| 布局/组件 | `AppLayout.vue`、`StatusTag.vue`、`PageHeader.vue` |
| 页面 | Login / Dashboard / AgentRun / Executions / ExecutionDetail / Approvals / Followups / Knowledge / Users |
| 工具 | `utils/format.ts`、`utils/permissions.ts` |
| 测试 | `http.test.ts`（5）、`permissions.test.ts`（4）、`format.test.ts`（2） |
| 冒烟 | `scripts/smoke.mjs`（Playwright-core + Edge，不下载浏览器） |
| 部署 | `deploy/app/nginx/aicode.conf`、`deploy/app/cloud-week29-web.sh` |

## 5. RED

- 前端没有既有测试基建：先建 `vue-tsc + Vitest` 门禁，再写页面；
- 首次 typecheck 暴露：
  1. axios 响应拦截器返回 `unknown` 与 `AxiosResponse` 类型不兼容；
  2. AntD `a-table` 的 slot `record` 是 `Record<string, any>`，直接传给强类型 Row 报 TS2345；
  3. TypeScript 7.0.2 与 `vue-tsc 3.3.13` 不兼容（找不到 `typescript/lib/tsc`）；
- UI 冒烟首轮：favicon 404 一条 console error；工作台/审批截图早于请求返回显示 0（非数据问题）。

## 6. GREEN

- **登录**：token → sessionStorage；`/auth/me` 恢复；401 统一清会话回登录；
- **工作台**：执行/待审/超时审批/逾期随访四卡片 + 最近执行；
- **Agent 运行**：4 个 Agent 动态表单；knowledge-qa 从绑定中选库；180s 超时/防重复；
- **执行详情**：`outputJson` 安全解析，报告/答案/引用/随访任务/异常分块；
- **审批**：待办/已批准/已拒绝/超时四视图；批准/拒绝；409 刷新；详情可跳执行；
- **随访**：状态视图 + 站内提醒；完成/取消；409 刷新；
- **知识库**：`syncStatus` 标签、导入/对账/删除（admin）、问答 + citations；
- **用户管理**：停用/恢复，停用自己按钮置灰（后端仍 400 兜底）；
- **角色**：viewer 无用户管理菜单、直接访问 Agent 路由被守卫拦回工作台。

## 7. 修正记录

| 问题 | 修正 |
|------|------|
| TS 7.0.2 与 vue-tsc 不兼容 | `typescript@5.9.3`（vue-tsc 3.3.13 兼容） |
| axios 拦截器类型 | 返回 `as unknown as typeof response` |
| AntD table slot 类型 | 事件处理参数放宽为 `Row \| Record<string, unknown>`，函数内断言 |
| favicon 404 | 新增 `public/favicon.svg` + `<link rel="icon">` |
| 截图早于数据返回 | 冒烟在关键页等待 1.2–1.5s（后续可改等表格行） |
| `pnpm install` exit=1 | 仅 `core-js` postinstall 被忽略（`ERR_PNPM_IGNORED_BUILDS`），依赖完整 |

## 8. 质量门禁

| 门禁 | 结果 |
|------|------|
| `pnpm typecheck` | 0 错误 |
| `pnpm test` | **11 用例全绿**（3 文件） |
| `pnpm build` | 成功；`dist/index.html` + assets |
| 本地 UI 冒烟 | **PASS=7 FAIL=0**，无 console error |
| 云端部署版冒烟 | **PASS=7 FAIL=0**（经 nginx `/api`） |
| 公网入口 | `http://HOST/` 200 |
| 后端回归 | 本周未改 Java；沿用 Week 28 根聚合 **990** 全绿 |

## 9. 验证证据

| 证据 | 位置 |
|------|------|
| 前端装依赖/TS 修复 | `logs/week-29/frontend-install.log`、`frontend-ts5.log` |
| 单测/构建 | 控制台 + `logs/week-29/`（构建产物 `web.tar.gz`） |
| 本地 UI 冒烟 | `logs/week-29/ui-smoke2.log` |
| 云端部署版冒烟 | `logs/week-29/ui-smoke-deployed.log` |
| 截图 | `logs/week-29/smoke-01..06*.png`、`logs/week-29/deployed/` |
| nginx 检查/安装 | `logs/week-29/target-week29-nginx-check.log`、`target-week29-install-nginx.log` |
| 部署 | `logs/week-29/target-week29-deploy-web.log` |

> 真实 Agent 运行（首轮冒烟）返回 `PENDING_APPROVAL`，但当时云端模型不可用，
> 报告为规则模板降级（Token=0）。前端正确显示「待审 + 报告 + 降级提示」；
> 这是后端模型瞬时不可用，不是前端缺陷，后续可复跑确认。

## 10. 风险与下周

| # | 风险 / 待办 | 下一步 |
|---|-------------|--------|
| 1 | 仅 HTTP，无 TLS/域名 | 正式对外前加证书 + 301 |
| 2 | 企业知识库页未做（范围 B） | 第 30 周：企业登录、建库、文本入库、文档列表 |
| 3 | 无 SSE，长任务 loading | 第 30 周评估模型流式能力；不行则维持同步 |
| 4 | 前端无 CI | 手工 build+scp；多环境/多人时加 CI |
| 5 | 真实 Agent 模型偶发降级 | 复跑确认；后端 LLM 可用性监控已有 |
| 6 | token 在 sessionStorage | XSS 风险登记；生产可换 HttpOnly Cookie |
| 7 | 冒烟依赖本机 Edge | CI 时改用 Playwright 自带 chromium |

## 11. ADR

| 决策 | 选择 | 理由 |
|------|------|------|
| 框架 | Vue3 + Vite + TS + Ant Design Vue | 已定；表单/表格成熟 |
| 状态 | Pinia | auth/badge 足够，避免过度设计 |
| token | sessionStorage | 关闭标签即失效；比 localStorage 窗口小 |
| 跨域 | nginx 同域 `/api` | 不放 CORS；限流按真实 IP |
| 企业页 | 第 30 周 | 范围 B；先把平台主流程做可用 |
| SSE | 第 30 周 | 需要后端流式能力，先同步 200 |

## 12. 第 30 周输入

- 企业知识库页：`/ent` 反代已就绪；页面复用同一 SPA（企业 token 分开存）或独立入口；
- SSE：先确认 `ChatModelPort` 是否支持流式，再决定「真流式」或「状态+最终答案」；
- 前端 CI/Playwright 矩阵（Edge/Chrome）与 TLS。
