# Spec: Week 21 — 医疗 SaaS 起步：租户 + 统一身份（SSO）+ 患者分析 Agent（Agent 1）+ 记忆隔离

## Objective

第 17–20 周把平台的「可观测性」收口完毕，第 21 周进入第 6 阶段（企业项目实战）：
把 `spring-ai-alibaba-agent`（8084）从「单院演示平台」推进为**医疗 SaaS 的最小可信骨架**，
并落地 README 第 6 阶段的 **Agent 1：患者分析 Agent**。

具体三件事（全部来自 Week 13 遗留清单，见规范 7.4）：

1. **多租户 + 统一身份**：引入 `platform_tenant` 与 `platform_user.tenant_key`；平台新增
   `POST /api/v1/platform/auth/verify` 作为**唯一的身份源**，enterprise（8081）改为「本地登录 or 委托平台登录」
   的 SSO 模式（`sso.enabled`），首次登录按 `sso_user_link` 自动开户；
2. **患者分析 Agent（Agent 1）**：平台新增一等 Agent `patient-analysis`（`AgentType.PATIENT_ANALYSIS`），
   一条链路完成「查患者 → 查检查报告 → 查健康指标 → 风险分析 → 生成报告 → 写长期记忆」，
   全程受 RBAC / Guardrail / 审计 / 执行记录 / 四后端观测约束；
3. **长期记忆按租户 + 用户隔离**：`platform_memory` 落库，读写都在 SQL 层带
   `tenant_key + user_id` 谓词；新增只读查询 API；patient-agent（8083）**标注 deprecated**，
   把 Run 入口统一指向 8084。

**红线不变**：Week 16 的响应信封 / `X-Trace-Id` / 错误码、Week 17 的 OTel span 与指标、
Week 18 的 Langfuse、Week 19 的网关鉴权与 SkyWalking 关联、Week 20 的 Prometheus/Grafana 契约，
全部**零回退**；`sso.enabled=false` 时 enterprise 行为与第 4 周完全一致。

## Tech Stack

| 项 | 本周启用 |
|----|----------|
| JDK 17、Spring Boot 3.4.5、Maven、`libs/ai-core`、Sa-Token、PostgreSQL/H2、Flyway | 复用，契约不改 |
| `spring-boot-starter-jdbc` + `JdbcTemplate`（平台已有） | 复用（新增两个 `Jdbc*Adapter`） |
| `ai-core` 的 `EmbeddingModelPort`（默认 `HashingEmbeddingAdapter`，1024 维，离线确定性） | 本周新增用法（记忆向量化） |
| `ai-core` 的 `VectorMath.cosine`（纯函数） | 复用（不在平台重复实现余弦） |
| JDK `HttpClient` + Jackson（enterprise 侧 SSO 客户端） | 本周新增用法（无新依赖） |
| Spring `OncePerRequestFilter`（8083 弃用标记） | 本周新增用法 |

明确不做：pgvector 建表/索引（见七，ADR 说明）、消息队列、K8s、注册中心、前端、
真实医院 HIS 对接、Agent 2/3/4、Langfuse Prompt 版本迁移、网关新增路由。

## Commands

- 不改 `JAVA_HOME`；JDK17：`-Djdk.17.home=E:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot`
- Maven：`-s D:\soft\maven-3.9.4\conf\settings-ailocal.xml`（本地仓库 `E:\workRepositoryAi`）
- 根聚合测试：`powershell -NoProfile -ExecutionPolicy Bypass -File .\run-maven-jdk17.ps1 test`
- 平台本周用例（16 个类 / 68 例）：

  ```powershell
  ... -pl apps/spring-ai-alibaba-agent -am test `
    "-Dtest=PlatformTenantUseCaseTest,PlatformMemoryRankerTest,PlatformMemoryUseCaseTest,PlatformMemoryPropertiesTest,JdbcPlatformMemoryAdapterTest,JdbcPlatformTenantAdapterTest,PatientAnalysisReportComposerTest,PatientAnalysisAgentTest,PatientAnalysisUseCaseTest,ReportLookupToolTest,PlatformAgentRunnerPatientAnalysisTest,PlatformAuthTenantTest,SecurityContextRunScopeAdapterTest,PlatformTenantMemoryHttpTest,PlatformSeedInitializerTest,PlatformRbacPermissionUpgradeInitializerTest" `
    "-Dsurefire.failIfNoSpecifiedTests=false"
  ```

