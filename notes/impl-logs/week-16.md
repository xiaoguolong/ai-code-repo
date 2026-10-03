# Week 16 实现日志 — 企业规范（API / 日志 / 审计 / 异常 + RBAC 落库）

- 日期：2026-10-01
- 批次：1
- 对应 Spec：docs/specs/week-16.md
- 对应架构：docs/architecture/week-16-architecture.md
- 对应接口：docs/api/week-16-api.md

## 1. 本周目标

1. 落地 **Week 13 遗留项**：8084 平台 `PlatformUserPort` / `PlatformRolePort` / `ExecutionRecordPort` 换成 PostgreSQL 落库，配套 Flyway `V1–V3`。
2. 新增 **审计规范**：`AuditLogPort` + 内存/JDBC 双适配器，登录成功/失败、Agent Run 终态、越权读执行记录四类事件留痕，并提供只读查询 API。
3. 新增 **日志规范**：`X-Trace-Id` 全链路（MDC + 响应头 + 日志格式）、统一访问日志。
4. 新增 **异常/API 规范**：错误码收敛到 `ApiErrorCode` 枚举、`ApiResponse` 增加 `traceId`、契约测试保证不泄漏内部信息。

## 2. 边界

- Always：Port 契约不变只换适配器；迁移脚本 Postgres/H2 双库可跑；审计与 traceId 有单测；中文 JavaDoc；无密钥入库
- Never：改 Graph / Workflow / Multi-Agent 编排；改 RBAC 与 Guardrail 业务规则；引 OTel / Langfuse / SkyWalking；改响应信封结构

## 3. 增量架构

见 `docs/architecture/week-16-architecture.md`（图 2 增量拓扑、图 3 traceId 链路、图 4 持久化装配）。

## 4. 新增类型清单

| 类型 | 名称 | 职责 |
|------|------|------|
| Enum | `ApiErrorCode` | 错误码 + HTTP 状态 + 中文文案唯一来源 |
| Filter | `TraceIdFilter` | traceId 生成/透传、写 MDC、回写响应头、finally 清理 |
| Filter | `ApiAccessLogFilter` | 统一访问日志（方法/路径/状态/耗时/traceId） |
| Advice | `ApiResponseTraceAdvice` | 给所有 `ApiResponse` 补 traceId，成功失败口径一致 |
| Util | `TraceIds` | MDC 键名/头名/长度归一化与读取 |
| Model | `AuditLogEntry` / `AuditAction` / `AuditResult` | 审计领域模型 |
| Port | `AuditLogPort` | 审计写入与按用户/全量查询 |
| Adapter | `JdbcPlatformUserAdapter` | `platform_user` 覆盖写与查询 |
| Adapter | `JdbcPlatformRoleAdapter` | `platform_role` + agent/tool/patient 三张授权子表，先删后插覆盖写 |
| Adapter | `JdbcExecutionRecordAdapter` | `execution_record` RUNNING→COMPLETED/FAILED 覆盖写 |
| Adapter | `JdbcAuditLogAdapter` | `audit_log` 写入与倒序查询 |
| Adapter | `InMemoryAuditLogAdapter` | `mode=memory` 审计 |
| Config | `PlatformJdbcConfiguration` / `PlatformJdbcSupport` | 落库适配器装配、双库主键与时间转换 |
| UseCase | `PlatformAuditUseCase` | 审计查询（admin 全量，其余仅本人） |
| Controller | `PlatformAuditController` | `GET /api/v1/platform/audit-logs` |
| DTO | `AuditLogResponse` | 审计响应体（不含凭据） |
| Migration | `V1__platform_rbac.sql` / `V2__platform_execution_record.sql` / `V3__platform_audit_log.sql` | RBAC / 执行记录 / 审计表 |
| Test | `ApiErrorCodeContractTest`(45) / `ApiStandardsContractTest`(5) / `PlatformAuthAuditTest`(5) / `PlatformExecutionAuditTest`(5) / `PlatformAuditUseCaseTest`(3) / `PlatformAuditSecurityTest`(1) / `InMemoryAuditLogAdapterTest`(3) / `JdbcPlatformUserAdapterTest`(4) / `JdbcPlatformRoleAdapterTest`(5) / `JdbcExecutionRecordAdapterTest`(6) / `JdbcAuditLogAdapterTest`(4) / `PlatformSeedIdempotencyTest`(4) | 规范契约与落库回归 |

