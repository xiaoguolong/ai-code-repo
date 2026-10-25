# Week 23 实现日志 — 批次 1：患者分析 HITL + 长期记忆生命周期

- 日期：2026-10-25
- 批次：1（HITL + 记忆生命周期）+ 2（A1 SSO 运维令牌 + B2 按租户复制角色）
- 对应 Spec：`docs/specs/week-23.md`
- 对应架构：`docs/architecture/week-23-architecture.md`
- 对应接口：`docs/api/week-23-api.md`
- 对应阅读：`notes/week-23-hitl-memory-lifecycle.md`
- 部署/迁移手册：`docs/deploy/week-23-migration.md`
- Postman：`docs/postman/week-23.postman_collection.json`

## 1. 本批次目标

第 22 周收口时登记的两项「无外部阻塞」遗留：

1. **患者分析 HITL**：HIGH 风险报告不能直接进随访；要能跨进程暂停、人工批准/拒绝后再继续；
2. **长期记忆生命周期**：TTL、按范围删除、换 embedding 模型后的向量重建，并用 V6 迁移落库。

## 2. 边界

- Always：只有 HIGH 触发 HITL；中/低风险行为与第 21 周逐字段一致；暂停落库（不依赖内存 checkpoint）；
  决策不调用模型；删除/检索带 scope；admin 才能清理/重建；V6 双库可跑；中文 JavaDoc。
- Never：本周做批次 2（SSO 解绑 / 租户级角色）；改 Week 10 workflow HITL；把审批做成内存队列；
  把 TTL 默认设为「永久」；在日志/审计里写报告正文。

## 3. 增量架构

见 `docs/architecture/week-23-architecture.md`：平台编排层新增 `platform_approval` 状态机，
记忆适配器新增 `expires_at` / `embedding_model` 两列与运维端点；Agent 内部链路不改。

核心状态机：

```text
RUNNING --LOW/MEDIUM--> COMPLETED（立即写记忆）
RUNNING --HIGH--------> PENDING_APPROVAL（不写记忆，生成审批任务）
PENDING_APPROVAL --approved--> COMPLETED（写记忆）
PENDING_APPROVAL --rejected--> REJECTED（不写记忆）
RUNNING --exception--> FAILED
```

## 4. 新增/改动类型

| 类型 | 名称 | 职责 |
|------|------|------|
| 模型/枚举 | `PlatformApproval`、`ApprovalStatus` | 审批任务与状态 |
| 模型 | `ExecutionStatus`（改） | 新增 `PENDING_APPROVAL` / `REJECTED` |
| 模型 | `PlatformMemoryRecord`（改） | 新增 `expiresAt` / `embeddingModel` + 7 参兼容构造器 |
| 模型 | `PlatformRunOutput`（改） | 新增 `pendingApproval` + 3 参兼容构造器 |
| Port | `PlatformApprovalPort` | save/find/list/markDecided（原子） |
| Port | `PlatformMemoryPort`（改） | delete/purgeExpired/listStaleEmbeddings/updateEmbedding |
| UseCase | `PlatformApprovalUseCase` | create/list/get（待办） |
| UseCase | `PlatformApprovalDecisionUseCase` | decide（批准写记忆、拒绝不写） |
| 组件 | `PlatformExecutionRecordWriter` | 审批决策后的执行终态落库 |
| 工具 | `PlatformRunOutputJson`、`PlatformVectorCodec` | 输出 JSON 解析/序列化、向量编解码 |
| UseCase | `PlatformMemoryMaintenanceUseCase` | delete/purge/rebuild + 定时清理入口 |
| UseCase | `PlatformExecutionUseCase`（改） | pending 分支：建审批任务 + 待审执行记录 |
| UseCase | `PatientAnalysisUseCase`（改） | HIGH 不写记忆，交给审批 |
| UseCase | `PlatformMemoryUseCase`（改） | 保存时写 TTL 与向量来源 |
| Adapter | `JdbcPlatformApprovalAdapter` / `InMemoryPlatformApprovalAdapter` | 审批落库/进程内 |
| Adapter | `JdbcPlatformMemoryAdapter` / `InMemoryPlatformMemoryAdapter`（改） | 过期过滤 + 生命周期方法 |
| 调度 | `MemoryCleanupScheduler` | 每日 03:30 清理过期 |
| 配置 | `PlatformMemoryLifecycleProperties`、`MemoryEmbeddingIdentity` | TTL/批量；`provider:model:dimension` |
| Controller | `PlatformApprovalController` | 待办列表/详情/决策 |
| Controller | `PlatformMemoryController`（改） | DELETE / purge / rebuild |
| DTO | `ApprovalDecisionRequest`、`PlatformApprovalResponse`、`PlatformApprovalListResponse`、3 个记忆操作响应 | 协议体 |
| 迁移 | `V6__platform_memory_lifecycle_and_approval.sql` | 两列 + 审批表 + 索引 |
| 演示数据 | `HealthMetricTool` / `PatientLookupTool`（改） | P002 作为 HIGH 风险真机验收入口 |

