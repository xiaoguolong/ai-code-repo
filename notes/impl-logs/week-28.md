# Week 28 实现日志 — 用户生命周期（TD-06）与结构收口

> 来源：`docs/specs/week-28.md` ｜ 架构：`docs/architecture/week-28-architecture.md` ｜
> 接口：`docs/api/week-28-api.md` ｜ 迁移：`V11__platform_user_disable.sql`。

## 1. 本周目标

1. **TD-06**：平台用户「停用/恢复」——`disabled_at` + 登录/verify 拒绝 + 会话踢出 + admin 接口 + 审计；
   **不硬删**，历史数据全保留。
2. **结构收口**：`PlatformExecutionUseCase`（431）、`FollowUpTaskUseCase`（290）、
   `PlatformMemoryUseCase`（241）全部拆到 200 行以内，公共行为不变。

## 2. 边界

**做了**：

- V11 `platform_user.disabled_at` + 索引；
- `PlatformUser`/端口/两个适配器（JDBC/内存）的停用状态；
- 登录与 SSO `/verify` 统一 401、`/auth/me` 防御、停用时踢平台会话；
- admin 列表/停用/恢复 3 个接口 + 审计 `USER_DISABLE`/`USER_ENABLE`；
- 三个大类拆分（新增 9 个协作类，原类变门面/编排）；
- 19 个新用例（用户管理 8 + 认证 3 + JDBC 4 + HTTP 4）。

**没做**：

- 用户硬删、`sso_user_link` 级联；
- gateway / enterprise 已签发会话的跨进程吊销（平台会话已踢出，其它入口等 TTL）；
- 租户管理员自助停用、用户管理页面（第 29 周前端）；
- 云端 V11 发布（**未执行**，步骤见 Spec 第四节）。

## 3. 增量架构

见 `docs/architecture/week-28-architecture.md`。一句话：

> 停用 = `disabled_at` + 会话踢出；认证入口统一 401（不暴露状态），`/auth/me` 再兜底；
> 结构 = 执行/随访/记忆各自「编排 / 工厂 / 查询 / 观测」拆开，门面保持原 API。

## 4. 新增/改动类型清单

### 4.1 TD-06

| 类型 | 文件 | 职责 |
|------|------|------|
| 迁移 | `V11__platform_user_disable.sql` | `disabled_at` 列 + 索引 |
| 模型 | `PlatformUser`（改） | 加 `disabledAt`；保留 4/5 参构造器；`disabled()`/`withDisabledAt` |
| 端口 | `PlatformUserPort`（改） | `listAll`、`updateDisabledAt`；`save` 不覆盖停用 |
| 端口 | `PlatformSessionPort`（新） | `revokeAll(userId)`，不依赖 Sa-Token 类型 |
| 适配器 | `SaTokenPlatformSessionAdapter`（新） | `StpUtil.logout(userId)` |
| 适配器 | `Jdbc/InMemoryPlatformUserAdapter`（改） | 列映射；`save` 保留停用；`listAll/update` |
| 用例 | `PlatformUserAdminUseCase`（新） | admin 列表/停用/恢复、自锁保护、幂等、审计 |
| 视图/DTO | `PlatformUserView`、`PlatformUserResponse`、`PlatformUserListResponse` | 不返回密码哈希 |
| 控制器 | `PlatformUserController`（新） | `GET /users`、`POST /{id}/disable|enable` |
| 认证 | `PlatformAuthUseCase`（改） | `authenticate` 停用分支；`me` 防御 |
| 审计 | `AuditAction`（改） | `USER_DISABLE` / `USER_ENABLE`；Week 16 API 表同步 |

### 4.2 拆分

| 原类 | 拆出 | 行数 |
|------|------|------|
| `PlatformExecutionUseCase` | `PlatformExecutionRunner` | 174 |
| | `PlatformExecutionRecorder` | 110 |
| | `PlatformExecutionObservability` | 124 |
| | `PlatformExecutionPendingApproval` | 94 |
| | `PlatformExecutionQueryUseCase` | 97 |
| 原类（门面） | | **118** |
| `FollowUpTaskUseCase` | `FollowUpTaskFactory` | 138 |
| | `FollowUpTaskQueryUseCase` | 130 |
| 原类（门面） | | **151** |
| `PlatformMemoryUseCase` | `PlatformMemoryWriteUseCase` | 80 |
| | `PlatformMemoryQueryUseCase` | 116 |
| 原类（门面） | | **122** |

> 拆分策略：新协作者由原类构造函数内部组装（不是 Spring Bean），因此原构造器、控制器、
> 既有测试**零改动**；公共方法保留并委托。

## 5. RED

- TD-06：先定契约（V11/401/幂等/seed 不复活），再补 `PlatformUserAdminUseCaseTest`、
  `PlatformAuthUseCaseTest`（停用/me/seed）、`JdbcPlatformUserAdapterTest`（列/保留）、
  `PlatformUserHttpTest`（停用→旧 token 401→恢复）。
- 拆分：以**既有 971 用例作为回归门禁**，先拆再跑；任何行为漂移都会在既有断言上暴露。
- 如实记录：本周实现顺序是「先代码骨架、后测试」，不是逐条先跑红；测试锁定的是契约而非实现细节。

## 6. GREEN

### 6.1 TD-06

