# Week 30 实现日志 — 企业知识库页 + 前端 CI/冒烟

> 来源：`docs/specs/week-30.md`；架构：`docs/architecture/week-30-architecture.md`；
> 接口：`docs/api/week-30-api.md`；SSE ADR：`docs/adr/0001-sse-deferred.md`。

## 1. 本周目标

1. 企业知识库页：企业登录 → 建库 → 纯文本入库 → 文档列表/删除；
2. 前端一键 CI：install/typecheck/test/build（可选冒烟）；
3. SSE 决策：`ChatModelPort` 同步，写 ADR 顺延。

## 2. 边界

**做了**：

- `/ent/knowledge` 页面（仅 admin）、企业独立 token（`satoken`）；
- 知识库 CRUD、文档列表/文本入库/删除；
- 导航/路由守卫/企业 401 只清企业会话；
- `tools/web-ci.ps1`（PowerShell 5.1/7 均可）；
- `scripts/enterprise-smoke.mjs`（临时库 + finally 清理）；
- nginx 修复：`/ent/api/` 只代理 API，`/ent/knowledge` 走 SPA；
- 静态重新部署 + 企业/平台真机冒烟。

**没做**：

- SSE（ADR-0001）；
- PDF/OCR 上传（企业另有 `/files`+`/ocr` 链路）；
- 知识库编辑/切片预览/租户授权；
- TLS/域名、CI 平台接入、Playwright 多浏览器矩阵。

## 3. 新增/修改

| 类别 | 文件 |
|------|------|
| 企业 API | `apps/web/src/api/enterprise.ts`（`/ent` + `satoken`） |
| 企业会话 | `apps/web/src/stores/enterpriseAuth.ts` |
| 页面 | `apps/web/src/views/EnterpriseKnowledgeView.vue` |
| 路由/菜单 | `router/index.ts`、`utils/permissions.ts`、`AppLayout.vue` |
| 类型 | `api/types.ts`（Enterprise*） |
| 入口 | `main.ts` 注册企业 401 handler |
| CI | `tools/web-ci.ps1` |
| 冒烟 | `apps/web/scripts/enterprise-smoke.mjs` |
| nginx | `deploy/app/nginx/aicode.conf`（`/ent/api/`） |
| 文档 | week-30 Spec/架构/API/本日志/ADR/README/logs-index |

## 4. RED → GREEN

### 4.1 关键缺陷：`/ent/` 把 SPA 路由也反代了

首次真机企业页 **500**，控制台 `Failed to load resource: 500`。
根因：nginx `location /ent/` 匹配 `/ent/knowledge`，把页面请求转给 enterprise 8081（无此路由）。

修复：

```nginx
location /ent/api/ {
    proxy_pass http://127.0.0.1:8081/api/;   # /ent/api/v1/x -> /api/v1/x
}
# /ent/knowledge 落到 location / 的 try_files /index.html
```

Vite 开发代理同样改为只代理 `/ent/api`。修复后 `/ent/knowledge` 200，API 正常。

### 4.2 CI 脚本在 PowerShell 5.1 下的三个坑

1. UTF-8 无 BOM + 中文被 5.1 误读 → 脚本改纯 ASCII；
2. pnpm 的 `$ <script>` 横幅写 stderr，`$ErrorActionPreference=Stop` 触发 `NativeCommandError`
   → 单步临时 `Continue`，并把输出转成字符串；
3. pnpm 子进程找不到 `node` → 脚本把 `$Node` 所在目录前置到 `PATH`。

### 4.3 企业冒烟挂载时序

`page.goto(..., domcontentloaded)` 后 Vue 尚未挂载，`getByRole('企业登录').count()` 为 0，
脚本误判“已登录”而卡在列表等待。改为 `waitForFunction` 等“企业登录或既有库”出现。

### 4.4 表格列宽

