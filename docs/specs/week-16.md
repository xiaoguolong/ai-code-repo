# Spec: Week 16 — 企业规范（API / 日志 / 审计 / 异常 + RBAC 落库）

## Objective

把 Week 8–15 长出来的平台（8084）从「演示可用」推到「企业可上线」：**一套可执行的企业规范**（统一响应信封与错误码、统一 traceId 与结构化日志、落库审计、异常不外泄）＋ **Week 13 遗留的 RBAC / ExecutionRecord PostgreSQL 持久化（Flyway）**，让规范与持久化同周收口。

## Tech Stack

| 项 | 本周启用 |
|----|----------|
| JDK 17、Spring Boot 3.4.5、Maven | 复用 |
| `spring-boot-starter-jdbc`（8084 新增） | 本周 |
| Flyway `flyway-core` + `flyway-database-postgresql` | 本周 |
| PostgreSQL（生产）/ H2 `MODE=PostgreSQL`（单测） | 本周 |
| Sa-Token、Spring AI Alibaba、ai-core | 复用，不改 |

明确不做（规范 3.4：P4 治理，未到 P5）：OpenTelemetry / Langfuse / SkyWalking / Prometheus、Gateway。

## Commands

- 不改 `JAVA_HOME`；JDK17：`-Djdk.17.home=E:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot`
- Maven：`-s <maven-home>\conf\settings-ailocal.xml`，本地仓库由该 settings 指定（仓库内 `.mvn/maven.config` 已配）
- 根聚合测试：`.\run-maven-jdk17.ps1 test`
- 单模块测试：`.\run-maven-jdk17.ps1 -pl apps/spring-ai-alibaba-agent -am test`
- 启动（8084）：`apps/spring-ai-alibaba-agent` 下 `.\run-maven-jdk17.ps1 spring-boot:run`
- Postman：导入 `docs/postman/week-16.postman_collection.json`

## 增量架构

见 `docs/architecture/week-16-architecture.md`（只画本周新增 / 改动节点）。

## 一、持久化：RBAC + ExecutionRecord（Week 13 遗留）

原则：**Port 不动，只换适配器**。`PlatformUserPort` / `PlatformRolePort` / `ExecutionRecordPort` 契约保持原样，周内不触碰 UseCase 业务规则。

| Port | Week 13 适配器 | 本周适配器 | 选择开关 |
|------|----------------|------------|----------|
| `PlatformUserPort` | `InMemoryPlatformUserAdapter` | `JdbcPlatformUserAdapter` | `platform.persistence.mode` |
| `PlatformRolePort` | `InMemoryPlatformRoleAdapter` | `JdbcPlatformRoleAdapter` | `platform.persistence.mode` |
| `ExecutionRecordPort` | `InMemoryExecutionRecordAdapter` | `JdbcExecutionRecordAdapter` | `platform.persistence.mode` |
| `AuditLogPort`（本周新增） | `InMemoryAuditLogAdapter` | `JdbcAuditLogAdapter` | `platform.persistence.mode` |

- `platform.persistence.mode=jdbc`（默认）：四处 JDBC 适配器生效，需 `DataSource`。
- `platform.persistence.mode=memory`：四处内存适配器生效，不需要数据库（无库单测 / 快速演示）。
- 两组实现互斥，禁止同时注册同一 Port。

### Flyway 迁移（`apps/spring-ai-alibaba-agent/src/main/resources/db/migration`）

| 脚本 | 表 | 说明 |
|------|----|------|
| `V1__platform_rbac.sql` | `platform_user` / `platform_role` / `platform_role_agent` / `platform_role_tool` / `platform_role_patient` | 用户、角色、Agent/Tool/数据域三类授权子表 |
| `V2__platform_execution_record.sql` | `execution_record` | 执行记录，外键指向 `platform_user`，含 token 与状态 |
| `V3__platform_audit_log.sql` | `audit_log` | 审计日志（谁 / 何时 / 对什么 / 做了什么 / 结果） |

约束：脚本必须同时可在 **PostgreSQL** 与 **H2（`MODE=PostgreSQL`）** 上执行；不使用 `CREATE EXTENSION`、`JSONB`、`CLOB`、部分索引等单边特性（`CLOB` 仅 H2 容忍，PostgreSQL 会直接报类型不存在）。长文本用无长度 `VARCHAR`。

迁移历史与引导（真库验证结论）：

| 配置 | 值 | 原因 |
|------|----|------|
| 历史表 | `flyway_schema_history_platform` | 本模块可能与其它应用共用同一个库（本仓库 smoke 环境即如此，该库已被 `spring-ai-demo` 占用 V1–V6）；共用 `flyway_schema_history` 会导致两套 V1 相互跳过或冲突 |
| `baseline-on-migrate` | `true` | 共用库 public 非空，禁用会直接报 `Found non-empty schema(s) but no schema history table` |
| `baseline-version` | `0` | 默认值 1 会把本模块 V1 记为已应用而跳过，`platform_*` 表一张都不建（真库已复现） |

## 二、审计规范（Audit）

