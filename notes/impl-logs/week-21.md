# Week 21 实现日志 — 医疗 SaaS 起步（租户 / 统一身份 SSO / 患者分析 Agent / 记忆隔离）

- 日期：2026-10-24
- 批次：1
- 对应 Spec：`docs/specs/week-21.md`
- 对应阅读：`notes/week-21-saas-multitenancy.md`
- 架构：`docs/architecture/week-21-architecture.md`｜接口：`docs/api/week-21-api.md`

## 1. 本周目标

第 20 周把可观测性收口，本周进入第 6 阶段实战第一刀，**只做能让后面三个 Agent 复用**的地基：

1. 多租户：`platform_tenant` + `platform_user.tenant_key`（`default` / `hospital-b`）；
2. 统一身份：平台新增 `POST /api/v1/platform/auth/verify`（只校验、不建会话）作为唯一身份源，
   enterprise 用 `sso.enabled` 开关式委托，`sso_user_link` 首次登录自动开户；
3. Agent 1：平台一等 Agent `patient-analysis`（查患者 → 查检查报告 → 查健康指标 → 规则风险 → 报告 → 写记忆）；
4. 长期记忆：`platform_memory` 按 `tenant_key + user_id` 隔离，隔离谓词写死在 SQL 里；
5. patient-agent（**8083**）标注 deprecated（三个响应头 + 启动告警，业务零改动）。

## 2. 边界

- Always：TDD（先 RED）；Week 4/13–20 契约零回退；迁移双库可跑；scope 只能服务端推导。
- Never：做 Agent 2/3/4；合并 `app_user` 与 `platform_user`；`CREATE EXTENSION vector`；
  删除 patient-agent；把租户/用户放进请求参数。

## 3. 增量架构

见 `docs/architecture/week-21-architecture.md`：
新增 Port 3 个（`PlatformTenantPort` / `PlatformMemoryPort` / `PlatformRunScopePort`）、
enterprise Port 2 个（`PlatformIdentityPort` / `SsoUserLinkPort`）、
UseCase 3 个（`PlatformTenantUseCase` / `PlatformMemoryUseCase` / `PatientAnalysisUseCase` + `SsoLoginUseCase` / `LoginFacade`）、
适配器 6 个、迁移 3 个（V4/V5/V7）。

## 4. 新增类型清单

| 类型 | 名称 | 职责 |
|------|------|------|
| 模型 | `PlatformTenant` / `TenantStatus` | 租户与状态（ACTIVE/SUSPENDED） |
| 模型 | `PlatformMemoryScope` / `PlatformMemoryRecord` / `PlatformMemoryHit` / `PlatformMemoryVector` | 记忆范围与记录（scope 必填） |
| 模型 | `ReportSummary` / `PatientAnalysisStep` / `PatientAnalysisResult` | Agent 1 结构化结果 |
| 枚举 | `AgentType.PATIENT_ANALYSIS` | 平台一等 Agent 类型 |
| Port | `PlatformTenantPort` | 租户读写 + `isActive`（未知租户视为不可用） |
| Port | `PlatformMemoryPort` | 写入/按范围查询/语义检索，**无无参查询入口** |
| Port | `PlatformRunScopePort` | 当前 Run 的租户+用户（无则空） |
| Port | `PlatformIdentityPort` / `SsoUserLinkPort`（enterprise） | 委托校验 / 映射表 |
| UseCase | `PlatformTenantUseCase` | 列表（仅 admin）+ 单查 |
| UseCase | `PlatformMemoryUseCase` | scope 推导、向量化、阈值排序、admin 换 scope |
| UseCase | `PatientAnalysisUseCase` | 入参校验 + 写记忆（失败不阻塞） |
| UseCase | `SsoLoginUseCase` / `LoginFacade`（enterprise） | SSO 登录 + 开关路由 |
| 领域服务 | `PatientAnalysisAgent` | 工具 → 规则 → LLM（降级）→ 结果 |
| 领域服务 | `PlatformMemoryRanker` / `PatientAnalysisReportComposer` | 余弦排序 / 降级报告（纯函数） |
| 适配器 | `JdbcPlatformTenantAdapter` / `InMemoryPlatformTenantAdapter` | 租户落库 / 进程内 |
| 适配器 | `JdbcPlatformMemoryAdapter` / `InMemoryPlatformMemoryAdapter` | 记忆落库（SQL 谓词）/ 进程内 |
| 适配器 | `SecurityContextRunScopeAdapter` | 运行上下文 → scope |
| 适配器 | `HttpPlatformIdentityAdapter` / `JdbcSsoUserLinkAdapter`（enterprise） | 平台校验客户端 / 映射表落库 |
| 工具 | `ReportLookupTool` | 检查报告（演示数据，确定性） |
| Filter | `DeprecatedApiFilter`（patient-agent） | 三个弃用响应头 |
| 配置 | `PlatformMemoryProperties` / `SsoProperties` | `platform.memory.*` / `sso.*` |
| 迁移 | `V4__platform_tenant.sql` / `V5__platform_memory.sql` / `V7__sso_user_link.sql` | 双库可跑 |
| Test | 14 个平台测试类 + 4 个 enterprise + 1 个 patient | 见第 6 节 |

