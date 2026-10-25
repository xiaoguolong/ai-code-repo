# apps/web — 医疗 SaaS AI Agent 平台前端（Week 29）

> Vue 3 + Vite + TypeScript + Pinia + Vue Router + Ant Design Vue 4.x。
> 同域部署：nginx `/` → 静态、`/api` → 平台 8084、`/ent/api` → enterprise 8081
> （`/ent/knowledge` 是 SPA 路由，不走代理）。

## 开发

```powershell
# Node/pnpm 用 DSH 自带运行时（勿改全局 PATH）
node <dsh>/dependencies/node/bin/node.exe <dsh>/dependencies/pnpm/bin/pnpm.mjs install
node <dsh>/dependencies/node/bin/node.exe <dsh>/dependencies/pnpm/bin/pnpm.mjs run dev
```

- 本地 5173；Vite 代理：`/api` → `127.0.0.1:8084`、`/ent/api` → `127.0.0.1:8081`。
  企业页 SPA 路由 `/ent/knowledge` 不走代理（与 nginx 一致）。
- 连远端平台时：设置 `VITE_PLATFORM_TARGET=http://127.0.0.1:18084`（SSH 隧道），再 `run dev`。
- 演示账号：`admin/admin123`、`operator/operator123`、`viewer/viewer123`。

## 企业知识库页（Week 30）

- 入口 `/ent/knowledge`（仅 admin）；企业账号与平台同源（SSO）。
- 企业 token 存 `aicode_enterprise_token`，请求头是 **`satoken`**（不是 Bearer）；
  企业 401 只清企业会话。
- 能力：建库、纯文本入库（name/content）、文档列表/删除、删库。
- 与平台关系：企业建库/入库 → 平台「知识库」导入绑定/对账 → 平台问答。

## 质量门禁

```powershell
# 本地一键（Node/pnpm 用 DSH 运行时）
..\..\tools\web-ci.ps1 -Node <node.exe> -Pnpm <pnpm.mjs>
# 含部署版冒烟
..\..\tools\web-ci.ps1 -Node <node.exe> -Pnpm <pnpm.mjs> -Smoke -WebBase http://127.0.0.1:18080

... run typecheck   # vue-tsc --noEmit
... run test        # Vitest（11 例：信封/错误映射/权限/JSON）
... run build       # typecheck + vite build → dist/
```

## 浏览器冒烟

需要本机 Edge；`playwright-core` 使用 `channel: 'msedge'`，不下载浏览器。

```powershell
$env:WEB_BASE='http://127.0.0.1:5173'
$env:LOG_DIR='..\..\logs\week-30'
node scripts\smoke.mjs          # 平台 8 项
node scripts\enterprise-smoke.mjs  # 企业页 9 项（临时库，finally 清理）
```

- `SMOKE_SKIP_AGENT=1` 跳过真实 Agent 运行（避免重复造待审数据）。
- 企业冒烟只操作 `smoke-` 前缀临时库，不动 `医疗随访规范库`。
- 输出 PASS/FAIL，截图写到 `LOG_DIR`。

## 结构

```text
src/
├── api/        # http（信封/错误/401）、platform、enterprise（satoken）、types
├── stores/     # auth（平台）、enterpriseAuth（企业）
├── router/     # 路由 + 登录/角色守卫
├── layouts/    # AppLayout（菜单/角标/用户）
├── views/      # 10 页：登录/工作台/Agent/执行/详情/审批/随访/知识库/用户/企业知识库
├── components/ # StatusTag/PageHeader
└── utils/      # format/permissions
```

## 约定

- 后端信封：只认 `code === "SUCCESS"`；401 清 sessionStorage 回登录；
- token 存 sessionStorage；不写日志；模型输出按文本渲染（禁原样 `v-html`）；
- 时间统一由 `dayjs` 从 UTC 转本地；
- 菜单/按钮按角色收敛，但权限最终以后端 403 为准。