## 5. RED（如实说明）

本批次**没有在写生产代码前单独留档 RED**：HITL 与记忆生命周期是同一条连续实现，
先把模型/端口/迁移/用例写完，再集中补测试。这与规范 4.1「先红灯再实现」有偏离，
与第 22 周批次 1 同类问题；补偿与真实红灯证据如下：

- **真实红灯 1（编译）**：`PlatformApprovalUseCase` 初版漏 `AuditLogPort` import，
  `test-compile` 直接失败；补 import 后通过。
- **真实红灯 2（用例）**：新增用例首轮 `Tests run: 56, Failures: 1`——
  `PlatformExecutionHitlTest.highRiskRunStopsAtPendingApproval...` 断言
  `output.path("memoryId").isNull()`；mock 的 `PlatformRunOutput` 没有该 key，得到 `MissingNode`。
  改为「缺失或 null 均可」后通过（生产路径 `toOutput` 会写 null）。
- **设计缺陷（写测试时发现并修）**：`InMemoryPlatformMemoryAdapter` 用过 `Instant.now()` 判过期，
  而用例用可注入 `Clock`；TTL 用例会出现「同一时间点在两处解释不同」的随机失败。
  已给适配器注入 `Clock`（无参构造器保留给既有测试），与 JDBC 的 `CURRENT_TIMESTAMP` 语义对齐。
- **测试隔离缺陷（真红风险）**：`JdbcPlatformApprovalAdapterTest` 与其它 `@SpringBootTest`
  共用同一个 H2 库；`listByUser` 精确断言会被先跑用例写入的审批污染。已加 `@BeforeEach`
  按本类专属 userId 清理。
- **断言假绿风险**：审批详情拒绝时 `memoryId` 是 JSON `null`，用 `doesNotExist()` 会误判；
  改为 `nullValue()`；记忆删除后的列表用 `not(hasItem(id))` 而非「空数组不存在」。

## 6. GREEN

### 6.1 模块全量

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-maven-jdk17.ps1 -pl apps/spring-ai-alibaba-agent -am test
```

```text
Tests run: 529, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

（Week 22 平台基线 478 → 529，新增 51。）

### 6.2 新增用例分布

| 测试类 | 例数 |
|--------|------|
| `PlatformApprovalUseCaseTest` | 11 |
| `PlatformExecutionHitlTest` | 5 |
| `PlatformApprovalHttpTest` | 6 |
| `JdbcPlatformApprovalAdapterTest` | 4 |
| `PlatformMemoryLifecycleTest` | 13 |
| `JdbcPlatformMemoryLifecycleTest` | 5 |
| `PlatformMemoryMaintenanceHttpTest` | 4 |
| `PatientAnalysisUseCaseTest`（增补） | +1 |
| `PlatformAgentRunnerPatientAnalysisTest`（增补） | +1 |
| `PlatformExecutionObservabilityTest`（增补） | +1 |
| **合计** | **51** |