- 新增领域端口 `AuditLogPort`（平台域），模型 `AuditLogEntry` / `AuditAction` / `AuditResult`。
- 审计事件（本周落地 4 类）：
  | action | 触发点 | resource | result |
  |--------|--------|----------|--------|
  | `LOGIN` | `PlatformAuthUseCase.login` 成功 | `platform.auth` | `SUCCESS` |
  | `LOGIN_FAILED` | `PlatformAuthUseCase.login` 凭据错误 | `platform.auth` | `FAILURE` |
  | `AGENT_RUN` | `PlatformExecutionUseCase.runAgent` 终态 | `agent:{agentKey}` | `SUCCESS` / `FAILURE` |
  | `EXECUTION_READ` | 查询他人执行记录被拒 | `execution:{id}` | `DENIED` |
- 审计字段：`auditId`、`userId`、`action`、`resource`、`result`、`traceId`、`detail`、`createdAt`。
- 审计**只记标识与结果**，禁止写入密码、token、API Key、完整 Prompt 正文；`detail` 由调用方传入已脱敏文本。
- 审计写失败**不得影响主流程**：`AuditLogPort.record` 由调用方 try/catch + WARN 日志兜底。

## 三、日志规范（Log）

- **traceId 全链路**：`TraceIdFilter`（`OncePerRequestFilter`，最高优先级）从请求头 `X-Trace-Id` 取，缺失则生成 UUID；写入 `MDC.traceId`，响应头回写 `X-Trace-Id`，`finally` 清理 MDC。
- **日志格式**：`logging.pattern.console` 含 `[%X{traceId:-}]`，控制台可 grep traceId。
- **请求访问日志**：`ApiAccessLogFilter` 输出 `[api] method= path= status= durationMs= userId=`，业务标识使用固定 `key=value` 形式（结构化、可 grep）。
- **敏感信息**：禁止打印密码、token、API Key、完整身份证 / 银行卡；复用 ai-core `PiiMasker`（Week 14）做输出脱敏。

## 四、异常规范（Exception）

- 错误码集中到 `ApiErrorCode` 枚举（框架层 `com.aicode.framework.dto`），`GlobalExceptionHandler` 禁止再出现字符串字面量错误码。
- 错误码命名统一 `UPPER_SNAKE_CASE`；HTTP 状态按语义：400 参数 / 401 未登录 / 403 越权 / 404 不存在 / 409 冲突 / 502 上游模型或工具 / 500 未预期。
- `ApiResponse` 增加 `traceId` 字段（成功与失败都带），失败响应 `data=null`。
- 对外错误信息不含堆栈、不含厂商原始报文、不含密钥；未预期异常统一 `INTERNAL_ERROR` + 通用文案，堆栈只进服务端日志。
- 每个错误码有中文 JavaDoc，说明触发条件与 HTTP 状态。

## 五、API 规范（API）

- 路径：`/api/v1/{domain}/...`；资源名复数小写中划线；不在路径出现动词（动作型除外：`/runs`、`/login`）。
- 响应信封：`{"code":"SUCCESS","message":"OK","data":{...},"traceId":"..."}`；失败 `data=null`。
- 分页约定：列表接口预留 `?page=&size=`，本周不新增分页接口，只在文档中固化口径。
- 契约测试保证：所有失败响应 `code` ∈ `ApiErrorCode`、必带 `traceId`、不含 `Exception` / `SQLException` / `at com.` 字样。
- 完整口径见 `docs/api/week-16-api.md`。

## Boundaries

- **Always**：Port 契约不变只换适配器；迁移脚本双库可跑；审计与 traceId 有单测；中文 JavaDoc；TDD；无密钥入库
- **Ask first**：Agent/Tool/Workflow 注册表落库（Week 13 未列遗留）；网关统一鉴权；enterprise 8083 改动；LLM 相关 API 变更
- **Never**：改 Graph / Workflow / Multi-Agent 编排；改 RBAC 业务规则与 Guardrail 逻辑；引入 OTel / Langfuse / SkyWalking；把 `ApiResponse` 改成嵌套 `{"error":{...}}` 破坏全仓既有信封

## Success Criteria

- [x] `platform.persistence.mode=jdbc` + H2 时，`PlatformUserPort` / `PlatformRolePort` / `ExecutionRecordPort` / `AuditLogPort` 装配的是 JDBC 适配器
- [x] `JdbcPlatformRoleAdapter` 可正确读写 Agent / Tool / patientId 三类授权集合
- [x] `JdbcExecutionRecordAdapter` 支持 RUNNING → COMPLETED 的状态覆盖与按用户过滤
- [x] 三份 Flyway 迁移在 H2（`MODE=PostgreSQL`）上随应用上下文启动成功，seed 幂等（重复启动不重复插入）
- [x] 登录成功 / 失败各写一条审计；越权读执行记录写 `DENIED` 审计
- [x] 任意失败响应含非空 `traceId`，且响应头 `X-Trace-Id` 与响应体一致
- [x] 审计记录的 `traceId` 取自当前请求 MDC，与访问日志/响应头一致（真库已发现并修复「审计 traceId 为空」）
- [x] `GlobalExceptionHandler` 无字符串字面量错误码，错误码全部来自 `ApiErrorCode`
- [x] 根聚合 `test` 全绿，Week 8–15 测试不回退（312 → 316 用例）
- [x] **真实 PostgreSQL 16 上 V1–V3 全部应用**（历史表为 `v0 BASELINE + v1 + v2 + v3`），重启只校验不重跑，seed 幂等
- [x] 落库模式可直接启动：`.env` 由 `spring.config.import` 自动加载，无需手工导出环境变量（真库验证）

