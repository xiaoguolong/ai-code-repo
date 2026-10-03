# Week 16 API 规范 — 平台企业规范（API / 日志 / 审计 / 异常）

> 端口 **8084**，基址 `http://localhost:8084`，路径前缀 `/api/v1`  
> 配套：[docs/specs/week-16.md](../specs/week-16.md)、[docs/architecture/week-16-architecture.md](../architecture/week-16-architecture.md)

本文是第 8 周起平台接口的**统一口径**，也是第 17–24 周新接口的验收依据。

---

## 1. 通用约定

| 项 | 规定 |
|----|------|
| 协议 | HTTP/1.1 + JSON（`Content-Type: application/json; charset=UTF-8`） |
| 版本 | 路径内版本号：`/api/v1/...`；破坏性变更升 `/api/v2`，禁止原地改语义 |
| 命名 | 资源名小写复数、中划线分词（`audit-logs`、`knowledge-bases`）；路径不出现动词，动作型端点用子资源（`/runs`、`/login`、`/resume`） |
| 鉴权 | Sa-Token，请求头 `satoken: <token>`；未登录 401 |
| 追踪 | 请求/响应头 `X-Trace-Id`；未传则服务端生成 UUID 并在响应头回写 |
| 时间 | 全部 UTC，ISO-8601（`2026-10-01T02:00:00Z`） |
| 分页 | 列表接口预留 `?page=0&size=20`（page 从 0 起）；本周未新增分页接口，新增时必须同时返回 `page/size/total` |
| 幂等 | 写接口暂不要求幂等键；重复注册类冲突返回 409 |

### 1.1 成功响应信封

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": { },
  "traceId": "6f1c0f2e-9b3a-4a6f-8a1d-2c9a1f0b7d55"
}
```

### 1.2 失败响应信封

```json
{
  "code": "VALIDATION_ERROR",
  "message": "message must not be blank",
  "data": null,
  "traceId": "6f1c0f2e-9b3a-4a6f-8a1d-2c9a1f0b7d55"
}
```

规则：

1. 失败时 `data` 恒为 `null`（不是 `{}`、不是省略）。
2. `traceId` 恒非空，且与响应头 `X-Trace-Id` 一致。
3. `message` 不得包含堆栈、`Exception` 类名、SQL 语句、厂商原始报文、密钥。
4. `code` 必须来自下表错误码枚举，禁止临时字符串。

> **信封口径**：本扁平信封（`code` / `message` / `data` / `traceId`）自第 2 周起即为全仓实现口径，
> `docs/code-implementation-spec.md` 5.6 已同步校正，不再存在"规范与实现偏离"。
> 相对第 8–15 周的唯一增量是新增 `traceId` 字段（`ApiResponse` 扩展，不改变既有字段语义，属兼容变更）。

---

## 2. 错误码（`ApiErrorCode`，唯一来源）

| code | HTTP | 触发条件 |
|------|------|----------|
| `VALIDATION_ERROR` | 400 | Bean Validation 失败、用例层入参非法 |
| `UNAUTHORIZED` | 401 | 未登录、登录态过期、用户名或密码错误 |
| `FORBIDDEN` | 403 | RBAC 越权：Agent / Tool / patientId 数据域 / 执行记录非本人 |
| `GUARDRAIL_VIOLATION` | 400 | 内容安全护栏拦截（Prompt 注入、超长、敏感） |
| `PLATFORM_NOT_FOUND` | 404 | 平台资源不存在（Agent、执行记录、角色） |
| `PLATFORM_CONFLICT` | 409 | 平台资源状态冲突（重复注册） |
| `AGENT_DISABLED` | 400 | Agent 已禁用 |
| `CHAT_MODEL_ERROR` | 502 | 模型调用失败（超时、限流、报文异常） |
| `TOOL_EXECUTION_ERROR` | 502 | 工具执行失败 |
| `AGENT_LOOP_EXCEEDED` | 500 | Agent 超出限定步数 |
| `AGENT_EXECUTION_ERROR` | 500 | Agent 执行异常 |
| `WORKFLOW_NOT_FOUND` | 404 | Workflow 实例不存在 |
| `WORKFLOW_NOT_PENDING` | 409 | Workflow 不处于待审批状态 |
| `INTERNAL_ERROR` | 500 | 未预期异常，对外仅通用文案 |

成功码固定为 `SUCCESS`，不属于错误码枚举。

---

## 3. 本周接口

### 3.1 认证

| Method | Path | 鉴权 | 说明 |
|--------|------|------|------|
| POST | `/api/v1/platform/auth/login` | 公开 | 登录，返回 token；成功写 `LOGIN` 审计，失败写 `LOGIN_FAILED` |
| POST | `/api/v1/platform/auth/logout` | 登录 | 注销 |
| GET | `/api/v1/platform/auth/me` | 登录 | 当前用户 |

`POST /api/v1/platform/auth/login`

```json
{ "username": "operator", "password": "operator123" }
```

200：

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": { "token": "…", "userId": 2, "username": "operator", "roleKey": "operator" },
  "traceId": "…"
}
```