改动既有文件：`ApiResponse`（加 traceId）、`GlobalExceptionHandler`（枚举错误码 + traceId）、`PlatformAuthUseCase` / `PlatformExecutionUseCase`（审计）、`PlatformRbacSeedInitializer`（按 Port 判空，双模式幂等）、三个 InMemory 适配器（加 `mode=memory` 条件）、`SpringAiAlibabaAgentApplication`（去掉 DataSource 排除）、`application.yml` + 新增 `application-jdbc.yml`、`pom.xml`（jdbc/flyway/postgresql/h2 依赖 + 启动默认 jdbc profile）。

## 5. RED

### 批次 1（规范与落库能力完全缺失）

- 先写 12 个测试类，随后执行 `mvn -Djdk.17.home=... -pl apps/spring-ai-alibaba-agent -am test-compile`
- 结果：`BUILD FAILURE` —— `ApiErrorCode`、`AuditLogPort`、`AuditLogEntry`、`Jdbc*Adapter` 等被测类型尚不存在，测试无法编译
- 原因：生产代码未写，符合预期

### 批次 2（测试可编译，但装配与配置未收敛）

| 现象 | 数量 | 根因 | 处置 |
|------|------|------|------|
| `ApplicationContext` 加载失败，所有 Spring 上下文测试报错 | 52 | Spring Boot 3.4 禁止 `spring.profiles.default` 出现在未加 `spring.config.activate.on-profile` 的文档中（`InactiveConfigDataAccessException`，application.yml:100） | 数据源与 Flyway 迁到独立 `application-jdbc.yml`，启动默认 profile 改由 `spring-boot.run.profiles`（mvn）与 `--spring.profiles.active=jdbc`（jar）提供 |
| 测试上下文因无数据源无法装配落库 Port | 若干 | H2/JDBC 依赖未入 pom | pom 增加 `spring-boot-starter-jdbc`、`flyway-core`、`flyway-database-postgresql`、`postgresql(runtime)`、`h2(test)` |
| `JdbcExecutionRecordAdapterTest` 1 例 `DataIntegrityViolationException` | 1 | 违反 `fk_execution_record_user`：测试直接写 `user_id=4` 但 `platform_user` 无该行 | 测试 `@BeforeEach` 先落库被引用的用户行（保留外键约束，不放宽 schema） |

## 6. GREEN

- 改动文件：见第 4 节清单（新增 24 个文件，改动 9 个文件）
- 命令：`apps/spring-ai-alibaba-agent` 或根目录下 `run-maven-jdk17.ps1 test`（等价 `mvn -Djdk.17.home=... -s <maven-home>/conf/settings-ailocal.xml test`）
- 结果：`BUILD SUCCESS`，根聚合 **312** 个用例全绿

| 模块 | 用例数 | 结果 |
|------|--------|------|
| ai-core | 90 | 全绿 |
| enterprise-knowledge-agent | 21 | 全绿 |
| patient-agent | 19 | 全绿 |
| spring-ai-alibaba-agent | 182 | 全绿 |

本周新增用例 40 个（Week 15 时 8084 模块 142 → 本周 182）。

## 7. 重构

- 做了什么：把 JDBC 公共能力（模板持有、双库通用主键生成、`Instant`↔`Timestamp`）抽到 `PlatformJdbcSupport`，装配抽到 `PlatformJdbcConfiguration`，避免四个适配器重复；审计读取按最小权限收口到 `PlatformAuditUseCase`；错误码从散落字符串收敛为枚举。
- 没做什么：没有引入 MyBatis/JPA；没有把 Agent/Tool/Workflow 注册表落库（Week 13 遗留只列 RBAC + ExecutionRecord，留 Week 21）；没有改响应信封结构。

## 8. 质量门禁

- [x] 编译：`mvn -Djdk.17.home=... test` 全模块通过
- [x] 单测：根聚合 312 用例全绿，Week 8–15 无回退
- [x] JDK：未改 `JAVA_HOME`，全部命令只传 `-Djdk.17.home=...`；pom 保留 `jdk.17.home`
- [x] 文档：新增/改动 public 类型均有中文 JavaDoc（含 `ApiErrorCode` 各枚举项触发条件）
- [x] 分层：Controller 仅做登录态与 DTO 转换；SQL 只在 `infrastructure/persistence`；UseCase 不感知 JDBC
- [x] 安全：无密钥入库；密码只存哈希；审计与日志不含密码/token/API Key；错误响应不含堆栈与 SQL
- [x] YAGNI：未引入 ORM、Testcontainers、OTel/Langfuse 等后续周次能力
- [x] 日志：本文件已填 RED/GREEN 证据

