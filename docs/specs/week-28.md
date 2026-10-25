# Spec: Week 28 — 用户生命周期与结构（TD-06 停用 + 三个大类拆分）

> 来源：`docs/specs/week-26-candidates.md` 第 2 节 P1 #4（TD-06 平台用户停用）与
> 第 3 节 P2 #1（拆分超 200 行类）；`docs/specs/week-24-candidates.md` TD-06 条目。
>
> 第 28 周不引入新依赖、不新增 Agent/Tool；只补「离职/停用」语义，并把三个历史大类按职责拆开。
> 页面（第 29 周）不在本周范围。

## Objective

1. **TD-06 平台用户停用**：`platform_user.disabled_at` + 登录/verify 拒绝 + admin 停用/恢复 +
   平台会话踢出 + 审计；**不做硬删**（外键与历史记录保留）。
2. **结构收口**：把 `PlatformExecutionUseCase`（431）、`FollowUpTaskUseCase`（290）、
   `PlatformMemoryUseCase`（241）拆到 200 行以内，**行为不变**、根聚合零回退。

## Tech Stack

- 复用：Spring Boot 3.4.5、PostgreSQL 16、Flyway、Sa-Token、H2（`MODE=PostgreSQL`）单测。
- 不新增依赖；新增迁移 `V11`。

## Commands

- 不改 `JAVA_HOME`；Maven 用 `-Djdk.17.home=E:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot`
- 测试：`powershell -NoProfile -ExecutionPolicy Bypass -File .\run-maven-jdk17.ps1 test`
- 单模块：`.\run-maven-jdk17.ps1 -pl apps/spring-ai-alibaba-agent -am test`

## Boundaries

- **Always**：先写/改测试再改行为；拆分只搬代码不改语义；停用失败可恢复；审计不写密码。
- **Ask first**：要改租户/角色模型、要硬删用户、要动 enterprise/gateway 会话策略。
- **Never**：硬删 `platform_user`（`execution_record` / `platform_approval` / 记忆外键）；
  把停用原因返回给登录方（避免账号探测）；把密码哈希写进响应/日志。

## 一、范围

| # | 事项 | 交付 | 预估 |
|---|------|------|------|
| 1 | **TD-06 停用/恢复** | V11 `disabled_at`；`PlatformUser` + 端口/适配器；登录/verify 拒绝；`/auth/me` 防御；平台会话踢出；admin 列表/停用/恢复；审计 | 1.5d |
| 2 | **拆 `PlatformExecutionUseCase`** | 运行时/记录工厂/观测/查询四类拆分 | 0.5d |
| 3 | **拆 `FollowUpTaskUseCase`** | 生成/查询拆分；Action 用例改用查询类 | 0.5d |
| 4 | **拆 `PlatformMemoryUseCase`** | 写入/查询拆分 | 0.5d |

**不做**：

- 用户硬删、`sso_user_link` 级联删除（等产品语义）；
- 租户管理员自助停用用户（当前仅平台 admin）；
- gateway / enterprise 已签发会话的主动吊销（它们各有 TTL；平台会话本周踢出）；
- 用户列表分页/搜索 UI（第 29 周前端；本周接口先给全量/上限）；
- 第 29 周页面、SSE、科室授权。

## 二、TD-06 设计

### 2.1 数据模型（V11）

```sql
ALTER TABLE platform_user ADD COLUMN IF NOT EXISTS disabled_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX IF NOT EXISTS idx_platform_user_disabled ON platform_user (disabled_at);
```

- `NULL` = 正常；非空 = 停用时间（保留原值，便于审计/恢复）；
- **不删行**：执行记录、审批、随访、记忆、审计全部保留；
- `save`（seed/测试）**不覆盖** `disabled_at`，避免重启 seed 把停用用户重新启用；
  停用/恢复只走 `updateDisabledAt`。

### 2.2 语义矩阵