## 5. RED

先写测试再写实现，首次 `test-compile` 失败（缺类，节选真实输出）：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-maven-jdk17.ps1 -q test-compile
```

```text
[ERROR] .../SsoLoginUseCaseTest.java:[11,41] 错误: 找不到符号
[ERROR]   符号:   类 PlatformIdentity
[ERROR]   位置: 程序包 com.aicode.enterprise.domain.model
[ERROR] .../SsoLoginUseCaseTest.java:[13,47] 错误: 找不到符号
[ERROR]   符号:   类 SsoProperties
[ERROR] .../LoginFacadeTest.java:[52,46] 错误: 找不到符号
[ERROR]   符号:   类 SsoLoginUseCase
[ERROR] .../InMemorySsoUserLinkAdapter.java:[3,40] 错误: 找不到符号
[ERROR]   符号:   类 SsoUserLinkPort
[ERROR] .../JdbcSsoUserLinkAdapterTest.java:[3,40] 错误: 找不到符号
[ERROR] ...BUILD FAILURE
```

- 平台侧 RED 同理：`PlatformTenant` / `PlatformMemoryScope` / `PlatformMemoryRanker` / `PatientAnalysisAgent`
  / `ReportLookupTool` / `DeprecatedApiFilter` 等类型均不存在；
- 记录的是**编译失败**而非断言失败——与第 20 周同口径：先让测试引用尚不存在的契约，再补实现。

## 6. GREEN

### 6.1 平台本周用例（59 例）

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-maven-jdk17.ps1 -pl apps/spring-ai-alibaba-agent -am test `
  "-Dtest=PlatformTenantUseCaseTest,PlatformMemoryRankerTest,PlatformMemoryUseCaseTest,PlatformMemoryPropertiesTest,JdbcPlatformMemoryAdapterTest,JdbcPlatformTenantAdapterTest,PatientAnalysisReportComposerTest,PatientAnalysisAgentTest,PatientAnalysisUseCaseTest,ReportLookupToolTest,PlatformAgentRunnerPatientAnalysisTest,PlatformAuthTenantTest,SecurityContextRunScopeAdapterTest,PlatformTenantMemoryHttpTest" `
  "-Dsurefire.failIfNoSpecifiedTests=false"
```

```text
Tests run: 4,  ... PatientAnalysisUseCaseTest
Tests run: 3,  ... PatientAnalysisAgentTest
Tests run: 2,  ... PatientAnalysisReportComposerTest
Tests run: 3,  ... ReportLookupToolTest
Tests run: 4,  ... PlatformAuthTenantTest
Tests run: 10, ... PlatformMemoryUseCaseTest
Tests run: 7,  ... PlatformTenantUseCaseTest
Tests run: 5,  ... PlatformTenantMemoryHttpTest
Tests run: 5,  ... PlatformMemoryRankerTest
Tests run: 5,  ... JdbcPlatformMemoryAdapterTest
Tests run: 4,  ... JdbcPlatformTenantAdapterTest
Tests run: 2,  ... PlatformAgentRunnerPatientAnalysisTest
Tests run: 3,  ... PlatformMemoryPropertiesTest
Tests run: 2,  ... SecurityContextRunScopeAdapterTest
Tests run: 59, Failures: 0, Errors: 0, Skipped: 0
```

真机验收后又补了 9 例（都是被真机缺陷倒逼的，见 9.2 与 10.2）：

```text
PlatformSeedInitializerTest                          4 例（租户/第二租户用户 seed 的逐 key 判空）
PlatformRbacPermissionUpgradeInitializerTest         3 例（operator 权限增量升级，不覆盖手工撤销）
PlatformTenantMemoryHttpTest（新增 2 例）             2 例（/auth/verify 免登录 + 错误密码 401）
```