## 9. 验证证据

```text
# 根聚合测试（真实输出摘要）
[INFO] Building ai-core 0.1.0-SNAPSHOT                                    [1/5]
[INFO] Tests run: 90, Failures: 0, Errors: 0, Skipped: 0
[INFO] Building enterprise-knowledge-agent 0.1.0-SNAPSHOT                 [2/5]
[INFO] Tests run: 21, Failures: 0, Errors: 0, Skipped: 0
[INFO] Building patient-agent 0.1.0-SNAPSHOT                              [3/5]
[INFO] Tests run: 19, Failures: 0, Errors: 0, Skipped: 0
[INFO] Building spring-ai-alibaba-agent 0.1.0-SNAPSHOT                    [4/5]
[INFO] Tests run: 186, Failures: 0, Errors: 0, Skipped: 0
[INFO] Building ai-code-repo 0.1.0-SNAPSHOT                               [5/5]
[INFO] BUILD SUCCESS

# 生产可交付物（clean package，跳过测试）
mvn -Djdk.17.home=... -q clean package -DskipTests
exit=0
apps/spring-ai-alibaba-agent/target/spring-ai-alibaba-agent-0.1.0-SNAPSHOT.jar  (92,575,548 bytes)

# 真实 PostgreSQL 16.15 落库验收（详见第 10 节）
Flyway: Successfully applied 3 migrations, now at version v3
Tomcat started on port 8084 → 接口 A–H 全部通过
```

关键验收点（均有对应测试）：

- 落库装配：`mode=jdbc` 时四个 Port 装配 `Jdbc*Adapter`（`JdbcPlatformUserAdapterTest.wiresJdbcAdapterWhenModeIsJdbc` 等）
- 授权集合读写：operator 三种授权 2/2/10 项且覆盖写生效（`JdbcPlatformRoleAdapterTest`、`PlatformSeedIdempotencyTest`）
- 执行记录状态机：RUNNING → COMPLETED 覆盖写并保留 token 与起止时间；`listByUserId` 倒序（`JdbcExecutionRecordAdapterTest`）
- 迁移与幂等：H2（`MODE=PostgreSQL`）与**真实 PostgreSQL 16.15** 上 Flyway V1–V3 均成功应用；seed 重复执行不重复插入（`PlatformSeedIdempotencyTest`、真库重启验证）
- 审计留痕：登录成功/失败、Run 终态、越权读各 1 条，且审计故障不阻断登录（`PlatformAuthAuditTest`、`PlatformExecutionAuditTest`）
- 审计 traceId：从 MDC 取值并与请求头/响应体一致（`PlatformAuthAuditTest`、`PlatformExecutionAuditTest` 中 4 个 MDC 回归用例 + 真库确定性验证）
- 全链路追踪：响应体 traceId 与响应头 `X-Trace-Id` 一致，客户端传入值被透传（`ApiStandardsContractTest`）
- 错误码契约：错误码全部来自枚举、HTTP 语义正确、无堆栈/SQL/密钥泄漏（`ApiErrorCodeContractTest`、`ApiStandardsContractTest`）

Postman：`docs/postman/week-16.postman_collection.json`

## 10. 真库验证（批次 2，真实 PostgreSQL 16.15）

环境：本机 VMware 虚拟机内的 PostgreSQL 16.15（Redis 同机）；连接串取自本环境仓库既有的 `.env`
（`SPRING_DATASOURCE_URL=jdbc:postgresql://<db-host>:<db-port>/<db-name>`，用户 `<db-user>`），未新建库、未改动既有库中的其它应用对象。
该库已被 `spring-ai-demo` 用掉 Flyway V1–V6，因此本周迁移改用**本模块专属历史表** `flyway_schema_history_platform`。

### 10.1 真库暴露的 3 个缺陷（H2 全部测不出来）