- enterprise 本周用例（4 个类 / 16 例）：

  ```powershell
  ... -pl apps/enterprise-knowledge-agent -am test `
    "-Dtest=SsoLoginUseCaseTest,LoginFacadeTest,HttpPlatformIdentityAdapterTest,JdbcSsoUserLinkAdapterTest" `
    "-Dsurefire.failIfNoSpecifiedTests=false"
  ```

- patient 本周用例：`... -pl apps/patient-agent -am test "-Dtest=DeprecatedApiFilterTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
- 启动平台（8084）：模块目录下 `.\run-maven-jdk17.ps1 spring-boot:run`（`jdbc` profile 默认激活）
  > 前提：先 `run-maven-jdk17.ps1 -pl libs/ai-core -am -DskipTests install`。
  > 直接 `spring-boot:run` 会用本地仓库里的旧 `ai-core` SNAPSHOT，真机表现为
  > `NoClassDefFoundError: com/aicode/core/domain/port/GuardrailPort`（本周实测踩过）。
- 启动 enterprise（8081）：同法；SSO 模式见 `.env.example` 的 `SSO_*`
- 启动 patient-agent（8083，**deprecated**）：仅作回归对照
- 接口：`POST /api/v1/platform/auth/verify`、`GET /api/v1/platform/tenants`、
  `GET /api/v1/platform/memories`、`POST /api/v1/platform/agents/patient-analysis/runs`
- Postman：导入 `docs/postman/week-21.postman_collection.json`

## 增量架构

见 `docs/architecture/week-21-architecture.md`（只画本周新增 / 改动节点，含 Port / Adapter / UseCase 清单）。

## 一、需求对账：文档写的东西，和代码里的东西不是一回事

本周先做了一次「需求 → 现状」对账，三条遗留里有两条的**端口号是错的**。以 `application.yml` 为准：

| 文档（README / 规范 7.4 / 部分周次 Spec） | 代码实际 | 本周处理 |
|---|---|---|
| enterprise 在 **8083** | `enterprise-knowledge-agent/application.yml` → **8081** | 本周文档统一按 **8081** 写；SSO 默认指向 `http://localhost:8084` |
| patient-agent 在 **8082** | `patient-agent/application.yml` → **8083** | 本周弃用标记打在 **8083**；规范 7.4 与 README 同步改正 |
| platform 8084 / gateway 8080 | 一致 | 不动 |

> 教训（写进实现日志）：端口这类「事实」必须以可执行配置为准做一次核对，否则 SSO 的 base-url
> 会照着文档写错，表现为「凭据明明对的，登录一直 401」——与第 19 周「报错信息指向的位置是错的」同一类问题。

其余对账：

| 需求 | 现状（Week 20 末） | 本周怎么补 |
|---|---|---|
| 多租户 | 无租户概念，`platform_user` 只有 id/username/role | 新增 `platform_tenant` + `tenant_key`，登录/`/me` 回带租户 |
| 统一身份 SSO | enterprise 有独立 `app_user` + 独立密码；platform 有 `platform_user`，**两套账号互不相识** | 平台新增 `/auth/verify`（不建会话）；enterprise 配置 `sso.enabled` 时委托平台校验 + `sso_user_link` 自动开户 |
| Agent 1 患者分析 | `medical-assistant`（Week 11）已覆盖「数据/分析/报告/随访」，但**不查检查报告**、**不写记忆**、**不是可配置 Agent**（只能直连 `/api/v1/multi-agent/runs`，不走平台执行记录/RBAC 统一入口） | 新增平台一等 Agent `patient-analysis`，复用工具与风险评估，补齐「检查报告 + 记忆 + 执行记录」 |
| 长期记忆隔离 | ai-core `LongTermMemoryPort.search(query, topK)` **没有租户/用户维度**（Week 7 只有 sessionId） | 平台侧新增 `PlatformMemoryPort`（scope 必填）+ `platform_memory` 表 + 查询 API；**不改** ai-core 契约 |
| 8083 收敛 | patient-agent 仍是第 5–7 周独立 ReAct 服务 | 标注 deprecated（响应头 + 启动告警 + 文档），Run 入口指向 8084；**不删代码** |

## 二、租户：SaaS 的第一道边界

### 2.1 表与模型

