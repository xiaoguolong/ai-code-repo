# Spec: Week 30 — 企业知识库页 + 前端 CI/冒烟

> 来源：`week-29-candidates.md` 的「第 30 周」与 `week-29.md` 的边界；第 29 周平台 9 页已上线。
> 本周把「知识库从哪来」补进浏览器，并把前端质量门禁收敛成一条命令。

## Objective

1. **企业知识库页**（`/ent/knowledge`）：企业登录 → 建库 → 纯文本入库 → 文档列表/删除；
   让「建库 → 入库 → 平台绑定 → 问答」全链路不再需要脚本。
2. **前端 CI/冒烟**：一条命令跑 install/typecheck/test/build（可选 Playwright 冒烟），
   把第 29 周的手工步骤固化成可重复门禁。
3. **SSE 决策**：`ChatModelPort` 当前只有同步 `chat(...)`，无流式方法；写 ADR 顺延，
   不在本周做「假流式」。

## Tech Stack

- 复用 `apps/web`（Vue3 + Vite + TS + Pinia + Ant Design Vue）；
- enterprise 8081 既有 API：`/api/v1/auth/*`、`/api/v1/knowledge-bases/*`；
- nginx `/ent/api/` 反代已就绪（第 29 周配置，去前缀转发 8081；`/ent/knowledge` 是 SPA 路由）；
- 测试：Vitest + Playwright-core（Edge），PowerShell 一键脚本。

## Commands

```powershell
# 前端门禁（Node/pnpm 用 DSH 运行时，见 tools/web-ci.ps1 参数）
.\tools\web-ci.ps1 -Node <node.exe> -Pnpm <pnpm.mjs>
.\tools\web-ci.ps1 -Node <node.exe> -Pnpm <pnpm.mjs> -Smoke -WebBase http://127.0.0.1:18080
```

## Boundaries

- **Always**：企业 token 与平台 token 分开存（`satoken` 头）；企业 401 只清企业会话；
  删除 KB/文档必须确认；冒烟创建临时 KB 并在 finally 清理。
- **Ask first**：改 enterprise 契约、要上 TLS/域名、要动企业库真实数据。
- **Never**：删除既有 `医疗随访规范库`；把企业 token 写进平台 store；做假 SSE。

## 一、范围

| # | 事项 | 交付 | 预估 |
|---|------|------|------|
| 1 | 企业登录/会话 | `stores/enterpriseAuth`、`api/enterprise`（`satoken` 头） | 0.5d |
| 2 | 知识库管理 | 列表/创建/删除、选中库 | 0.5d |
| 3 | 文档管理 | 文档列表、纯文本入库（name+content）、删除 | 0.5d |
| 4 | 导航/权限 | `/ent/knowledge` 路由（admin）、菜单项 | 0.2d |
| 5 | 前端 CI | `tools/web-ci.ps1`（install/typecheck/test/build/smoke） | 0.3d |
| 6 | 冒烟 | `scripts/enterprise-smoke.mjs`：建临时库→入库→删文档→删库 | 0.5d |
| 7 | 部署/文档 | 重新 build + 静态部署 + 真机冒烟 + 文档 | 0.5d |

**不做**：

- **SSE 流式**：见 ADR；需要 `ChatModelPort` 增加流式契约 + provider 适配 + 前端 reader，
  不是本周 3 天能可靠交付的；
- PDF/OCR 上传（当前 enterprise 入库是 JSON 文本；PDF 走 OCR 是独立链路）；
- 企业知识库编辑/切片预览；租户级知识库授权（P1 #2）；
- TLS/域名、前端 CI 平台接入（只做本地一键脚本）。

## 二、企业页设计

### 2.1 会话

| 项 | 平台 | 企业 |
|----|------|------|
| token key | `aicode_platform_token` | `aicode_enterprise_token` |
| header | `Authorization: Bearer` | **`satoken`**（enterprise `sa-token.token-name`） |
| 401 | 清平台会话回 `/login` | 清企业会话，页面内显示企业登录卡 |
| 登录 | `/api/v1/platform/auth/login` | `/ent/api/v1/auth/login`（SSO，同一账号） |

### 2.2 页面流

```text
/ent/knowledge
 ├─ 未登录：企业登录卡（用户名/密码）→ POST /ent/api/v1/auth/login
 └─ 已登录：
     ├─ 知识库表（id/name/description/createdAt）[新建] [删除]
     ├─ 选中库 → 文档表（documentId/name/chunkCount/createdAt）[入库] [删除]
     └─ 入库弹窗：name + content（纯文本，≤100000）
```

- 创建/删除只对当前企业账号名下的库；页面不展示他人库；
- 删除确认文案明确「不可恢复」；冒烟只操作 `smoke-` 前缀临时库；
- 知识库列表为空时提示「先建库，再入库，最后到平台导入绑定」。

### 2.3 API 映射