| # | 现象 | 根因 | 处置 |
|---|------|------|------|
| 1 | V2 迁移失败：`ERROR: type "clob" does not exist` | `input_json` / `output_json` 用了 `CLOB`。H2 `MODE=PostgreSQL` 容忍 CLOB，PostgreSQL 无此类型 | 改为 `VARCHAR`（无长度）：PostgreSQL 不限长、H2 取最大值，双库通用 |
| 2 | `platform_*` 表一张都没建，历史表只有一条 `v1 BASELINE` | `baseline-on-migrate=true` 且默认 `baselineVersion=1`：Flyway 在「库非空且历史表不存在」时引导，并把 V1 记为已应用 → **V1 被跳过** | 显式 `baseline-version: 0`，引导记录落在 v0，V1–V3 正常执行 |
| 3 | 改 `baseline-on-migrate=false` 后启动直接失败：`Found non-empty schema(s) "public" but no schema history table` | 共用库的 public 模式非空（有同库其它应用的表），不引导时 Flyway 拒绝建历史表 | 回到 `true` + `baseline-version: 0`（即 #2 的组合），并用独立历史表隔离版本 |

### 10.2 审计 traceId 为空（批次 2 发现并修复）

- 现象：真库 `audit_log.trace_id` 为空串，而 HTTP 响应体/响应头 traceId 正常。
- 根因：`PlatformAuthUseCase` 与 `PlatformExecutionUseCase` 调用 `AuditLogEntry.of(...)` 时把 traceId 参数**硬编码为 `null`**；而响应里的 traceId 是之后由 `ApiResponseTraceAdvice` 填的，两者无关。日志 MDC 本身是好的（同一请求的 `[api]`/`[platform]` 日志都带 traceId）。
- 为何单测没抓到：`PlatformAuthAuditTest` / `PlatformExecutionAuditTest` 全部传显式 traceId，从未验证「从 MDC 取值」这条真实路径。
- 处置：两处改为 `TraceIds.current()`；新增 4 个回归测试（登录成功/失败、Agent Run、越权读）在 `MDC.put` 后断言审计 traceId 与 MDC 一致。
- 证据：修复前 3 条审计 `trace_id=''`；修复后带确定性请求头 `X-Trace-Id: week16-deterministic-trace` 登录，落库为 `trace_id='week16-deterministic-trace'`。

### 10.3 真库最终证据（SQL 级）

```text
flyway_schema_history_platform:
  rank=1 v0 << Flyway Baseline >>  type=BASELINE success=true
  rank=2 v1 platform rbac          type=SQL      success=true
  rank=3 v2 platform execution record type=SQL   success=true
  rank=4 v3 platform audit log     type=SQL      success=true

行数：platform_user=3  platform_role=3  platform_role_agent=2  platform_role_tool=2
      platform_role_patient=10  execution_record=1  audit_log=4

audit_log：
  user=1 LOGIN        SUCCESS trace_id=''        ← 修复前的旧 jar
  user=0 LOGIN_FAILED FAILURE trace_id=''        ← 修复前
  user=1 AGENT_RUN    FAILURE trace_id=''        ← 修复前
  user=2 LOGIN        SUCCESS trace_id='week16-deterministic-trace'   ← 修复后

execution_record：
  d75e3567-… user=1 agent=medical-assistant status=FAILED
  started=2026-10-01 15:22:42.098855  finished=2026-10-01 15:22:42.840753

platform_user：id=1 admin / id=2 operator / id=3 viewer，password_hash 均为哈希（无明文）
execution_record.input_json / output_json 类型 = character varying
同库既有应用（`spring-ai-demo`）的 flyway_schema_history 仍为 6 行（未被本模块污染）
```

### 10.4 真库启动与接口验收（真实 HTTP）

```text
Flyway: Successfully validated 4 migrations → Migrating to v1/v2/v3
        → Successfully applied 3 migrations, now at version v3
Tomcat started on port 8084 / Started SpringAiAlibabaAgentApplication in 10.5s
重启：Successfully validated 4 migrations，Current version of schema "public": 3，
      未重复执行迁移、未重复 seed（platform_user 仍为 3 行）→ 幂等确认

A. 未登录 GET /audit-logs        → 401 UNAUTHORIZED，响应头与响应体 traceId 一致，data=null
B. POST /auth/login (admin)      → 200 SUCCESS，roleKey=admin，token 36 位
C. POST /auth/login (错误密码)   → 401 UNAUTHORIZED「用户名或密码错误」
D. POST /auth/login (空用户名)   → 400 VALIDATION_ERROR，data=null，traceId 非空
E. GET  /roles                   → admin(admin=true) / operator(2 agents, 2 tools, 10 patients) / viewer(空)
F. POST /agents/medical-assistant/runs → 502 CHAT_MODEL_ERROR（占位 Key，预期）
   → 落库 execution_record status=FAILED + 审计 AGENT_RUN/FAILURE
G. GET  /executions              → 1 条 FAILED，userId=1
H. GET  /audit-logs              → LOGIN / LOGIN_FAILED / AGENT_RUN 三类留痕
```