`V4__platform_tenant.sql`（H2/PostgreSQL 双库可跑，规范 3.7）：

```sql
CREATE TABLE IF NOT EXISTS platform_tenant (
    tenant_key   VARCHAR(64)  PRIMARY KEY,
    display_name VARCHAR(128) NOT NULL,
    status       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE platform_user ADD COLUMN IF NOT EXISTS tenant_key VARCHAR(64) NOT NULL DEFAULT 'default';
```

- 默认值 `'default'` 保证**老库/老数据**升级后立即可用（不要求先回填）；
- `status` 只允许 `ACTIVE` / `SUSPENDED`（枚举 `TenantStatus`，落库为字符串）；
- seed：`default`（默认租户）+ `hospital-b`（演示医院 B），后者带一个用户 `doctor_b`，
  用于**证明隔离真的生效**（不是只写在文档里）。

### 2.2 语义（写死并单测）

| 规则 | 说明 |
|------|------|
| 租户来源 | **只来自服务端用户记录**（`platform_user.tenant_key`），任何请求参数都不得指定租户 |
| 登录 | 租户 `SUSPENDED` → 401（对外只说「凭据无效」，不暴露「你的租户被停用」） |
| 执行 | 用户只能读到自己租户内、自己名下的记忆与执行记录（执行记录沿用 Week 16 的 `user_id` 归属规则） |
| 管理 | `GET /tenants` 仅 `admin`；非管理员 403 |
| 老数据 | 迁移后所有既有行落在 `default` 租户，Week 13–20 的用户/角色/执行记录行为不变 |

## 三、统一身份 SSO（enterprise 8081 → platform 8084）

### 3.1 身份源只有一个：平台

平台新增（**不创建会话**，因此 enterprise 反复登录不会在平台侧留下无人使用的 session）：

```
POST /api/v1/platform/auth/verify
{"username":"operator","password":"operator123"}
→ 200 {"code":"SUCCESS","data":{"userId":2,"username":"operator","roleKey":"operator","tenantKey":"default"}}
→ 401 凭据错误 / 租户停用
```

审计：`verify` 成功写 `LOGIN`（detail 带 `sso=true`），失败写 `LOGIN_FAILED` —— SSO 登录在平台侧同样可追溯。

### 3.2 enterprise 侧：默认不变，开关切换

| 项 | 值 |
|----|----|
| 开关 | `sso.enabled`（默认 **false**，`SSO_ENABLED`） |
| 平台地址 | `sso.platform-base-url`（默认 `http://localhost:8084`，`SSO_PLATFORM_BASE_URL`） |
| 超时 | `sso.platform.timeout-ms`（默认 3000） |
| 自动开户 | `sso.auto-provision`（默认 true） |
| 行为 | 开：`POST /api/v1/auth/login` 委托平台 `verify`，按 `sso_user_link` 找本地用户，找不到则建本地用户并建链；关：走第 4 周本地校验，**代码路径与测试一行不改** |
| 失败 | 平台不可达 → **503 `AUTH_SSO_UNAVAILABLE`**（不伪装成 401，避免把「平台挂了」误判成「密码错了」）；凭据错 → 401 |
| 冲突 | 平台用户首次登录、但本地已有同名用户且未建链 → **409**，要求管理员显式处理（不自动合并账号，防止张冠李戴） |

`V7__sso_user_link.sql`：

```sql
CREATE TABLE IF NOT EXISTS sso_user_link (
    platform_user_id   BIGINT      PRIMARY KEY,
    enterprise_user_id BIGINT      NOT NULL UNIQUE,
    tenant_key         VARCHAR(64) NOT NULL,
    linked_at          TIMESTAMP WITH TIME ZONE NOT NULL
);
```

为什么不直接共用 `platform_user` 表（选项对照见架构文档 ADR）：两模块历史表/迁移版本独立
（enterprise 用默认 `flyway_schema_history`，platform 用 `flyway_schema_history_platform`），
且 enterprise 的 `app_user` 被文档归属、会话、Token 统计外键依赖；改表等于一次性数据迁移，
风险远大于一张映射表。

## 四、患者分析 Agent（Agent 1）

### 4.1 为什么是「平台一等 Agent」而不是再写一个 `/runs`