| 场景 | 行为 |
|------|------|
| 停用用户登录 `/auth/login` | **401**，与密码错误同文案（不暴露「被停用」）；审计 `LOGIN_FAILED` detail 带 `disabled=true` |
| 停用用户 SSO `/auth/verify` | **401** 同上（enterprise 登录随之失败） |
| 停用用户已有平台会话 | 停用时 `StpUtil.logout(userId)` 踢出；`/auth/me` 再校验 `disabledAt` 防御 |
| 停用用户历史数据 | 管理员仍可查询执行/审批/随访/记忆（`getUser` 不阻断读取） |
| admin 停用自己 | **400**（防止把自己锁死） |
| 重复停用 / 重复恢复 | 幂等 200，不重复写审计 |
| 不存在用户 | 404 `PLATFORM_NOT_FOUND` |
| 非 admin 调用 | 403 `FORBIDDEN` |

### 2.3 API（详见 `docs/api/week-28-api.md`）

- `GET /api/v1/platform/users`（admin）：列出用户与停用状态；
- `POST /api/v1/platform/users/{userId}/disable`（admin）：停用 + 踢会话；
- `POST /api/v1/platform/users/{userId}/enable`（admin）：恢复；
- 审计动作：`USER_DISABLE` / `USER_ENABLE`（detail 只带 userId/username）。

### 2.4 幂等与并发

- `updateDisabledAt` 用 `UPDATE ... WHERE id=?`；返回 false = 用户不存在；
- 已停用再停用：用例先查，直接返回当前视图（不写第二条审计）；
- 停用 + 踢会话：会话踢出失败只 WARN，停用状态已落库（以 `/auth/me` 的 disabled 检查兜底）；
- 单实例假设：踢会话立即生效；多实例时 Sa-Token 共享存储同理。

### 2.5 seed 兼容（关键）

`PlatformRbacSeedInitializer` / `PlatformHospitalBSeedInitializer` 启动时会 `save` 用户；
JDBC 适配器的 `save` **不更新** `disabled_at`，InMemory 适配器合并时保留旧 `disabledAt`。
测试必须覆盖：停用 → `save`（模拟重启 seed）→ 仍停用。

## 三、类拆分设计（行为不变）

| 原类（行数） | 拆分后 | 职责 |
|--------------|--------|------|
| `PlatformExecutionUseCase`（431） | `PlatformExecutionUseCase` | `runAgent` 编排、span 生命周期、终态审计 |
| | `PlatformExecutionRecorder` | 构造 RUNNING/COMPLETED/FAILED/PENDING 记录、JSON 序列化 |
| | `PlatformExecutionObservability` | 成功/失败/待审的 span 属性 + 指标 + trace 维度 |
| | `PlatformExecutionPendingApproval` | HIGH 待审：创建审批 + 待审记录 + 指标 |
| | `PlatformExecutionQueryUseCase` | 列表/详情 + 越权读审计 |
| `FollowUpTaskUseCase`（290） | `FollowUpTaskUseCase` | 运行 Agent 生成任务 + 审批通过生成任务 + 审计 |
| | `FollowUpTaskQueryUseCase` | 列表/详情/站内提醒/范围解析 |
| | `FollowUpTaskActionUseCase`（改） | 完成/取消，改用查询类取任务 |
| `PlatformMemoryUseCase`（241） | `PlatformMemoryUseCase` | 查询 + 范围解析 + 语义/时间检索 |
| | `PlatformMemoryWriteUseCase` | 保存记忆 + TTL + 向量来源 + 向量化失败降级 |

约束：

- 只搬代码，不改 public 行为/错误码/审计动作/指标口径；
- Controller 按职责注入新类；既有测试可保留（必要时改构造/调用点）；
- 拆分后每个类 **< 200 行**（含 JavaDoc），`wc -l` 为证；
- 根聚合用例数不低于 Week 27 的 **971**。

## 四、Success Criteria

- [x] V11 在 H2 首次 `applied 11 migrations ... now at version v11`，重启只校验；`disabled_at` 列/索引存在；
- [x] 停用用户：登录 401、verify 401（文案与密码错一致），审计 `LOGIN_FAILED` 含 `disabled=true`；
- [x] 停用用户已有平台 token：`/auth/me` 401（会话已踢出或 disabled 检查拦截）；
- [x] admin：`GET /users` 200；停用/恢复 200；重复操作幂等；停用自己 400；operator 403；不存在 404；
- [x] 停用只加列不删行：执行/审批/随访/记忆/审计历史数据保留（查询路径不校验 disabled）；
- [x] seed 重跑（`save`）不会把停用用户恢复；
- [x] 审计 `USER_DISABLE` / `USER_ENABLE` 各一条（幂等不重复）；
- [x] 三个原类拆分后均 < 200 行（118 / 151 / 122）；拆出的类职责单一、有 JavaDoc；
- [x] 根聚合 **990** 全绿（Week 27 基线 971，+19；platform 709）；
- [x] 文档：Spec / 架构 / 接口 / 实现日志 / README / 审计事件表同步。