> 断言：`X-Trace-Id` 透传 → 响应体回显一致 → 审计 `trace_id` 落库一致（批次 2 修复后成立）。

### 10.5 批次 3：收尾审计发现并补的两个缺口

对本周交付做了一次「未完成/遗漏」专项审计，查出并修复：

| 缺口 | 问题 | 处置 | 验证 |
|------|------|------|------|
| `.env` 无人加载 | 仓库内所有模块都没有 `spring.config.import`，`.env` 只在手工导出环境变量时才生效；文档却写着 `spring-boot:run` 即可启动——真库环境下会因连默认 `localhost:5432` 而启动失败 | `application-jdbc.yml` 增加 `config.import: optional:file:.env[.properties]`，`optional:` 保证文件缺失时回退默认值 | 清空全部 `SPRING_DATASOURCE_*` / `SPRING_PROFILES_ACTIVE` 后再启动：`Successfully validated 4 migrations`、`Current version: 3`、8084 OPEN；登录 + `/roles`（读到 admin/operator/viewer）+ 审计 traceId 匹配 `env-import-check` |
| JavaDoc 缺失 | Week 15 遗留的 `EvalCaseSummary`、`EvalCaseResultResponse` 两个 public 内部 record 无 JavaDoc，违反规范 5.4 与门禁「public 类型均有 JavaDoc」 | 补中文 JavaDoc 与字段说明 | 全量扫描 `src/main/java` 下 public 类型，无缺失 |
| 规范 5.6 信封自相矛盾 | 规范 5.6 早期草案写 `{"error":{"code","message"}}` 且错误码用小写 `validation_error`，而自第 2 周起全仓实现均为扁平 `{code,message,data}` + 大写错误码——后来周次若照草案改造就会破坏全部既有接口 | 按实现校正规范 5.6，补齐约定细则表与历史说明；同步修正 `docs/api/week-02-api.md` 的信封与 4 处错误码示例（对照 `spring-ai-demo` 的 `GlobalExceptionHandler` 实际取值）；清理 week-16 规格/架构/API/`ApiResponse` JavaDoc 中"偏离"表述 | 全仓复扫 `"error": {` 无残留；规范 5.6 与实现一致 |
| `.env` 只在平台模块可读 | 只有 `spring-ai-alibaba-agent` 加了 `config.import`，其余 3 个模块的 `.env` 仍需靠外部 export 才生效，跨环境行为不一致 | 4 个模块的 `application.yml` 统一声明 `spring.config.import: optional:file:.env[.properties]`；`patient-agent` 的 Redis 显式兼容 Spring 标准名 `SPRING_REDIS_HOST/PORT` | 全量测试仍走 H2（日志为 `jdbc:h2:mem:testdb` / `jdbc:h2:mem:platform_test`，无真库地址）；单独验证环境变量优先级 |
| 数据库脚本注意事项未沉淀为规范 | 真库踩到的 `CLOB`、`baseline-version`、共用历史表等结论只写在 week-16 文档里，后续周次新建迁移脚本会重踩 | 规范新增 **3.7 数据库与迁移脚本约定（全局强制）**：方言禁用表、Flyway 历史表与引导、脚本不可变性、真库验收 5 步、跨环境叠加 5 项核查、`.env` 优先级 | 规范与 week-16 API 文档同步；两处核查清单可执行 |

### 10.6 配置优先级实测（跨环境安全的关键）

各环境可能在 shell 里 `export` 了 `.env` 的内容，也可能用 IDEA 的 EnvFile 插件注入；若 `.env` 反而覆盖环境变量，就会**静默连到错误的库**。实测结论（三组）：

| 场景 | 结果 |
|------|------|
| 清空 `SPRING_DATASOURCE_*`，只留 `.env` | 正常连上目标库并启动（`Successfully validated 4 migrations`、`Current version: 3`、8084 OPEN） |
| 设 `SPRING_DATASOURCE_URL` 指向 `127.0.0.1:1`（唯一标记）+ 保留 `.env` | 报 `Connection to 127.0.0.1:1 refused` → **环境变量胜出，`.env` 未反覆盖** |
| 单测（`@ActiveProfiles("test")` + `application-test.yml`） | 走 H2 `jdbc:h2:mem:*`，全程未出现真库地址 → **profile 配置胜出** |