> 中途红过一次（真实失败，非编译失败）：
> 1. `PlatformMemoryRankerTest.ordersByCosineSimilarityDescending` —— 排序函数会丢弃余弦 `<=0` 的候选，
>    而用例把正交向量写进了期望列表。**是用例错、实现对**（无关项不应出现在检索结果里），改用两条正相关候选；
> 2. `JdbcPlatformMemoryAdapterTest.listsOnlyRecordsOfRequestedScope` —— 与 `PlatformTenantMemoryHttpTest`
>    同 JVM 跑时列表多出一条：两类测试共用同一个 H2 内存库，HTTP 测试往 `(default, 2)` 写了记忆。
>    改用本类专属租户/用户（`jdbc-mem-*` / 91xx）——这正是第 16 周「单测全绿但换顺序就红」的同类问题。

### 6.2 enterprise 本周用例（16 例）

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-maven-jdk17.ps1 -pl apps/enterprise-knowledge-agent -am test `
  "-Dtest=SsoLoginUseCaseTest,LoginFacadeTest,HttpPlatformIdentityAdapterTest,JdbcSsoUserLinkAdapterTest" `
  "-Dsurefire.failIfNoSpecifiedTests=false"
```

```text
Tests run: 2,  ... LoginFacadeTest
Tests run: 6,  ... SsoLoginUseCaseTest
Tests run: 3,  ... JdbcSsoUserLinkAdapterTest
Tests run: 5,  ... HttpPlatformIdentityAdapterTest
Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
```

### 6.3 patient-agent 本周用例（2 例）

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-maven-jdk17.ps1 -pl apps/patient-agent -am test `
  "-Dtest=DeprecatedApiFilterTest" "-Dsurefire.failIfNoSpecifiedTests=false"
# => Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
```

### 6.4 根聚合

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-maven-jdk17.ps1 test
```

```text
ai-core 90 / enterprise 37 / patient 21 / platform 453 / gateway 100
=> 合计 701，Failures 0，Errors 0，BUILD SUCCESS
```

（Week 20 基线 615 → 本周 701，新增 86；platform 385 → 453，enterprise 21 → 37，patient 19 → 21，gateway 不变。）

## 7. 重构

- `PlatformMemoryRanker` 从两个适配器里抽出来：内存与 JDBC 实现共用同一份「余弦 → 阈值 → topK」，
  避免两套排序逻辑漂移；单测只测这一份；
- `PlatformAgentRunner` 保留三参构造器（第 8–20 周用例零改动），新增五参构造器并显式 `@Autowired`
  （第 19/20 周两次因「多构造器无 @Autowired」在真机启动失败，这次不等真机暴露）；
- `PlatformAuthUseCase` / `PlatformPermissionChecker` 同样用「旧构造器委托 + 新构造器 @Autowired」演进，
  既有 82 个平台测试类一行未改（除种子断言随需求更新）；
- `PlatformMemoryController` 曾为回显 tenantKey 重复查用户，改为 `resolveScope` 公开给控制器，
  一次解析、一次查询（去掉控制器对 `PlatformUserPort` 的依赖）；
- `PatientAnalysisReportComposer` 做成纯函数并在报告首尾都写明「规则模板 / 模型不可用」，
  避免降级文本被误当成模型输出。

## 8. 质量门禁

- [x] 编译：根聚合 `test-compile` 通过（JDK17 + `-Djdk.17.home`，未改 `JAVA_HOME`）
- [x] 单测：本周新增 86 例全绿；根聚合 701（见 9.5）
- [x] 分层：`PlatformMemoryUseCase` 只依赖 Port；`PatientAnalysisAgent` 不接触 `HttpServletRequest`/租户；
      `Service` 层无 SQL（SQL 只在 `Jdbc*Adapter`）
- [x] 安全：无密钥入库；SSO 不打印密码/令牌；记忆响应不含 embedding；越权一律 403
- [x] YAGNI：未引入 pgvector、消息队列、注册中心、K8s
- [x] 数据库脚本：双库可跑（H2 单测已跑 V4/V5/V7；PostgreSQL 见 9.2）
- [x] 文档：新增 public 类型均有中文 JavaDoc
- [x] 日志：本文档（RED/GREEN 证据均为真实执行输出）

## 9. 验证证据