- `authenticate`：密码正确后先查 `disabledAt`，非空 → 审计 `LOGIN_FAILED detail disabled=true` → 401；
  与密码错/租户停用同文案；
- 停用时 `updateDisabledAt` → `revokeAll`（失败只 WARN）→ 审计；
- `/auth/me`：`disabled` 直接 401，防「会话没踢干净」；
- 幂等：已停用再停用返回原状态，不重复审计；恢复同理；
- 自锁：停用自己 400；
- `save` 不写 `disabled_at`：JDBC UPDATE 不含该列、INSERT 可带；内存实现合并保留旧值。

### 6.2 拆分

- 执行：`Runner` 拿原 `runAgent` 全流程；`Recorder` 造 RUNNING/COMPLETED/FAILED/PENDING；
  `Observability` 出 span 属性/指标/trace 维度；`PendingApproval` 建审批 + 待审记录；`Query` 列表/详情 + 越权审计；
- 随访：`Factory` 负责 Agent 计划落任务与审批通过生成（含唯一标题幂等/失败审计）；
  `Query` 负责列表/详情/提醒/范围；`Action` 仍用门面的 `requireTask`；
- 记忆：`Write` 负责 TTL/向量来源/向量化失败降级；`Query` 负责范围/时间/语义检索。

## 7. 重构记录

| 动作 | 前 | 后 |
|------|----|----|
| `PlatformExecutionUseCase` | 431 行 | 118 行（+5 协作者） |
| `FollowUpTaskUseCase` | 290 行 | 151 行（+2 协作者） |
| `PlatformMemoryUseCase` | 241 行 | 122 行（+2 协作者） |
| 控制器/测试构造器 | — | 未改（门面兼容） |

拆分过程中出现过一次执行用例的 `try-with-resources` 占位写法，编译期即改正，未进入测试；
此外无生产行为改动。

## 8. 质量门禁

| 门禁 | 结果 |
|------|------|
| 编译（platform 主/测试源码） | BUILD SUCCESS |
| platform 全量 | **709 用例，0 失败**（Week 27 基线 690，+19） |
| 根聚合 | **990 用例，0 失败**（Week 27 基线 971，+19） |
| H2 迁移 | 首次 `Successfully applied 11 migrations ... now at version v11`；重启只校验 |
| 三个原类行数 | 118 / 151 / 122（均 < 200） |
| 禁用/跳过用例 | 0（未使用 `@Disabled`） |

> 根聚合分布：ai-core 90 / enterprise 70 / patient 21 / platform 709 / gateway 100 = **990**。

## 9. 验证证据

| 证据 | 位置 |
|------|------|
| platform 全量（709 + V11 首次；含 TD-06 四类） | `logs/week-28/test/target-week28-platform-full.log` |
| 根聚合（990） | `logs/week-28/test/target-week28-full.log` |
| 接口示例 | `docs/api/week-28-api.md` |

**未执行**：云端 V11 发布与真机停用/恢复。发布平台 jar 后 Flyway 自动应用 V11；
真机建议顺序：admin 停用测试账号 → 旧 token 访 `/auth/me` 401 → 登录 401 → 恢复 → 登录 200 →
重启平台确认停用状态不被 seed 覆盖。

## 10. 风险与下周输入

| # | 风险 / 待办 | 影响 | 下一步 |
|---|-------------|------|--------|
| 1 | 云端未应用 V11 | 停用接口/列不存在 | 发布平台 jar（Flyway 自动 V11），按第 9 节真机步骤复核 |
| 2 | gateway/enterprise 旧会话 | 停用后仍可访问各自入口 | 平台已踢出；其它入口等 TTL；合规要求高时做共享会话/回源 |
| 3 | 无租户管理员自助停用 | 只能平台 admin 操作 | 等租户管理面（第 29 周后） |
| 4 | 用户列表无分页 | 用户量增长后变慢 | 第 29 周前端需要时加 `limit/offset` |
| 5 | 停用原因未结构化 | 只能从审计 detail 看 | 产品确认后加 `disable_reason` |
| 6 | 第 29 周前端 | 用户管理页尚未有 UI | 按 `week-29-candidates.md` 范围 B 做平台 8 页 |

## 11. 决策记录 ADR

| 决策 | 选项 | 选择 | 理由 |
|------|------|------|------|
| 停用 vs 硬删 | `disabled_at` / 物理删 | **停用** | 外键/历史/审计必须保留；硬删需级联策略 |
| 对外文案 | 明确停用 / 统一凭据无效 | **统一 401** | 防账号状态探测（与租户停用同口径） |
| 会话 | 只靠 TTL / 主动踢出 | **踢出 + me 防御** | 离职场景要立即失效 |
| 停用自己 | 允许 / 禁止 | **禁止** | 防唯一 admin 自锁 |
| seed 与停用 | `save` 覆盖 / 保留 | **保留** | 重启不能复活停用账号 |
| 拆分方式 | 按层拆 / 按用例拆 | **按用例职责拆 + 门面兼容** | 控制器/测试零改动，单类职责单一 |

## 12. 未执行与边界

本周**未做云端发布**，也没有真机环境缺陷可记录；V11 是只加列，回滚只需代码回滚
（旧代码忽略新列），彻底回退先备份再 `DROP COLUMN disabled_at`。
第 29 周按既定计划做平台 8 页前端（`apps/web` + Ant Design Vue），
用户管理页可直接调用本周三个接口。