README 第 6 阶段的 Agent 1 要求：查询患者、查询检查报告、查询健康指标、风险分析、自动生成报告。
Week 11 的 `medical-assistant` 已有前四项里的三项，但它**只从 `/api/v1/multi-agent/runs` 直连**：
不进执行记录、不受平台 RBAC、不写记忆、不产生 `agent.run` 指标。本周把它做成
`AgentType.PATIENT_ANALYSIS`，经 `POST /api/v1/platform/agents/patient-analysis/runs` 调度，
一次性获得 Week 13–20 的全部能力（RBAC / Guardrail / 审计 / 执行记录 / OTel / Langfuse / Prometheus）。

### 4.2 执行链路（确定性步骤 + 一次 LLM）

```text
patientId
  ├─ PatientLookupTool  → 患者基础信息
  ├─ ReportLookupTool   → 检查报告列表（本周新增工具）
  ├─ HealthMetricTool   → 健康指标
  ├─ PatientRiskAssessor→ 风险等级 + 依据（纯规则，不烧 token）
  ├─ LLM（patient-analysis 模板）→ 分析报告
  └─ PlatformMemoryPort → 写一条按 租户+用户 隔离的长期记忆
```

| 步骤 | 失败处理 |
|------|----------|
| 工具调用 | 抛 `ToolExecutionException`，本次执行 FAILED（不吞错） |
| 风险评估 | 纯规则，无外部依赖 |
| LLM | `ChatModelException` → **降级**：用规则模板生成报告并置 `degraded=true`（日志 WARN），执行仍 COMPLETED；不把规则文本伪装成模型输出 |
| 写记忆 | 失败只 WARN，不影响本次结果（记忆不是主链路单点） |

### 4.3 输出契约（`PlatformRunOutput.output`）

| 字段 | 说明 |
|------|------|
| `patientId` / `patient` | 患者编号 + 基础信息（name/age/gender/diagnosis） |
| `reportCount` / `reports` | 检查报告数量 + 列表（reportId/type/date/conclusion/abnormal） |
| `metrics` | 收缩压 / 舒张压 / 空腹血糖 / HbA1c |
| `riskLevel` / `justification` | 风险等级与判断依据（复用 Week 9 的 `PatientRiskAssessor`） |
| `report` | 分析报告正文 |
| `degraded` | 是否走了 LLM 降级 |
| `memoryId` | 写入的长期记忆 ID（未写成功为 null） |
| `steps` / `totalSteps` | 步骤轨迹（与 Week 11 同构，便于前端复用） |

### 4.4 权限与工具

- `operator` 角色新增：Agent `patient-analysis`、Tool `ReportLookupTool`（`admin` 天然全通）；
- Guardrail 参数白名单不变（`patientId` / `task`），工具由 Agent 内部调用，仍经 `AuthorizingToolPort`；
- `AgentType` 新增枚举值；未注册/禁用/越权的行为与既有 Agent 完全一致（复用 `PlatformExecutionUseCase`）。

## 五、长期记忆：按租户 + 用户隔离

### 5.1 表

`V5__platform_memory.sql`：

```sql
CREATE TABLE IF NOT EXISTS platform_memory (
    memory_id  VARCHAR(64)  PRIMARY KEY,
    tenant_key VARCHAR(64)  NOT NULL,
    user_id    BIGINT       NOT NULL,
    session_id VARCHAR(64)  NOT NULL,   -- 会话语义：patientId
    task       VARCHAR      NOT NULL,
    answer     VARCHAR      NOT NULL,
    embedding  VARCHAR,                 -- 向量以 JSON 文本存储（见 ADR）
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_platform_memory_scope ON platform_memory(tenant_key, user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_platform_memory_session ON platform_memory(tenant_key, user_id, session_id);
```

### 5.2 端口与实现

| 类型 | 名称 | 职责 |
|------|------|------|
| 值对象 | `PlatformMemoryScope` | `(tenantKey, userId)`，**必填**，构造时校验非空/正数 |
| 模型 | `PlatformMemoryRecord` / `PlatformMemoryHit` | 记忆记录 / 命中（含 score） |
| Port | `PlatformMemoryPort` | `save`、`search(scope, query, topK)`、`listRecent(scope, limit)` |
| 适配器 | `InMemoryPlatformMemoryAdapter` / `JdbcPlatformMemoryAdapter` | `platform.persistence.mode=memory/jdbc`，与既有适配器同套开关 |
| 用例 | `PlatformMemoryUseCase` | 组装 scope、向量化、阈值过滤、排序；**scope 只从用户记录推导** |