### 9.1 H2（MODE=PostgreSQL）迁移与 seed

平台全部 `@SpringBootTest` 用例启动时执行同一套 Flyway 脚本：

```text
Migrating schema "PUBLIC" to version "4 - platform tenant"
Migrating schema "PUBLIC" to version "5 - platform memory"
Successfully applied 5 migrations
[platform] tenant seed applied tenants=2
[platform] rbac seed applied roles=3 users=4
```

enterprise 用例：

```text
Migrating schema "PUBLIC" to version "7 - sso user link"
Successfully applied 7 migrations
```

幂等性：`PlatformSeedIdempotencyTest` 连续执行两次 seed，用户数/角色数/Agent 数/工具数均不变。

### 9.2 PostgreSQL（真库）

环境：腾讯云 PostgreSQL 16（`aidemo` 库，经 `ssh -L 5432` 隧道），应用本地以 `jdbc` profile 启动
（`LANGFUSE_ENABLED=false` / `OBSERVABILITY_ENABLED=false` / `MONITORING_ENABLED=false`，只验证落库与业务）。

首次启动（V4/V5 从未执行过）：

```text
Successfully validated 5 migrations (execution time 00:00.666s)
Migrating schema "public" to version "4 - platform tenant"
Migrating schema "public" to version "5 - platform memory"
Successfully applied 2 migrations to schema "public", now at version v5
Started SpringAiAlibabaAgentApplication in 21.595 seconds
[platform] tenant seed applied created=2
```

第二次启动（幂等）：

```text
Successfully validated 5 migrations
Started SpringAiAlibabaAgentApplication in 15.883 seconds
（无 Migrating 行、无 seed 行 —— 迁移只校验、seed 只补缺）
```

历史表（`flyway_schema_history_platform`，逐版本无跳过）：

```text
v1 | platform rbac | success=true
v2 | platform execution record | success=true
v3 | platform audit record | success=true
v4 | platform tenant | success=true
v5 | platform memory | success=true
```

> 说明：本库没有 `v0 BASELINE` 行——该历史表是第 16 周首次建库（当时 public 为空）时创建的，
> baseline 逻辑不会再插入。规范 3.7.4 关心的是「有没有被跳过的版本」，此处 v1–v5 全部真实执行。

行数变化（同一库、同一批操作）：

| 表 | 迁移前 | 迁移后 | 说明 |
|----|--------|--------|------|
| `platform_tenant` | — | 2 | default / hospital-b |
| `platform_user` | 3 | **4** | 新增 `doctor_b`（真机暴露 seed 粒度问题后修复，见 10.2 #7） |
| `platform_memory` | — | 3 | 3 次患者分析各写一条 |
| `execution_record` | 7 | 10 | 3 次 `patient-analysis` 执行 |

### 9.3 真机端到端：Agent 1（真实 DeepSeek + 真实 embedding）

```text
POST /api/v1/platform/agents/patient-analysis/runs  {"input":{"patientId":"P003","task":"评估患者血糖控制情况并给出随访建议"}}
=> status=COMPLETED  agentType=PATIENT_ANALYSIS  model=deepseek-v4-pro
   usage={promptTokens:..., completionTokens:..., totalTokens:1587}  elapsed≈32s
   output: patient=张三/P003 age=62 reports=2 risk=MEDIUM degraded=false
           steps=patient_lookup -> report_lookup -> metric_lookup -> risk_analysis -> report_generation
           memoryId=b2a7d383-1fbd-4fa8-a64f-ba7352b3ab9d
   报告正文（模型生成，节选）：风险摘要 / 异常指标解读 / 检查报告要点 / 随访建议 四段
```

记忆与隔离（同一台真机、真实 SQL）：

```text
GET /memories?patientId=P001（operator，default 租户） => 1 条
GET /memories?limit=20（operator）                   => 2 条
GET /memories?patientId=P001（doctor_b，hospital-b）  => 0 条   ← 跨租户不可见
GET /memories?userId=2（admin）                      => 2 条   ← 管理员可换 scope
GET /memories?query=血糖控制不好怎么办                => 1 条，score=0.6703  ← 真 embedding 语义命中
响应 item 字段：memoryId/patientId/task/answer/score/createdAt，**无 embedding**
```

`/auth/verify`（SSO 身份源）：