即优先级：命令行参数 / 环境变量 → `application-<profile>.yml` → `.env` → `application.yml` 默认值。已写入规范 3.7.6。

### 10.7 批次 4：去除写死的库名默认值 + 适配 IDEA 运行方式

两个跨环境隐患，来自「某环境的库名被当成了通用默认值」：

| 问题 | 影响 | 处置 |
|------|------|------|
| `application*.yml` 里把某个历史库名写成数据源默认值 | 其它环境看到会误以为「必须建这个库」；缺配置时还会静默连到错误地址 | 三个模块的数据源默认值全部改为空占位 `${SPRING_DATASOURCE_URL:}` 等，缺配置时明确报缺失；库名/账号只由环境变量或 `.env` 提供 |
| `spring.config.import: file:.env` 按**进程工作目录**解析 | IDEA 运行配置的工作目录通常是项目根，模块级 `.env` 命不中，该 import 空转 | 明确 IDEA 场景用 EnvFile 插件注入（环境变量优先级更高，二者不冲突）；`config.import` 作为命令行/脚本方式的便利保留；规范 3.7.6 记录两种运行方式与相对路径语义 |
| `spring-ai-demo/docker-compose.yml` 写死同一个历史库名 | 同上的误导 | 统一为与模块对应的中性名 `demo`，并注明应用连哪个库由环境变量/`.env` 决定 |

全量测试复验：316 用例全绿（数据源改为空占位后，单测因 `application-test.yml` 提供 H2 而不受影响）。

### 10.8 批次 5：修掉 IDE 启动缺 profile 的问题（来自实际运行配置排查）

核对本机 IDEA 运行配置（`.idea/workspace.xml`）发现：

| 观察 | 说明 |
|------|------|
| 4 个模块的运行配置都有 `envFilePaths` = `$PROJECT_DIR$/apps/<模块>/.env` | EnvFile 插件已正确接上，`.env` 以**环境变量**形式注入（优先级最高），模块级 `.env` 能被读到 |
| 运行配置里**没有** `SPRING_PROFILES_ACTIVE`，截图 "Active profiles" 也为空 | 而数据源与 Flyway 迁移只在 `jdbc` profile 的配置文件里 |

关键结论：**IDE 直接 Run 不读 Maven 的 `spring-boot.run.profiles`**——那个参数只对 `mvn spring-boot:run` 生效。因此先前"从 IDEA 启动"会在没有数据源的情况下失败，且报错是含糊的
`Failed to configure a DataSource: 'url' attribute is not specified ... you may need to activate it`，容易被误判成"库连不上"。

处置：在平台模块 `application.yml` 声明 `spring.profiles.default: jdbc`，IDE 与 `java -jar` 都自动生效（命令行 `spring-boot:run` 行为不变）。

| 验证 | 结果 |
|------|------|
| 不传 profile、不设任何数据源环境变量，仅靠模块 `.env` 从 jar 启动 | 自动激活 jdbc → 读取 `.env` 中的连接串 → `Successfully validated 4 migrations`、`Current version: 3`、8084 OPEN（等价于 IDEA 场景） |
| 显式关掉默认 profile（`-Dspring.profiles.default=none`） | 报 `Failed to configure a DataSource` + 提示激活 profile → 确认修复前就是该失败模式，且修复后消失 |
| 全量单测 | 316 用例全绿，仍走 `jdbc:h2:mem:platform_test`（profile 配置文件优先于 `application.yml`，未被空数据源占位覆盖） |

同时确认：`.env` 被 `.gitignore:47` 忽略（`git check-ignore -v` 验证），不会入库；本周文档中所有环境实例信息（库名、主机、账号、工作区与 Maven 路径）已改为占位符，避免另一环境拉代码时混淆。

## 11. 风险与下周输入