### 5.3 检索语义

- 向量：`EmbeddingModelPort.embed(task)`（默认 hashing，离线确定性；`embedding.provider=openai` 时自动换真模型）；
- 候选集：先按 `scope` 取最近 N 条（`platform.memory.candidate-limit`，默认 500），再在 Java 侧算余弦——
  与 `ai-core` `VectorMath.cosine` 同一实现，不重复造；
- 过滤：`score >= platform.memory.min-score`（默认 0.05，可配），按 score 降序取 topK（默认 5，上限 20）；
- **隔离在 SQL 层**：`WHERE tenant_key = ? AND user_id = ?` 是适配器方法签名的强制参数，
  用例层**没有**「不带 scope 的查询」入口；单测覆盖「同租户不同用户」与「同用户不同租户」两种越界。

### 5.4 API

```
GET /api/v1/platform/memories?patientId=P001&query=血糖控制&limit=5
→ 200 {"code":"SUCCESS","data":{"items":[{"memoryId":"...","patientId":"P001","task":"...",
        "answer":"...","score":0.42,"createdAt":"2026-10-24T08:00:00Z"}]}}
```

- 默认只返回**当前用户**的记忆；`admin` 可加 `userId` 查他人（未加则查自己）；
- 非管理员传 `userId` → 403；对不存在的 `userId` → 404；
- 响应只回任务/结论/时间/分数，**不回 embedding**（体积大且无意义）。

## 六、patient-agent（8083）标注 deprecated

| 动作 | 内容 |
|------|------|
| 响应头 | 所有响应加 `Deprecation: true`、`Sunset: Wed, 31 Dec 2026 23:59:59 GMT`、`Link: <http://localhost:8084/api/v1/platform/agents/patient-analysis/runs>; rel="successor-version"` |
| 启动日志 | `[deprecated] patient-agent 将于 2026-12-31 下线，请改用 8084 的 patient-analysis` |
| 文档 | README 第 5–7 周与规范 7.4 标注；`docs/specs/week-21.md` 作为唯一去向说明 |
| 代码 | **不删**：第 5–7 周 19 个用例仍全绿，作为回归对照；不为「收敛」改它的业务行为 |

## 七、关于 pgvector：本周明确不做，理由写死

规范 7.4 写「长期记忆按用户/租户隔离（pgvector）」。本周把**隔离**做实，把**存储形态**留活：

| 方案 | 结论 |
|------|------|
| `platform_memory.embedding` 用 `vector(1024)` + HNSW | **本周不做**：平台模块没有 pgvector 依赖；平台库与其它应用共用，`CREATE EXTENSION` 属库级改动（规范 3.7.1 明确要求扩展脚本单列且只对 PG 生效）；H2 单测无法覆盖，等于「写了一段没人验证过的 SQL」 |
| 文本存向量 + Java 余弦（**采用**） | 单用户记忆量级是几十到几百条（候选集上限 500），余弦成本可忽略；同一套代码 H2/PostgreSQL 都能跑，单测能真跑；`PlatformMemoryPort` 已把存储形态隔离，后续换 pgvector 只换适配器 |

换 pgvector 的触发条件（写进 Open Questions）：单用户记忆 > 1 万条，或需要跨用户共享知识检索。

## 八、降级与安全

- **平台挂了**：enterprise 登录 503（明确「身份源不可用」），本地会话不受影响；
- **SSO 关了**：enterprise 完全回到第 4 周行为，无额外网络调用、无额外依赖；
- **LLM 挂了**：患者分析降级出规则报告，`degraded=true`，执行记录 COMPLETED（与「失败」区分）；
- **记忆写失败**：只 WARN，`memoryId=null`，不影响报告；
- **越权**：租户/用户不匹配一律 403（`PlatformAccessDeniedException`），不返回「记录存在但你看不到」的差异信息；
- **密钥**：SSO 只传 username/password（HTTPS 由部署层保证）；平台不返回 token 给 enterprise；
  文档与 `.env.example` 不含任何真实地址/密钥；
- **密码**：`verify` 与 `login` 同一套哈希校验；审计只记用户名，不记密码。

## Boundaries