| 动作 | 方法 + 路径（经 `/ent`） | 说明 |
|------|--------------------------|------|
| 企业登录 | `POST /ent/api/v1/auth/login` | `{username,password}` → `{token,user}` |
| 企业登出 | `POST /ent/api/v1/auth/logout` | `satoken` 头 |
| 库列表 | `GET /ent/api/v1/knowledge-bases` | 当前用户名下 |
| 建库 | `POST /ent/api/v1/knowledge-bases` | `{name,description}`，201 |
| 删库 | `DELETE /ent/api/v1/knowledge-bases/{id}` | 有文档时后端可能 409 |
| 文档列表 | `GET /ent/api/v1/knowledge-bases/{id}/documents` | |
| 文本入库 | `POST /ent/api/v1/knowledge-bases/{id}/documents` | `{name,content}` |
| 删文档 | `DELETE /ent/api/v1/knowledge-bases/{id}/documents/{docId}` | |

## 三、前端 CI

`tools/web-ci.ps1`：

```text
install（可选 -SkipInstall）→ typecheck → test → build
   → 可选 -Smoke：node scripts/smoke.mjs（需 WEB_BASE 可达）
```

- 任一阶段非 0 立即失败；输出阶段耗时；
- 不修改全局 PATH/JAVA_HOME；Node/pnpm 由参数传入；
- 冒烟默认跳过（需要后端与数据），CI 可用 `-Smoke` 开启。

## 四、Success Criteria

- [x] 企业页：admin 登录成功；建临时库、列表可见、入库成功、文档列表可见、删文档/删库成功；
- [x] 既有 `医疗随访规范库` 未被修改/删除（冒烟后仍 1 库 / 1 文档）；
- [x] 企业 401 只清企业 token，页面回企业登录卡，不影响平台会话；
- [x] 平台菜单按角色显示「企业知识库」（仅 admin）；viewer 直访被路由拦截；
- [x] `tools/web-ci.ps1` 一条命令 typecheck/test/build 全绿；`-Smoke` 可跑部署版；
- [x] 云端 `/ent/api` 反代、企业页、冒烟均通过；部署后平台页不回归（8/8）；
- [x] SSE 有 ADR 记录（`docs/adr/0001-sse-deferred.md`）；
- [x] 文档：Spec/架构/接口/实现日志/README/日志索引。

### 4.1 交付结果（2026-10-25）

| 门禁 | 结果 |
|------|------|
| 前端 CI | typecheck 0、Vitest **11**、build 成功、exit 0 |
| 企业页冒烟 | **PASS=9 FAIL=0**（登录/建库/入库/删文档/删库/既有库未变/企业 401 隔离/平台会话仍在） |
| 平台回归 | **PASS=8 FAIL=0**（含 viewer 直访企业页拦截） |
| 数据清理 | 临时库已删；既有库 1、文档 1 未动 |
| 云端入口 | `/` 200、`/ent/knowledge` 200、`/ent/api` 登录 200 |
| nginx | `location /ent/api/` 只代理 API；SPA 路由走 fallback |
| 后端 | 无 Java 改动；沿用 Week 28 根聚合 **990** |

## 五、风险与 ADR

| 风险 | 缓解 |
|------|------|
| 企业 token 与平台串号 | 分 key + 分 header；企业 401 不触发平台登出 |
| 误删既有知识库 | 删除二次确认；冒烟只建 `smoke-` 临时库；finally 清理 |
| 入库大文本卡顿 | content ≤100000（后端限制）；前端 show-count + 提交 loading |
| 企业 SSO 不可用 | 显示企业登录错误，不影响平台页 |
| 冒烟污染数据 | 临时库名带时间戳；失败也在 finally 删除 |
| SSE 期望 | ADR 明确：当前 `ChatModelPort` 同步；真流式需新契约，顺延 31 |

**ADR（SSE 顺延）**：`ChatModelPort` 只有 `ChatResult chat(...)`，无 `Flux`/回调流。
要做真 SSE 至少需要：① 端口新增流式方法；② Spring AI/OpenAI 适配器实现；③ 平台 SSE 端点
（鉴权、Guardrail、执行记录、取消）；④ 前端 reader。收益是首字延迟，但当前同步 200 已可用；
本周优先企业页与 CI，SSE 顺延第 31 周（或前端明确要求时）。

## 六、五天排期

| 天 | 交付 |
|----|------|
| D1 | 企业 API 客户端 + auth store + 登录卡 |
| D2 | 知识库列表/建/删 |
| D3 | 文档列表/入库/删除 + 页面联调 |
| D4 | 前端 CI 脚本 + 企业冒烟脚本 |
| D5 | 构建/部署/真机冒烟/文档 |

## 七、已知边界

- 企业页只支持纯文本入库；PDF/OCR 仍在 enterprise 独立链路；
- 企业页仅 admin（SSO 映射通常只给管理员）；
- 无 TLS/域名；无前端 CI 平台，只有本地脚本；
- SSE、科室授权、导出、HIS/LIS 仍按触发条件排后续。
