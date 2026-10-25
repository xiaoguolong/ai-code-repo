# apps/web — 医疗 SaaS AI Agent 平台前端（Week 29）

> Vue 3 + Vite + TypeScript + Pinia + Vue Router + Ant Design Vue 4.x。
> 同域部署：nginx `/` → 静态、`/api` → 平台 8084、`/ent` → enterprise 8081（第 30 周用）。

## 开发

```powershell
# Node/pnpm 用 DSH 自带运行时（勿改全局 PATH）
node <dsh>/dependencies/node/bin/node.exe <dsh>/dependencies/pnpm/bin/pnpm.mjs install
node <dsh>/dependencies/node/bin/node.exe <dsh>/dependencies/pnpm/bin/pnpm.mjs run dev
```

- 本地 5173；Vite 代理：`/api` → `127.0.0.1:8084`、`/ent` → `127.0.0.1:8081`。
- 连远端平台时：设置 `VITE_PLATFORM_TARGET=http://127.0.0.1:18084`（SSH 隧道），再 `run dev`。
- 演示账号：`admin/admin123`、`operator/operator123`、`viewer/viewer123`。

## 质量门禁

```powershell
... run typecheck   # vue-tsc --noEmit
... run test        # Vitest（11 例：信封/错误映射/权限/JSON）
... run build       # typecheck + vite build → dist/
```

## 浏览器冒烟

需要本机 Edge；`playwright-core` 使用 `channel: 'msedge'`，不下载浏览器。

```powershell
$env:WEB_BASE='http://127.0.0.1:5173'
$env:LOG_DIR='..\..\logs\week-29'
node scripts\smoke.mjs
```

- `SMOKE_SKIP_AGENT=1` 跳过真实 Agent 运行（避免重复造待审数据）。
- 输出 PASS/FAIL，截图写到 `LOG_DIR`。

## 结构

```text
src/
├── api/        # http（信封/错误/401）、platform（接口）、types
├── stores/     # auth（token/me/角色）
├── router/     # 路由 + 登录/角色守卫
├── layouts/    # AppLayout（菜单/角标/用户）
├── views/      # 9 页：登录/工作台/Agent/执行/详情/审批/随访/知识库/用户
├── components/ # StatusTag/PageHeader
└── utils/      # format/permissions
```

## 约定

- 后端信封：只认 `code === "SUCCESS"`；401 清 sessionStorage 回登录；
- token 存 sessionStorage；不写日志；模型输出按文本渲染（禁原样 `v-html`）；
- 时间统一由 `dayjs` 从 UTC 转本地；
- 菜单/按钮按角色收敛，但权限最终以后端 403 为准。