## 本周收尾审计（未完成 / 已延期项）

| 项 | 状态 | 说明 |
|----|------|------|
| 手工可执行启动说明 | 已补 | `application-jdbc.yml` 增加 `spring.config.import: optional:file:.env[.properties]`；原先文档写的 `spring-boot:run` 在真库环境实际跑不通（.env 不会被自动加载） |
| public 类型 JavaDoc | 已补 | Week 15 遗留的 `EvalCaseSummary` / `EvalCaseResultResponse` 两个内部 record 补上 JavaDoc |
| 规范 5.6 响应信封不一致 | **已闭合** | 规范 5.6 早期草案写 `{"error":{...}}`（从未实现），实际全仓自第 2 周起为扁平信封。已在第 16 周把规范 5.6 按实现校正，并同步修正 `docs/api/week-02-api.md` 的信封与错误码示例（错误码为大写 `VALIDATION_ERROR` 等，非草案的 `validation_error`） |
| Agent / Tool / Workflow 注册表落库 | 延期 Week 21 | Week 13 遗留只列 RBAC + ExecutionRecord；平台配置落库随 SaaS 整合 |
| `GET /audit-logs` 分页与保留策略 | 延期 Week 17+ | 当前全量返回，`audit_log` 持续增长 |
| `execution_record.id` 改用序列/identity | 延期 | 现为 `COALESCE(MAX(id),0)+1`，写入量小无并发瓶颈 |
| 全量构建下跑测试 | 未做 | 本周为节约时间用 `-pl apps/spring-ai-alibaba-agent -am`；仅在收尾时跑过一次根聚合全量（通过） |
| Redis 集成 | 本周不涉及 | 第 16 周范围不含缓存/对话记忆；仅确认 6379 可达，未写入 |
| `listExecutions` / `listAuditLogs` 无上限保护 | 未做 | 大结果集可能拖慢响应，接可观测体系时一并处理 |

## Open Questions

无。

## ADR

| 决策 | 选项 | 选择 | 理由 |
|------|------|------|------|
| 持久化开关 | 纯 `@ConditionalOnBean(DataSource)` / 显式属性 | 显式 `platform.persistence.mode` | 避免 H2 测试上下文让内存 Port 意外消失；行为确定、可解释 |
| ORM | MyBatis-Plus / Spring Data JPA / 裸 `JdbcTemplate` | 裸 `JdbcTemplate` | 本周只 5 张表；P6 SaaS 前不引入 ORM 复杂度（YAGNI） |
| `save` 语义 | 数据库方言 upsert / 先查后写 | 先查后写（insert / update 分支） | 双库可移植；避免 PostgreSQL 与 H2 的 `ON CONFLICT` 差异 |
| 响应信封 | 规范 5.6 早期草案 `{"error":{...}}` / 全仓实际扁平信封 | 扁平信封 + 新增 `traceId`，并把规范 5.6 按实现校正 | 草案从未实现；第 2 周起四个模块均为 `{code,message,data}`。改信封要动 Week 8–15 全部 Controller 与测试，属破坏性重构，收益低于风险 |
| 审计落点 | UseCase 内直接调用 / AOP 切面 | UseCase 内直接调用 | 事件语义明确、无代理魔法；4 个事件点显式可测 |
| Agent/Tool 注册表 | 本周一并落库 / 保持内存 | 保持内存 | Week 13 遗留只列 RBAC + ExecutionRecord；平台配置落库留 Week 21 |
| 时间列类型 | `TIMESTAMP` / `TIMESTAMP WITH TIME ZONE` | `TIMESTAMP WITH TIME ZONE` | 与 enterprise 既有迁移一致；避免跨时区歧义 |
| 共用库迁移隔离 | 独立数据库 / 独立 schema / 同库独立历史表 | 同库独立历史表 | 沿用现有库配置；独立历史表下 V1–V3 版本号保持不变，且不污染既有应用的迁移记录 |
| Flyway 引导版本 | 默认 `1` / 显式 `0` | 显式 `0` | 共用库 public 非空必须引导，但默认 1 会跳过本模块 V1（真库复现） |
| 长文本列类型 | `CLOB` / `TEXT` / 无长度 `VARCHAR` | 无长度 `VARCHAR` | `CLOB` 仅 H2 容忍、PostgreSQL 报错；`TEXT` H2 不支持 |
| 审计 traceId 来源 | 调用方显式传参 / 从 MDC 读取 | 从 MDC 读取 | 显式传参在真实调用链上被写成 `null`，审计无法与访问日志串联（真库发现） |