> 云端 V11 发布与真机停用/恢复**尚未执行**（无运维窗口）：发布平台 jar 后 Flyway 自动应用 V11，
> 按第 2 节做「停用 → 旧 token 401 → 恢复 → 重启不被 seed 覆盖」复核。

### 4.1 交付结果（2026-10-25）

| 门禁 | 结果 |
|------|------|
| 根聚合 | **990** 用例全绿（ai-core 90 / enterprise 70 / patient 21 / platform 709 / gateway 100） |
| platform 模块 | 690 → **709**（+19） |
| V11（H2） | 首次 `applied 11 migrations ... now at version v11`；重启只校验 |
| TD-06 语义 | 停用/恢复/踢会话/幂等/自锁/seed 保留/审计 共 19 例 |
| 拆分行数 | `PlatformExecutionUseCase` 431→118、`FollowUpTaskUseCase` 290→151、`PlatformMemoryUseCase` 241→122 |
| 云端 V11 | **未执行**：发布平台 jar 后自动应用，按第 2 节复核 |

## 五、风险与回滚

| 风险 | 影响 | 缓解 |
|------|------|------|
| seed 覆盖停用状态 | 重启后被停用用户复活 | `save` 不写 `disabled_at`；InMemory 合并保留；单测锁定 |
| 会话踢出失败 | 停用后旧 token 仍可用 | `/auth/me` 检查 disabled；必要时重启/等 TTL；日志 WARN |
| 误停用管理员 | 无人可管理 | 禁止停用自己；保留 admin 账号文档；恢复接口幂等 |
| 拆分改变行为 | 回归 | 只搬代码；根聚合 971 全绿；关键路径测试保留 |
| V11 加列不兼容 H2 | 迁移失败 | `ADD COLUMN IF NOT EXISTS` + 标准类型；H2/PG 同脚本 |
| gateway/enterprise 旧会话 | 停用后仍可访问各自入口 | 本周边界：平台会话踢出；gateway/enterprise TTL 到期；文档登记 |

回滚：代码回滚到 Week 27；V11 只加列，旧代码可运行；如需彻底回退先备份再
`ALTER TABLE platform_user DROP COLUMN disabled_at; DROP INDEX idx_platform_user_disabled;`。

## 六、ADR

| 决策 | 选项 | 选择 | 理由 |
|------|------|------|------|
| 停用还是硬删 | `disabled_at` / 物理删 | **停用** | 外键与历史审计必须保留；硬删需级联策略 |
| 登录失败文案 | 明确「已停用」 / 统一「凭据无效」 | **统一 401** | 避免账号状态探测（与租户停用同口径） |
| 会话处理 | 只靠 TTL / 主动踢出 | **踢出 + me 防御** | 离职场景要立即失效 |
| 停用自己 | 允许 / 禁止 | **禁止** | 防止唯一 admin 自锁 |
| 记录方式 | 布尔 / 时间戳 | **时间戳** | 保留「何时停用」证据，恢复后置空 |
| 拆分方式 | 按层拆 / 按用例拆 | **按用例职责拆** | Controller 注入清晰，单类职责单一 |

## 七、已知边界 / Open Questions

- gateway 与 enterprise 的会话有自己的 TTL，停用不跨进程踢出；若要求「一处停用、全端立即失效」，
  需要共享会话表或每请求回源 `/auth/me`，留到有真实合规要求时做。
- 是否允许租户 admin 停用本租户用户？当前仅平台 admin；等租户管理面（第 29 周后）再定。
- 停用是否要联动 `sso_user_link`？当前不删链、不删企业用户；恢复后 SSO 仍可用。
- 是否需要「停用原因/操作人」独立字段？当前审计已有操作人，原因放 detail 需产品确认。