```text
POST /api/v1/platform/auth/verify {"username":"operator","password":"operator123"}
=> 200 {"code":"SUCCESS","data":{"userId":2,"username":"operator","roleKey":"operator","tenantKey":"default"}}
   无 token 字段；未登录可直接调用（白名单）
POST /auth/verify {"password":"wrong"} => 401 UNAUTHORIZED
```

### 9.4 关键行为证据（本地/H2 用例，补充真机无法覆盖的边界）

| 行为 | 证据（用例） |
|------|--------------|
| 停用租户登录/校验都 401，且审计不泄露密码 | `PlatformAuthTenantTest.suspendedTenantCannotVerifyNorLogin` |
| `/verify` 不建会话、审计带 `sso=true` | `PlatformAuthTenantTest.verifyReturnsIdentityWithTenantWithoutOpeningSession` |
| 同租户不同用户互不可见 | `PlatformMemoryUseCaseTest.isolatesMemoriesBetweenUsersOfSameTenant` |
| 同用户不同租户互不可见 | `PlatformMemoryUseCaseTest.isolatesMemoriesBetweenTenantsForSamePatientId` |
| 隔离在 SQL 谓词里（H2 真实查询） | `JdbcPlatformMemoryAdapterTest.listsOnlyRecordsOfRequestedScope` |
| 非管理员借 `userId` 读他人 → 403 | `PlatformMemoryUseCaseTest` + `PlatformTenantMemoryHttpTest.nonAdminCannotReadAnotherUsersMemories` |
| 记忆响应不含 embedding | `PlatformTenantMemoryHttpTest.memoriesAreIsolatedByTenantAndUser` |
| 模型失败 → `degraded=true` 且执行 COMPLETED | `PatientAnalysisAgentTest.degradesToRuleTemplateWhenModelFails` |
| 工具失败不吞错 | `PatientAnalysisAgentTest.propagatesToolFailureInsteadOfSwallowingIt` |
| 记忆写失败不影响报告 | `PatientAnalysisUseCaseTest.memoryFailureDoesNotFailTheRun` |
| SSO 首次开户写随机密码（本地登不进去） | `SsoLoginUseCaseTest.firstLoginProvisionsLocalUserAndLinkThenIssuesLocalToken` |
| 同名未建链 → 409（不自动合并） | `SsoLoginUseCaseTest.conflictWhenLocalUsernameExistsWithoutLink` |
| 平台不可达 → 503 而非 401 | `HttpPlatformIdentityAdapterTest` + `GlobalExceptionHandler` |
| 8083 三个弃用头且保留业务状态码 | `DeprecatedApiFilterTest` |

### 9.5 根聚合测试

```text
ai-core 90 / enterprise 37 / patient 21 / platform 453 / gateway 100
=> 合计 701，Failures 0，Errors 0，BUILD SUCCESS
```

### 9.6 未执行的真机项（如实登记）

**enterprise（8081）没有连真平台跑 SSO 端到端**，原因不是时间不够，而是环境本身不允许：

1. `apps/enterprise-knowledge-agent/.env` 的 `SPRING_DATASOURCE_URL` / `SPRING_REDIS_HOST` 仍指向
   **`192.168.132.128`**——那是 2026-10-18 已下线的 VMware 节点（见 `docs/deploy/week-19-cloud-env.md`），
   第 19 周的环境批次只迁了中间件与平台侧配置，enterprise 的 `.env` 没跟着更新；
2. 云上 `aidemo` 库里**只有 platform 的表**（`app_user` 与 `flyway_schema_history` 都不存在），
   说明 enterprise 从未在这个库上跑过。若直接启动，enterprise 的 Flyway 是默认历史表 +
   `baseline-version` 默认 1：在非空 schema 上会把 **V1 记成已应用而跳过**，
   随后 V2 的外键找不到 `app_user` → 迁移失败并留下失败记录（规范 3.7.2 明确警告过这个形态）。

因此本周的 SSO 验证边界是：

| 环节 | 验证方式 |
|------|----------|
| 平台 `/auth/verify`（真实进程 + 真实 PG） | 真机调用：200 + 身份含 tenantKey，无 token；错误密码 401 |
| enterprise → 平台的 HTTP 契约（报文/状态码/信封） | `HttpPlatformIdentityAdapterTest`（真实 `HttpServer` socket 桩，5 例） |
| 建链 / 自动开户 / 409 / 503 / 关闭回退 | `SsoLoginUseCaseTest`（6 例）+ `LoginFacadeTest`（2 例） |
| 映射表 SQL（H2 + Flyway V7） | `JdbcSsoUserLinkAdapterTest`（3 例） |