- **Always**：TDD（先 RED）；中文 JavaDoc；Week 4/13–20 契约零回退；
  租户/用户隔离在 SQL 层强制；迁移脚本双库可跑（H2 + PostgreSQL）；规范 6.0 交付包齐全
- **Ask first**：改动 `libs/ai-core` 的 Port 契约；把 enterprise 的 `app_user` 迁到 `platform_user`；
  平台库执行 `CREATE EXTENSION vector`；删除 patient-agent 模块；给网关加新路由
- **Never**：本周做 Agent 2/3/4；改 Week 16 响应信封；把租户/用户放进请求参数由客户端指定；
  把 LLM 降级报告伪装成模型输出；为 SSO 在日志里打印密码或平台 token；
  引入注册中心 / K8s / 消息队列；改 8083 的既有业务行为（只加响应头与日志）

## Success Criteria

- [x] 迁移：`V4__platform_tenant.sql` / `V5__platform_memory.sql`（平台）、`V7__sso_user_link.sql`（enterprise）
      在 H2 与 PostgreSQL 上均可执行；真库首次启动 `Successfully applied 2 migrations ... now at version v5`；
      重启只 `Successfully validated 5 migrations`、seed 只补缺（真机两次启动实测）
- [x] 租户：登录/`/me` 回带 `tenantKey`；`GET /tenants` 仅 admin（401/403/200 三态）；
      停用租户登录 401 且不泄露停用原因（`PlatformTenantUseCaseTest` / `PlatformAuthTenantTest` /
      `PlatformTenantMemoryHttpTest`）
- [x] SSO：`POST /api/v1/platform/auth/verify` 免登录（白名单）、不创建会话、不返回 token、凭据错 401、
      成功写 `LOGIN` 审计；enterprise `sso.enabled=true` 时首次登录自动开户并建链、二次登录复用链、
      同名未建链 409、平台不可达 503、`sso.enabled=false` 时本地登录行为不变
      （`SsoLoginUseCaseTest` / `HttpPlatformIdentityAdapterTest` / `PlatformTenantMemoryHttpTest`）
      —— **说明**：平台侧真机已验证；enterprise 侧因 `.env` 仍指向已下线节点、且云上 `aidemo` 库没有
      enterprise 的表，未做跨进程真机验收（详见实现日志 9.6），该项留给第 22 周
- [x] Agent 1：`POST /api/v1/platform/agents/patient-analysis/runs` 返回 5 类数据 + steps + memoryId；
      LLM 异常时 `degraded=true` 且执行 COMPLETED；越权 patientId 403；未注册 404
      （`PatientAnalysisUseCaseTest` / `PatientAnalysisAgentTest` / 真机真实模型 1587 tokens 一次 COMPLETED）
- [x] 记忆隔离：用户 A 写、用户 B 查不到；同用户跨租户查不到；admin 可查指定用户；
      非管理员指定 userId 403；响应不含 embedding（`PlatformMemoryUseCaseTest` /
      `JdbcPlatformMemoryAdapterTest` / 真机 `doctor_b` 查同一患者 0 条）
- [x] 8083 弃用：所有响应带 `Deprecation` / `Sunset` / `Link` 三个头；启动 WARN；
      第 5–7 周 19 个用例零回退（`DeprecatedApiFilterTest`）
- [x] 契约：`AgentType` 新增值不破坏既有序列化用例；`PlatformRole` 新增工具/Agent 后
      Week 13–15 用例零回退；`X-Trace-Id` 与响应信封不变（`ApiStandardsContractTest`）
- [x] 根聚合 `test` 全绿：**701** 用例（ai-core 90 / enterprise 37 / patient 21 / platform 453 / gateway 100），
      本周新增 **86**，Week 4–20 用例零回退
- [x] 交付物齐全：Spec / 架构 / 接口 / Postman / 实现日志 / 阅读提纲；
      README 与规范 7.3/7.4 同步改正端口与状态

## 真机暴露并修复的缺陷（事后补登记）

> 三条都不是业务逻辑错误，而是**单测默认不经过的装配/初始化**：白名单、seed 开关、配置映射。
> 与第 19–20 周「真机暴露的问题往往在装配层」的结论一致。