401（`UNAUTHORIZED`）：用户名不存在或密码错误，**不区分**两种原因。

### 3.2 审计日志（本周新增）

| Method | Path | 鉴权 | 说明 |
|--------|------|------|------|
| GET | `/api/v1/platform/audit-logs` | 登录 | admin 返回全部；非 admin 仅返回本人；按 `createdAt` 倒序 |

200：

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": [
    {
      "userId": 2,
      "action": "AGENT_RUN",
      "resource": "agent:medical-assistant",
      "result": "SUCCESS",
      "traceId": "…",
      "detail": "executionId=3f2b…",
      "createdAt": "2026-10-01T02:00:07Z"
    }
  ],
  "traceId": "…"
}
```

未登录：401 `UNAUTHORIZED`。

### 3.3 RBAC 与执行记录（Week 13 既有，本周改为落库）

| Method | Path | 鉴权 | 说明 |
|--------|------|------|------|
| GET | `/api/v1/platform/roles` | 登录 | 角色列表 |
| GET | `/api/v1/platform/roles/{roleKey}` | 登录 | 角色详情（含 Agent / Tool / patientId 授权集合） |
| POST | `/api/v1/platform/agents/{agentKey}/runs` | 登录 + Agent 权限 | 调度 Agent；终态写 `AGENT_RUN` 审计 |
| GET | `/api/v1/platform/executions` | 登录 | admin 全部，其余仅本人 |
| GET | `/api/v1/platform/executions/{executionId}` | 登录 | 非本人且非 admin → 403 `FORBIDDEN`，并写 `EXECUTION_READ/DENIED` 审计 |

行为与 Week 13 一致，仅存储从进程内换为 PostgreSQL（`PlatformUserPort` / `PlatformRolePort` / `ExecutionRecordPort` 契约不变）。

---

## 4. 审计事件规范

| action | 触发点 | resource | result |
|--------|--------|----------|--------|
| `LOGIN` | 登录成功 | `platform.auth` | `SUCCESS` |
| `LOGIN_FAILED` | 登录失败（用户不存在或密码错误） | `platform.auth` | `FAILURE` |
| `AGENT_RUN` | 平台调度 Agent 到达终态 | `agent:{agentKey}` | `SUCCESS` / `FAILURE` |
| `EXECUTION_READ` | 读取他人执行记录被拒 | `execution:{executionId}` | `DENIED` |

约束：

- 字段为 `userId / action / resource / result / traceId / detail / createdAt`；`userId` 未知（登录失败）记 0。
- **禁止**写入密码、token、API Key、完整 Prompt 或患者隐私正文；`detail` 只放标识与错误摘要，超 512 字符截断。
- 审计写失败不得影响主流程（调用方 try/catch + WARN 日志）。
- 新增 action 必须同步本表、`AuditAction` 枚举与测试。

---

## 5. 日志规范

1. **traceId 全链路**：`TraceIdFilter`（最高优先级）读 `X-Trace-Id` 或生成 UUID → 写 MDC → 回写响应头 → 请求结束清理。
2. **日志格式**（控制台）：`%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%X{traceId:-}] %logger{36} - %msg`，可 `grep` traceId 串起一次请求全部日志。
3. **访问日志**：`ApiAccessLogFilter` 输出固定 key=value，仅记方法、路径、状态、耗时、traceId，不记请求/响应正文：

   ```text
   [api] method=POST path=/api/v1/platform/agents/medical-assistant/runs status=200 durationMs=42 traceId=6f1c0f2e…
   ```

4. **业务日志**：沿用既有前缀 `[platform]` / `[framework-agent]` / `[multi-agent]` / `[workflow]`，同样 key=value 结构化。
5. **禁止**打印密码、token、API Key、身份证/银行卡全量；对外输出走 ai-core `PiiMasker`（Week 14）。

---

## 6. 异常规范

| 要求 | 落地方式 |
|------|----------|
| 错误码集中 | `ApiErrorCode` 枚举，`GlobalExceptionHandler` 不再出现字符串字面量 |
| HTTP 语义 | 状态码取自枚举，禁止一律 200 |
| 不外泄内部细节 | 未预期异常统一 `INTERNAL_ERROR` + 通用文案，堆栈只进服务端 ERROR 日志 |
| 必带 traceId | `ApiResponse` 增加 `traceId`；`GlobalExceptionHandler` 与 `ApiResponseTraceAdvice` 统一填充 |
| 可回归 | `ApiStandardsContractTest` 断言 code 属于枚举、traceId 非空且与响应头一致、响应体不含堆栈/SQL/密钥特征 |

---

## 7. 持久化与迁移

| 项 | 值 |
|----|----|
| 模式开关 | `platform.persistence.mode`：`jdbc`（默认，落库）/ `memory`（进程内，无库演示） |
| 建库配置 | profile `jdbc`（`application-jdbc.yml`）。**数据源不设默认值**，由环境变量或模块 `.env` 提供；库名/账号各环境不同，禁止写进 yml |
| 迁移 | Flyway `classpath:db/migration`，`V1__platform_rbac.sql`、`V2__platform_execution_record.sql`、`V3__platform_audit_log.sql` |
| 迁移历史表 | `flyway_schema_history_platform`（本模块专属），**不要**与其它应用共用 `flyway_schema_history` |
| 迁移引导 | `baseline-on-migrate: true` + `baseline-version: 0`；见下方「共用库注意事项」 |
| 双库兼容 | 脚本不使用 `CREATE EXTENSION` / `JSONB` / `CLOB` / 部分索引；长文本用无长度 `VARCHAR`；单测用 H2 `MODE=PostgreSQL` 跑同一套脚本，真库为 PostgreSQL 16 |
| 启动（落库） | **IDEA**：运行配置用 EnvFile 插件加载 `$PROJECT_DIR$/apps/spring-ai-alibaba-agent/.env`（插件把文件内容作为环境变量注入，优先级最高）；profile 由 `application.yml` 的 `spring.profiles.default: jdbc` 兜底，**不需要**在 "Active profiles" 里填（IDE 不读 Maven 的 `spring-boot.run.profiles`）。**命令行**：模块目录下 `.\run-maven-jdk17.ps1 spring-boot:run`。**jar**：`java -jar ...`（默认 profile 同样生效）。环境变量优先级高于 `.env` |
| 启动（无库） | `$env:PLATFORM_PERSISTENCE_MODE="memory"` 后直接启动，并使用内存适配器 |

### 共用库注意事项（真库验证结论）

本模块可能与其它应用**共用同一个数据库**（由各环境自己的 `.env` 指定；本仓库 smoke 环境即共用一个库，其中已被 `spring-ai-demo` 占用 Flyway V1–V6）。此时：

1. 必须使用专属历史表 `flyway_schema_history_platform`，否则两套 V1 会相互跳过或版本冲突。
2. 必须 `baseline-on-migrate: true`：共用库的 public 模式非空，禁用它 Flyway 会直接报
   `Found non-empty schema(s) "public" but no schema history table` 并启动失败。
3. 必须 `baseline-version: 0`：默认值 1 会把本模块的 V1 记为「已应用」而跳过，
   结果是 `platform_*` 表一张都不建（真库曾复现，表现为历史表只有一条 `v1 BASELINE`）。
   正确结果是历史表为 `v0 BASELINE` + `v1/v2/v3` 三条 SQL 记录。
4. 新增第三个入库模块时沿用同样三条约定。

### 往已有环境的库上首次启动（pull 新代码后必查）

各环境库中已有前 15 周的对象，本模块首次启动前先确认表名不冲突，启动后按表核对：

```sql
-- 1) 启动前：确认本模块表名在库中空闲（避免与既有对象重名）
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'public' AND table_name IN (
  'platform_user','platform_role','platform_role_agent','platform_role_tool',
  'platform_role_patient','execution_record','audit_log','flyway_schema_history_platform');