企业页初版 ID/描述/文档 ID 列过宽，名称列被挤掉；改为名称列带副标题（描述/文档 ID），
删除独立宽列后布局正常。

## 5. 质量门禁

| 门禁 | 结果 |
|------|------|
| `tools/web-ci.ps1`（-SkipInstall） | install 跳过；typecheck 0；Vitest **11**；build 成功；**exit 0** |
| 企业页真机冒烟 | **PASS=9 FAIL=0**（登录/建库/入库/删文档/删库/既有库未变/企业 401 隔离/平台会话仍在） |
| 平台部署版回归 | **PASS=8 FAIL=0**（含 viewer 直访企业页拦截） |
| 数据清理 | 冒烟后企业库仍 **1** 个、文档 **1** 个（临时库已删） |
| 云端入口 | `/` 200、`/ent/knowledge` 200、`/ent/api` 登录 200 |
| 后端 | 本周无 Java 改动；沿用 Week 28 根聚合 **990** |
| 前端单测 | 11（权限测试增加企业菜单断言） |

## 6. 验证证据

| 证据 | 位置 |
|------|------|
| CI 控制台/日志 | `logs/week-30/ci-console.log`、`logs/week-30/ci/web-ci-*.log` |
| 企业页冒烟 | `logs/week-30/ui-smoke-enterprise.log` |
| 企业页截图 | `logs/week-30/enterprise/enterprise-page.png` |
| 平台回归 | `logs/week-30/ui-smoke-platform.log`、`logs/week-30/platform/` |
| 部署 | `logs/week-30/target-week30-deploy-web.log` |
| nginx 修复/重载 | `logs/week-30/target-week30-nginx-reload.log` |
| 备份 | `/opt/ai-code/backup/week29-20261025-172431`（部署脚本自动） |

## 7. 云端记录（2026-10-25）

| 步骤 | 时间（CST） | 结果 |
|------|-------------|------|
| 企业登录/KB 手工验证 | 17:08 | `satoken` 头；既有库 1、文档 1 |
| 首次部署企业页 | 17:18 | `/ent/knowledge` 500（nginx 误代理） |
| nginx 修复 | 17:19 | 只代理 `/ent/api/`；页面 200 |
| 企业页冒烟 | 17:20 | **PASS=7**；17:26 加 401 隔离后 **PASS=9** |
| 表格列宽修复 + 重部署 | 17:24 | 页面布局正常；冒烟 **PASS=9** |
| 平台回归 | 17:25 | **PASS=8**；viewer 直访企业页被拦 |

> 部署脚本 `cloud-week29-web.sh` 复用：备份 → 解压 → nginx reload → trust-forwarded-for →
> 平台重启 health 200。静态上一版在 `/opt/ai-code/web.prev`。

## 8. ADR（SSE）

见 `docs/adr/0001-sse-deferred.md`。摘要：`ChatModelPort` 只有同步 `chat(...)`，真流式需
新端口契约 + 适配器 + SSE 端点 + 前端 reader；本周优先企业页与 CI，顺延第 31 周。

## 9. 边界与下周

| # | 待办 | 说明 |
|---|------|------|
| 1 | SSE | ADR-0001；触发条件明确后再做 |
| 2 | PDF/OCR 上传 | 企业已有文件/OCR 链路，页面未接 |
| 3 | 知识库编辑/切片预览 | 需要后端编辑 API 与 UI |
| 4 | TLS/域名 | 当前 HTTP；正式对外前加证书 |
| 5 | CI 平台 | 现为本地 `tools/web-ci.ps1`；多环境时接 GitHub Actions/流水线 |
| 6 | 企业页权限 | 仅 admin；若开放 operator，需要企业侧租户授权模型 |

## 10. 结论

第 30 周把“建库/入库”补进浏览器，企业知识库全链路（企业建库 → 平台绑定 → 问答）可用；
前端门禁收敛为一条命令；SSE 有明确 ADR 与触发条件，不再悬空。