- **规范 5.6 信封不一致（已闭合）**：规范 5.6 早期草案写 `{"error":{...}}`（从未实现），实际自第 2 周起全仓为扁平信封。第 16 周已把规范 5.6 按实现校正，并同步修正 `docs/api/week-02-api.md`（其信封与错误码示例同为过时草案写法）。
- **共用库的迁移约束（真库已验证，仍需团队约定）**：本模块与 `spring-ai-demo` 共用同一个库，靠独立历史表 `flyway_schema_history_platform` 隔离版本号。若后续再有第三个模块入库，必须沿用同样做法（专属历史表 + `baseline-version: 0`），否则会出现 V1 相互跳过或版本冲突。
- **列表接口无上限保护**：`GET /executions` 与 `GET /audit-logs` 均全量返回，`audit_log` 会持续增长；Week 17+ 接可观测体系时应补分页与保留策略。
- **主键生成**：`execution_record.id` 用 `COALESCE(MAX(id),0)+1` 生成（为兼容 H2 与 PostgreSQL 未用 identity），写入量小无并发瓶颈；若后续并发写执行记录增多，应改为 identity/序列。
- **真库数据现状**：本环境 smoke 库中已存在本周创建的 7 张平台表与 4 条演示数据（3 seed 用户 + 1 条 FAILED 执行记录 + 4 条审计）。如需干净环境，按 `docs/api/week-16-api.md` 第 7 节重建即可（只清本模块对象，不影响同库其它应用）。
- **下周（Week 17 OpenTelemetry）输入**：`TraceIdFilter` 已建立 traceId 与 MDC 约定（且审计已真正落该 traceId），OTel 接入时应以该 traceId 作为跨系统关联键，避免双份链路 ID。

## 12. 决策记录 ADR

| 决策 | 选项 | 选择 | 理由 |
|------|------|------|------|
| 持久化开关 | 纯 `@ConditionalOnBean(DataSource)` / 显式属性 | 显式 `platform.persistence.mode` | 避免 H2 测试上下文让内存 Port 意外消失；行为确定可解释 |
| ORM | MyBatis / JPA / 裸 `JdbcTemplate` | 裸 `JdbcTemplate` | 本周仅 5 张表，P6 SaaS 前不引 ORM 复杂度 |
| `save` 语义 | 方言 upsert / 先查后写 | 先查后写（UPDATE 0 行则 INSERT） | 规避 PostgreSQL 与 H2 的 `ON CONFLICT`/`MERGE` 差异 |
| 数据源装配 | 默认 profile 内联 / 独立 `jdbc` profile 文件 | 独立 `application-jdbc.yml` | Spring Boot 3.4 禁止 `spring.profiles.default` 出现在非 profile 文档，内联方案直接启动失败 |
| 响应信封 | 规范草案嵌套 `error` / 全仓实际扁平信封 | 扁平信封 + `traceId`，并把规范 5.6 按实现校正 | 草案从未实现；第 2 周起四个模块均为 `{code,message,data}`。改信封属破坏性重构，风险高于收益 |
| 审计落点 | AOP 切面 / UseCase 显式调用 | UseCase 显式调用 | 事件语义明确、无代理魔法、可单测 |
| 审计故障处理 | 与主流程同事务失败 / 兜底告警 | try/catch + WARN | 审计不应成为登录/执行单点故障 |
| 执行记录外键 | 保留 FK / 去掉 FK 放宽测试 | 保留 FK，测试补用户行 | 数据完整性优先，不因测试便利放宽 schema |
| Agent/Tool 注册表 | 本周一并落库 / 保持内存 | 保持内存 | Week 13 遗留只列 RBAC + ExecutionRecord；平台配置落库留 Week 21 |
| **共用库迁移隔离** | 独立数据库 / 独立 schema / 同一库独立历史表 | 独立历史表 `flyway_schema_history_platform` | 不改动既有库配置；独立 schema 改动更大。独立历史表下 V1–V3 版本号可保持不变，且不污染同库其它应用的 `flyway_schema_history` |
| **Flyway 引导版本** | 默认 `baselineVersion=1` / 显式 `0` | 显式 `0` | 共用库 public 非空必须引导；但默认 1 会把本模块 V1 记为已应用而跳过（真库复现），取 0 才能让 V1–V3 全跑 |
| **长文本列类型** | `CLOB` / `TEXT` / 无长度 `VARCHAR` | 无长度 `VARCHAR` | `CLOB` 仅 H2 容忍、PostgreSQL 报 `type "clob" does not exist`；`TEXT` H2 不支持。无长度 `VARCHAR` 双库通用 |
| **审计 traceId 来源** | 调用方显式传入 / 从 MDC 读取 | 从 MDC 读取（`TraceIds.current()`） | 显式传参在真实调用链上被写成 `null`，导致审计无法与访问日志串联；MDC 是唯一可靠来源，并补了 4 个回归测试 |