-- 期望：无输出（全新环境）或只有本模块上次执行留下的对象

-- 2) 启动后：确认版本没被跳过
SELECT installed_rank, version, description, type, success
FROM flyway_schema_history_platform ORDER BY installed_rank;
-- 期望：v0 BASELINE + v1 + v2 + v3 共 4 行；若只有一条 "v1 BASELINE" 说明 V1 被跳过

-- 3) 启动后：确认既有应用的迁移记录未被污染（行数应与升级前一致）
SELECT COUNT(*) FROM flyway_schema_history;

-- 4) 确认 seed 幂等与密码为哈希
SELECT COUNT(*) FROM platform_user;                       -- 期望 3
SELECT username, password_hash LIKE '%admin123%' AS leaked FROM platform_user;  -- leaked 应全为 false
```

判定标准：四条查询全部符合期望即为升级成功。任一不符按 `docs/code-implementation-spec.md` 3.7 排查，**不要**用 `DROP` 既有应用的表来"修"问题。

重建演示环境（只清本模块对象，不影响同库其它应用的表）：

```sql
DROP TABLE IF EXISTS flyway_schema_history_platform, platform_user, platform_role,
    platform_role_agent, platform_role_tool, platform_role_patient,
    execution_record, audit_log CASCADE;
-- 随后重启应用，Flyway 会重新建表并 seed
```


表结构：

```text
platform_user(id PK, username UNIQUE, password_hash, role_key, created_at)
platform_role(role_key PK, display_name, is_admin)
platform_role_agent(role_key, agent_key)      -- PK(role_key, agent_key)
platform_role_tool(role_key, tool_key)        -- PK(role_key, tool_key)
platform_role_patient(role_key, patient_id)   -- PK(role_key, patient_id)
execution_record(id PK, execution_id UNIQUE, user_id FK→platform_user.id, agent_key,
                 agent_type, status, input_json, output_json, model,
                 prompt_tokens, completion_tokens, total_tokens, error_message,
                 started_at, finished_at)