第 22 周补 enterprise `.env`（指向云上库 + 为它单独准备 `aidemo_enterprise` 库并显式设
`flyway.table` / `baseline-version: 0`）后，再做一次真正的跨进程 SSO 验收。

## 10. 风险与下周输入

### 10.1 风险

- **SSO 是新增网络依赖**：enterprise 每次登录多一次 HTTP 调用（默认 3s 超时）。平台慢会放大登录延迟；
  已用 503 明确区分「身份源故障」与「凭据错误」，但**没有熔断**——如果平台持续不可用，登录会持续等 3s；
- **映射表没有「解绑」接口**：平台侧删了用户，enterprise 的 link 仍在，下次登录会因平台 401 而失败，
  但本地用户与历史数据保留。需要运维手工清理（第 22 周补管理接口）；
- **记忆是 append-only**：没有删除/过期策略，`platform_memory` 会随时间增长（演示环境可接受）；
- **单用户记忆候选集上限 500**：超过后只检索最近 500 条，更早的记忆查不到（Spec 已登记为换 pgvector 的触发条件）；
- **`hospital-b` 用户与 `default` 用户共用角色表**：角色是全局的，租户目前只隔离「身份与记忆」，
  不隔离「角色定义」。真要按租户配角色，需要 `platform_role.tenant_key`（第 22 周评估）；
- **换 embedding 模型会让旧向量不可比**：本周 `platform_memory` 里同时存在「向量为空」（配置未映射时写入的 2 条）
  和「1024 维真向量」。生产换模型必须重建或丢弃旧记忆，本周未做重建工具——这是下一步要补的运维能力。

### 10.2 真机暴露并修复的缺陷

前 5 条是本地单测/编译阶段暴露的，后 3 条**只在真实 PostgreSQL + 真实 LLM 环境暴露**：

| # | 现象 | 根因 | 修法 |
|---|------|------|------|
| 1 | 记忆检索用例把「正交向量」算进结果，断言失败 | 排序函数主动丢弃余弦 `<=0` 的无关项，用例期望与实现不一致 | 判定为**用例错**：无关项不该进结果；改用两条正相关候选，并单加一条「丢弃非正相关」用例 |
| 2 | `JdbcPlatformMemoryAdapterTest` 与 HTTP 用例同 JVM 跑时多出一条记录 | 所有 `@SpringBootTest` 共用同一个 H2 内存库，HTTP 用例往 `(default,2)` 写了记忆 | 落库用例改用专属租户/用户（`jdbc-mem-*` / 91xx），不再依赖「库里恰好只有我的数据」 |
| 3 | `PlatformMemoryUseCase.resolveScope` 编译失败 | lambda 里引用了会被重新赋值的局部变量 | 改为 `final` 局部变量 + if/else 两分支赋值 |
| 4 | `PatientAnalysisUseCaseTest` 编译失败 | 测试里 `EmbeddingProperties` 少传一个参数（6 个分量传了 5 个） | 补参数；顺带确认构造器没有重载，避免「少传也能编」的假安全感 |
| 5 | `FakeToolPort` 无法被测试内部类继承 | 测试桩类声明为 `final` | 去掉 `final`（可用性 > 无意义的不可变） |
| 6 | `/api/v1/platform/auth/verify` 真机调用返回 **401**（凭据是对的） | 新端点没加进 Sa-Token 白名单：`SaRouter.match("/platform/**")` 要求登录，verify 本身就是「用来拿身份」的 | 加 `.notMatch("/api/v1/platform/auth/verify")`；并补 2 个**走完整拦截器链**的 HTTP 用例（原先只有单元测试，覆盖不到拦截器） |
| 7 | 真机上 operator 跑患者分析 **403**；`platform_user` 只有 3 行（没有 `doctor_b`） | 两个 seed 都以「表是否为空」为整体开关：老库已有用户/角色 → 本版本新增的用户与授权永远补不上 | ① 租户/用户 seed 改为**逐 key/id 判空**（且不覆盖运维状态）；② 新增 `PlatformRbacPermissionUpgradeInitializer` 做**权限并集升级**（只补本版本新增的，不恢复被撤销的） |
| 8 | 记忆落库成功但向量为空，语义检索永远 0 命中 | 平台 `application.yml` 只映射了 `embedding.provider`，没映射 `api-key/base-url/model`，`.env` 里的 `EMBEDDING_API_KEY` 存在却读不到 | 补全 embedding 配置映射；修复后同一查询命中 `score=0.6703` |