### 6.3 根聚合

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-maven-jdk17.ps1 test
```

```text
ai-core 90 / enterprise 37 / patient 21 / platform 529 / gateway 100
=> 合计 777，Failures 0，Errors 0，BUILD SUCCESS
```

（Week 22 基线 726 → 777，新增 51；其它四个模块零变化。）

## 7. 重构

- 把删除/清理/重建从 `PlatformMemoryUseCase` 拆到 `PlatformMemoryMaintenanceUseCase`：
  读写主链路与低频运维的权限、批量上限、审计口径不同，混在一起会超过 200 行；
- 把审批「待办」与「决策」拆成 `PlatformApprovalUseCase` / `PlatformApprovalDecisionUseCase`：
  决策含写记忆/改执行状态两个副作用，单独测试更容易定位；
- 抽出 `PlatformRunOutputJson`（执行输出解析/序列化）、`PlatformVectorCodec`（向量编解码）、
  `PlatformExecutionRecordWriter`（审批后终态落库）、
  `MemoryPurgeResult` / `MemoryRebuildResult`（结果 record），新增类均 < 200 行；
- 既有 `PlatformExecutionUseCase`（382 行）与 `PlatformMemoryUseCase`（204 行）仍超 200 行，
  属 Week 17/21 遗留；本批次只加 pending 分支，拆分留独立重构批次（不把无关注释放进本周提交）；
- 审批与记忆都用「Port + jdbc/memory 双适配器」，与第 16/21 周模式一致；
- `PlatformRunOutput` / `PlatformMemoryRecord` 加字段时都保留旧构造器，避免 50+ 处既有调用被波及；
- 未做：审批补偿任务、分布式锁、异步重建（见 Spec 第十三节，均为有明确需求再做）。

## 8. 质量门禁

- [x] 编译：`-pl apps/spring-ai-alibaba-agent -am test-compile` 通过（JDK17 参数方式，未改 `JAVA_HOME`）
- [x] 单测：平台 529 全绿；根聚合 777 全绿
- [x] 分层：Controller 只调 UseCase；审批/记忆 SQL 仅在 `infrastructure/persistence`；
      领域模型无 Spring/Web 类型
- [x] 安全：删除谓词带 `tenant_key + user_id`；非 admin 清理/重建 403；审计/日志不含报告正文；
      未新增密钥；`.env.example` 仅占位
- [x] YAGNI：未引入 Quartz/Flowable/Redis 分布式锁/异步任务；调度用 Spring `@Scheduled`
- [x] 数据库脚本：V6 在 H2(MODE=PostgreSQL) 首次应用 + 后续上下文只校验；
      **真实 PostgreSQL 2026-10-25 已执行**（备份 → applied 1 migration → 重启幂等 → 旧行 5 条不变）
- [x] 云端验收：`cloud-accept-week23.sh` **PASS=21 FAIL=0**（P002 1731 tokens、重建 5 条旧向量）
- [x] 文档：Spec / 架构 / API / Postman / 部署手册 / 本日志 / README + 规范 7.3 索引

## 9. 验证证据

### 9.1 H2 首次应用 V6

`target-week23-full.log`：

```text
Migrating schema "public" to version "6 - platform memory lifecycle and approval"
Successfully applied 6 migrations to schema "public", now at version v6 (execution time 00:00.326s)
```

后续 Spring 上下文：

```text
Successfully validated 6 migrations
Current version of schema "public": 6
Schema "public" is up to date. No migration necessary.
```

### 9.2 关键行为

- `PlatformApprovalHttpTest`：P002 → `PENDING_APPROVAL`，output 带 `approvalId`/`approvalStatus=PENDING`；
  批准 → `APPROVED` + `COMPLETED` + `memoryId`，执行记录 output 含 memoryId；
  拒绝 → `REJECTED` 且 `memoryId=null`；重复决策 409；他人 403；非法 status 400。
- `PlatformExecutionObservabilityTest`：待审 span `execution.status=PENDING_APPROVAL`，
  `agent.run.count{status=PENDING_APPROVAL}=1`，duration timer 存在。
- `JdbcPlatformMemoryLifecycleTest`：V6 两列往返；过期行在 list/session/search 三处均不可见；
  `purgeExpired` 只删过期行；删除带 scope；`listStaleEmbeddings` + `updateEmbedding` 后语义检索命中。
- 审计：`APPROVAL_SUBMIT` / `APPROVAL_DECIDE`（SUCCESS/DENIED）/ `MEMORY_DELETE` /
  `MEMORY_PURGE` / `MEMORY_REBUILD` 均已落 `AuditAction` 并更新 `docs/api/week-16-api.md` 事件表。

### 9.3 云端执行（2026-10-25 02:49–02:52）

| 步骤 | 结果 |
|------|------|
| 迁移前巡检 | 平台 v5、`platform_memory` 5 行、无 V6 列/审批表；available≈1.69 GB |
| 备份 | `/opt/ai-code/backup/week23-20261025-024942`（jar 95 MB + `.env` + 21 KB dump + SHA256SUMS） |
| 发布 | `deploy-app.ps1 -Target platform -SkipTests`；远端 `remote-deploy.sh` 自动备份 `app.jar.prev` |
| Flyway | `Successfully applied 1 migration ... now at version v6`（0.034 s）；启动 12.99 s |
| 重启幂等 | 第二次启动 `Successfully validated 6 migrations` + `Schema "public" is up to date`；启动 11.09 s |
| 旧数据 | `platform_memory` 仍 5 行；`expires_at` 全 NULL（不误删）；`embedding_model` 全 NULL（待重建） |
| 向量重建 | admin 触发后 5 行全部 → `openai:qwen3.7-text-embedding-flash:1024` |
| HITL 验收 | **PASS=21 FAIL=0**：P002 1731 tokens → `PENDING_APPROVAL` → 批准 `COMPLETED` + 记忆 → 重复 409 → 删除 → operator 403 / admin 200 |
| 审计 | `APPROVAL_SUBMIT=1`、`APPROVAL_DECIDE=1`、`MEMORY_DELETE=1`、`MEMORY_PURGE=1`、`MEMORY_REBUILD=1` |
| 资源 | available≈1.71 GB、swap 0 MB、disk 42% |

证据：`target-week23-cloud-accept.log`、`target-week23-cloud-precheck-after3.log`、`target-week23-deploy.log`。

### 9.4 环境插曲（非代码）

本会话开始时 Maven 对所有模块 `target/` 写入报「拒绝访问」（连 `Set-Content` 都失败），
根因是工作区子目录的 Windows 文件权限缺少当前用户完全控制（DSH 工作区授权未继承到子目录）。
按 `diagnose-windows-sandbox-acl` 技能对 `apps` 目录做了一次诊断/修复（备份 + 可回滚），
随后根聚合与全模块构建恢复正常。**仓库内容未因此改动**；该问题与第 23 周业务代码无关。

## 10. 风险与下周输入

1. **回滚演练未做**：备份与 `remote-deploy.sh --rollback` / 库回滚 SQL 已就绪，但未实际停机演练；
   下次变更窗口建议先做一次备份恢复验证；
2. **决策非单事务**：`markDecided → 写记忆 → 改执行状态` 三步；记忆失败只 WARN，
   执行状态写失败会留 `APPROVED + PENDING_APPROVAL` 组合。当前靠日志（approvalId/executionId）人工修；
3. **TTL 旧数据**：迁移前 5 条旧记忆 `expires_at=NULL`（永不过期），符合「不误删」设计；
   新建记忆才写 TTL，若要让旧数据也纳入 TTL，需要一次性回填策略（未做）；
4. **内存余量**：云端 available≈1.71 GB；本周没有新增常驻进程，发布后已复核；
5. **批次 2 已确认 A1（运维令牌）+ B2（按租户复制角色）**，按第 13 节进入实现；SSO 409/503 真机随批次 2 补。

## 11. ADR（本批次）

| 决策 | 选项 | 选择 | 理由 |
|------|------|------|------|
| 暂停实现 | Graph interrupt / 平台状态机 | 平台状态机 + `platform_approval` | 跨进程、可重启；Week 10 `MemorySaver` 重启丢 |
| 暂停点 | 模型前 / 工具前 / 报告后 | 报告后、写记忆前 | 审核人看得到报告；「进随访」= 写记忆 |
| 审批存储 | 只存执行记录 / 独立表 | 独立表 | 待办要有索引与状态；执行记录不是任务队列 |
| 报告存放 | 审批表冗余 / 单份 | 执行记录单份 | 避免两份报告不一致 |
| 决策并发 | 先查后改 / 原子更新 | `UPDATE ... WHERE status='PENDING'` | 抢输方 409，杜绝重复写记忆 |
| TTL | 只定时删 / 到期不可见 + 清理 | 两者结合 | 读取立即生效；清理控制膨胀 |
| 向量版本 | 不记录 / `embedding_model` | 记录 `provider:model:dimension` | 没有标记无法判断哪些向量需重建 |
| 重建方式 | 自动 / 管理端点 | 管理端点，100 条/批 | 换模型是低频运维，自动重建会打爆配额 |
| 清理调度 | Quartz / Spring `@Scheduled` | `@Scheduled` + 可关 | 单实例、低负载；不引入调度框架 |

## 12. 交付物

- `docs/specs/week-23.md`、`docs/architecture/week-23-architecture.md`、`docs/api/week-23-api.md`
- `docs/deploy/week-23-migration.md`、`docs/postman/week-23.postman_collection.json`
- `deploy/app/cloud-accept-week23.sh`（云端实跑 **PASS=21 FAIL=0**）
- 代码：V6 + 2 个 Port/适配器对 + 3 个 UseCase + 1 个 Controller + DTO + 调度/配置
- 配置：`application.yml`（4 个 lifecycle 键）、`.env.example`（4 个可选键）、
  `application-test.yml`（测试关清理）
- 测试：10 个测试类/增补，共 51 例

## 13. 批次 2：产品决策已确认（2026-10-25）

> 用户已确认：**A1 运维令牌**、**B2 按租户复制角色**。
> 详细选项见 `docs/specs/week-23.md` 第十节；批次 2 实现按这两个选择落地。

### 决策 A 实现（A1）

- `ENTERPRISE_ADMIN_TOKEN` → `enterprise.admin.token`；未配置时端点 403（fail closed）；
- `AdminTokenGuard` 常量时间比较，日志不打印令牌；
- `/api/v1/admin/sso-links`：GET 列表 / POST 建链或重建 / DELETE 解绑；
- `deleteLocalUser=false` 只解绑（下次登录 409 属预期）；`true` 解绑并删本地用户（可重新开户）；
- 不走 Sa-Token：`SaTokenConfig` 排除 `/api/v1/admin/**`；
- 本地用户被业务数据引用时删除失败 → 409，提示只解绑，不级联删业务数据。

### 决策 B 实现（B2）

- V7 重建角色四表：`(tenant_key, role_key[, value])`，旧全局角色/授权复制给每个租户；
- `PlatformRolePort.findByKey(tenantKey, roleKey)`；兼容入口只读 default；
- `PlatformPermissionChecker` 按用户 tenant 取角色，旧库缺失回退 default；
- seed 逐 `(tenant, role)` 判空补缺（default + hospital-b），不覆盖已有角色/授权；
- `execution_record.tenant_key`（V7 加列 + 按 user 回填），`ExecutionRecord` / 响应带 `tenantKey`；
- 平台当前无“删除用户”API，删用户级联**暂缓**（强删会撞执行/审批外键）。

### 批次 2 测试

| 测试类 | 例数 |
|--------|------|
| `SsoAdminUseCaseTest` | 10 |
| `SsoAdminControllerTest` | 4 |
| `JdbcSsoUserLinkAdapterTest`（增补） | +2 |
| `SsoLoginUseCaseTest`（增补） | +1 |
| `JdbcPlatformRoleAdapterTest`（增补） | +2 |
| `JdbcExecutionRecordAdapterTest`（增补） | +1 |
| `PlatformExecutionUseCaseTest`（增补） | +1 |
| `PlatformSeedIdempotencyTest`（增补断言） | 0 |

### 批次 2 结果

- enterprise：37 → **55**（+18），全绿；
- platform：529 → **532**（+3），全绿；
- 根聚合：777 → **798**（90 + 55 + 21 + 532 + 100），`BUILD SUCCESS`；
- V7 在 H2(MODE=PostgreSQL) 首次应用（`Successfully applied 7 migrations ... now at version v7`）；
- **云端已执行**（2026-10-25 06:29–06:35）：
  - 备份 `/opt/ai-code/backup/week23-b2-20261025-062917`
  - 平台 V7：`Successfully applied 1 migration ... now at version v7`；default/hospital-b 各 3 角色；
    执行记录 13 条 tenant 与 user 不一致数 = 0
  - enterprise 发布 + `ENTERPRISE_ADMIN_TOKEN` 64 位写入 `.env`（不打印）
  - 批次 2 验收 **PASS=19 FAIL=0**：令牌 403/200、角色租户、执行 tenant、
    建链/解绑删本地用户、**409 同名未建链**、**503 平台不可达**、恢复后 200
  - V7 后回归批次 1：`cloud-accept-week23.sh` 再跑 **PASS=21 FAIL=0**
    （P002 1439 tokens、重建 scanned=0，说明 5 条旧向量已在批次 1 验收时重建）
- 回滚演练未执行（备份 + `pg_restore` 步骤已写）

### 批次 2 已知边界

| # | 边界 | 处理 |
|---|------|------|
| 1 | 平台删用户级联 | 平台无删用户 API；先走 enterprise 运维解绑，待用户生命周期产品语义 |
| 2 | `deleteLocalUser=false` 后 409 | 有意保留（同名可能是两个人）；文档明确要求运维选 `true` 才能重新开户 |
| 3 | 旧执行记录 tenant 回填 | V7 按 `user_id` 回填；用户被删/异常的记录保持 default |
| 4 | 跨租户 admin 视野 | admin 仍可看全部租户；B2 只隔离角色副本，不改变 admin 的平台级语义 |

## 14. 上线后回归验收（2026-10-25 08:15–08:21）

用户要求复跑一遍验证；以下是本次实测结果，不是引用历史记录。

### 14.1 本地

```text
command: powershell -NoProfile -ExecutionPolicy Bypass -File .\run-maven-jdk17.ps1 test
result:  Tests run: 798, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS
detail:  ai-core 90 / enterprise 55 / patient 21 / platform 532 / gateway 100
H2:      Successfully applied 7 migrations ... now at version v7
```

日志：`target-week23-verify-local.log`。

### 14.2 云端只读巡检

- `cloud-week23-precheck.sh`：平台/网关/enterprise active，health 200；Flyway v1–v7 全 success；
  `platform_approval` 存在；记忆 5 条、向量来源统一 `openai:qwen3.7-text-embedding-flash:1024`
- `cloud-week23-b2-check.sh`：default 3 角色 / hospital-b 3 角色；operator 授权两租户各 3 项；
  执行记录 14 条全部 default，tenant 与 user 不一致数 **0**

日志：`target-week23-verify-precheck.log`、`target-week23-verify-b2check.log`。

### 14.3 云端功能验收

| 脚本 | 结果 | 关键数据 |
|------|------|----------|
| `cloud-accept-week23.sh` | **PASS=21 FAIL=0** | P002 **1807 tokens**；待审不写记忆；批准写记忆 `52fff16e…`；重复 409；删除成功；重建 scanned=0 |
| `cloud-accept-week23-b2.sh` | **PASS=19 FAIL=0** | 无/错令牌 403、有令牌 200；hospital-b 角色；tenantKey=default；探针建链/解绑删本地用户；**409**；**503**；恢复后 200 |

日志：`target-week23-verify-hitl.log`、`target-week23-verify-b2accept.log`。

### 14.4 收尾状态

503 用例停/启平台后已恢复：`ai-code-platform active`、`ai-code-enterprise active`、`health=200`。
验收产生的 P002 记忆已由脚本删除；新增 1 条已批准审批记录留作审计样本。