audit_log(id PK, user_id, action, resource, result, trace_id, detail, created_at)
```

Seed（幂等，重复启动不重复插入）：

| 用户 | 密码 | 角色 | Agent | Tool | patientId |
|------|------|------|-------|------|-----------|
| admin | admin123 | admin | 全部 | 全部 | 全部 |
| operator | operator123 | operator | patient-risk, medical-assistant | PatientLookupTool, HealthMetricTool | P001–P010 |
| viewer | viewer123 | viewer | 无 | 无 | 无 |

密码只存哈希（`PasswordHasher`，含 `auth.password-salt`），明文不落库、不进日志。

---

## 8. 契约测试索引

| 测试 | 覆盖 |
|------|------|
| `ApiErrorCodeContractTest` | 命名 UPPER_SNAKE、唯一、中文文案、HTTP 状态语义、必需错误码齐备 |
| `ApiStandardsContractTest` | 失败 code 属枚举、traceId 非空且与响应头一致、无堆栈/SQL/密钥泄漏 |
| `PlatformAuthAuditTest` | 登录成功/失败审计、不泄漏密码、审计故障不阻断登录 |
| `PlatformExecutionAuditTest` | Run 终态审计、越权读 `DENIED`、鉴权失败不写噪声审计 |
| `PlatformAuditUseCaseTest` | admin 全量 / 非 admin 仅本人 |
| `PlatformAuditSecurityTest` | 未登录 401 且带 traceId |
| `JdbcPlatformUserAdapterTest` / `JdbcPlatformRoleAdapterTest` / `JdbcExecutionRecordAdapterTest` / `JdbcAuditLogAdapterTest` | 四类落库适配器读写与双库兼容 |
| `PlatformSeedIdempotencyTest` | seed 幂等、密码哈希、授权集合、注册表不重复 |