> #6/#7 的共同点与第 19–20 周的教训一致：**真机暴露的往往不是业务逻辑，而是「装配与初始化」**——
> 拦截器白名单、种子数据开关、配置项映射，这三类东西单测默认都不经过。


### 10.3 下周输入（第 22 周）

- 长期记忆的**过期/删除**策略（按租户 TTL 或按条数上限）+ 管理接口；
- **记忆向量重建工具**（换 embedding 模型后旧向量不可比，需要批量重算或丢弃）；
- **enterprise `.env` 环境迁移 + 独立库**：现指向已下线的 `192.168.132.128`；
  需为它准备独立库（或在共用库上显式 `flyway.table` + `baseline-version: 0`），再补一次跨进程 SSO 真机验收；
- `sso_user_link` 的**解绑/重建**接口，以及平台侧删除用户时的级联处理；
- 患者分析接 **HITL**（HIGH 风险报告需人工确认后才能进随访流程）；
- 检查报告数据源从演示数据换成真实 HIS/LIS 查询（`ReportLookupTool` 契约不变）；
- 若引入 Agent 2（运营分析），先确认租户级指标隔离（当前指标只按 `application` 打标签，没有 tenant 维度，
  加 tenant 标签会带来基数问题，需要先评估）。

## 11. 交付物清单

- `docs/specs/week-21.md`、`docs/architecture/week-21-architecture.md`、`docs/api/week-21-api.md`
- `docs/postman/week-21.postman_collection.json`
- `notes/week-21-saas-multitenancy.md`、本日志
- 迁移：`V4__platform_tenant.sql`、`V5__platform_memory.sql`（平台）、`V7__sso_user_link.sql`（enterprise）
- 代码：平台 20 个新类 + 8 处改造；enterprise 8 个新类 + 4 处改造；patient-agent 2 个新类
- 配置：`application.yml`（`platform.memory.*`、`embedding.*`、Sa-Token 白名单）、enterprise `application.yml` + `.env.example`（`sso.*`）

## 12. 决策记录 ADR

| 决策 | 选项 | 选择 | 理由 |
|------|------|------|------|
| 身份源 | 双写两套账号 / 平台唯一 | 平台唯一（`/auth/verify`） | 两套密码 = 两套过期策略 + 两套泄露面 |
| verify vs 复用 login | 拿平台 token 再丢弃 / 新增 verify | 新增 verify | 不产生孤儿会话；语义清晰（校验 ≠ 登录） |
| SSO 用户映射 | 共用 `platform_user` / 映射表 | 映射表 `sso_user_link` | 迁移风险、Flyway 历史表冲突、`app_user` 外键依赖 |
| SSO 失败语义 | 一律 401 / 区分 503 | 401 凭据错、503 身份源不可用 | 把「平台挂了」报成「密码错」会误导排查 |
| 自动开户 | 关闭 / 开启（默认） | 开启 + 同名冲突 409 | 新员工零运维；同名不自动合并，避免张冠李戴 |
| 患者分析入口 | 直连 `/multi-agent/runs` / 平台 Agent | 平台 Agent（`PATIENT_ANALYSIS`） | 一次拿到 RBAC/审计/执行记录/指标/span |
| 风险判断 | 让模型判 / 复用规则 | 复用 `PatientRiskAssessor` | 可解释、可单测、不烧 token；模型只写报告 |
| LLM 失败 | 整单失败 / 降级出规则报告 | 降级 + `degraded=true` | 报告可用且来源可辨；不冒充模型输出 |
| 记忆隔离位置 | 用例层过滤 / SQL 谓词 | SQL 谓词 + Port 无无参查询 | 漏一个分支就是跨租户泄露 |
| 记忆向量存储 | pgvector / 文本向量 | 文本向量（Port 隔离） | H2 可测、库级改动最小（Spec 七） |
| 租户状态 | 不做 / 做（ACTIVE/SUSPENDED） | 做，登录时校验 | SaaS 最基本的下线手段 |
| 8083 处理 | 删除 / 弃用标记 | 弃用标记 | 保留第 5–7 周 19 个用例作为回归对照 |
| 端口文档 | 按文档 / 按代码 | 按代码（8081/8083）并改文档 | 端口是事实，文档必须让位于可执行配置 |