| # | 现象 | 根因 | 修法 |
|---|------|------|------|
| 1 | 真机调 `/api/v1/platform/auth/verify` 返回 **401**（凭据正确） | 新端点没进 Sa-Token 白名单，`/platform/**` 一律要求登录；而 verify 本身就是「用来拿身份」的 | 加 `.notMatch("/api/v1/platform/auth/verify")`，并补 2 个**走完整拦截器链**的 HTTP 用例 |
| 2 | 真机上 operator 跑患者分析 **403**；`platform_user` 只有 3 行（缺 `doctor_b`） | 两个 seed 都以「表是否为空」为整体开关：老库已有用户/角色 → 本版本新增的用户与授权永远补不上 | 租户/用户 seed 改**逐 key/id 判空**（不覆盖运维状态）；新增 `PlatformRbacPermissionUpgradeInitializer` 做**权限并集升级**（只补新增，不恢复被撤销的） |
| 3 | 记忆落库成功但向量为空，语义检索永远 0 命中 | 平台 `application.yml` 只映射 `embedding.provider`，没映射 `api-key/base-url/model`；`.env` 里的 `EMBEDDING_API_KEY` 存在却读不到 | 补全 embedding 配置映射；修复后同一查询命中 `score=0.6703` |

## 与计划的偏差（预登记）

| 计划（README / 规范 7.4） | 实际做法 | 原因 |
|---|---|---|
| 「8082 patient-agent 收敛至 8084」 | patient-agent 实际在 **8083**；本周只标注 deprecated，不删代码 | 端口文档错误（见一）；删除会让第 5–7 周 19 个用例失去回归价值 |
| 「enterprise（8083）↔ platform（8084）SSO」 | enterprise 实际在 **8081**；SSO 做成**开关式委托**，不合并用户表 | 共用表 = 一次性数据迁移 + 两套 Flyway 历史表冲突；映射表风险最小 |
| 「长期记忆按用户/租户隔离（pgvector）」 | 隔离按租户+用户落库并用单测证明；向量以 JSON 存储，pgvector 延后 | 见七 |
| README「Agent 1：患者分析」 | 复用 Week 9 风险评估与 Week 6 工具，不重写一条独立链路 | 重复建设会分叉两套风险规则 |
| 计划未提「检查报告」数据源 | 新增 `ReportLookupTool`（演示数据，确定性） | Agent 1 明确要求「查询检查报告」，现有工具集没有 |

## Open Questions

- enterprise 的 `app_user` 何时真正并入 `platform_user`（现在是映射表）→ 等有第二个模块要接 SSO 时再评估；
- 租户级配置（模型、Prompt、价目表）是否按 `tenant_key` 分表 → 本周只有平台级配置，YAGNI；
- `platform_memory` 何时切 pgvector → 单用户 > 1 万条，或需要跨用户共享检索；
- 患者分析是否接 HITL（高风险报告人工确认）→ Week 10 的 HITL 在 workflow 侧，Agent 侧留到第 22 周；
- 8083 的下线日期（`Sunset`）是否与真实排期一致 → 现填 2026-12-31，需团队确认；
- **换 embedding 模型后旧记忆向量不可比**（本周真机留下的 2 条空向量就是配置未生效时的产物）→
  第 22 周补「重建 / 丢弃」工具，否则最多只能「清表重来」；
- 角色是否按租户隔离（现在 `platform_role` 是全局的）→ 有第二个租户要独立配权时再做，需要 `tenant_key` 进角色表。

## ADR（本周）

完整对照见 `docs/architecture/week-21-architecture.md` 第 8 节与实现日志第 12 节，摘要：

| 决策 | 选项 | 选择 | 理由 |
|------|------|------|------|
| 身份源 | 双写 / 平台唯一 | 平台唯一（`/auth/verify`） | 两套密码 = 两套过期策略与两套泄露面 |
| SSO 接入方式 | 共用用户表 / 映射表 | 映射表 `sso_user_link` | 迁移风险与 Flyway 历史表冲突 |
| 统一入口 | 直连 multi-agent / 平台 Agent | 平台 Agent | RBAC / 审计 / 执行记录 / 指标一次到位 |
| 记忆隔离位置 | 用例层过滤 / SQL 层谓词 | SQL 层 | 用例层漏一个分支就是数据泄露 |
| 记忆存储 | pgvector / 文本向量 | 文本向量（Port 隔离） | H2 可测、库级改动最小，见七 |
| 8083 处理 | 删除 / 标注弃用 | 标注弃用 | 保留回归对照，风险最低 |
