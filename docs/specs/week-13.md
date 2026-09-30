# Spec: Week 13 — 权限体系（RBAC / Agent / Tool / 数据域）

## Objective

在 `spring-ai-alibaba-agent`（8084）平台层实现 **RBAC 权限链**：用户 → 角色 → Agent → Tool → 数据（patientId）。Platform Run **强鉴权**；直连 Week 8–11 Run API **软 gate**（配置项，默认关闭）；工具执行经 `AuthorizingToolPort` 装饰 `ToolRegistry`。**不改** Graph/Workflow 编排、ai-core `ToolRegistry`、enterprise 模块。

## Tech Stack

- JDK 17、Spring Boot 3.4.5、Maven
- Sa-Token（`sa-token-spring-boot3-starter` 1.46.0），复用 ai-core `AuthTokenPort` / `PasswordHasher`
- 持久化：进程内内存 Port + seed（与 Week 12 同模式）
- 复用：Week 12 Platform / `ToolRegistry` / Week 8–11 运行时

## Commands

- 根聚合：`.\run-maven-jdk17.ps1 test`
- 单模块：`.\run-maven-jdk17.ps1 -pl apps/spring-ai-alibaba-agent -am test`
- 启动：`apps/spring-ai-alibaba-agent` 下 `.\run-maven-jdk17.ps1 spring-boot:run`（8084）

## 增量架构

见 `docs/architecture/week-13-architecture.md`。

## API（端口 8084）

| 资源 | Method | Path | 鉴权 |
|------|--------|------|------|
| Auth | POST | /api/v1/platform/auth/login | 公开 |
| Auth | POST | /api/v1/platform/auth/logout | 登录 |
| Auth | GET | /api/v1/platform/auth/me | 登录 |
| Role | GET | /api/v1/platform/roles | 登录 |
| Role | GET | /api/v1/platform/roles/{roleKey} | 登录 |
| Execution | POST | /api/v1/platform/agents/{agentKey}/runs | **强 gate** |
| Execution | GET | /api/v1/platform/executions | **强 gate**（按用户隔离） |
| Execution | GET | /api/v1/platform/executions/{id} | **强 gate**（按用户隔离） |

详见 `docs/api/week-13-api.md`。

## Seed

| 用户 | 密码 | 角色 | Agent | Tool | patientId |
|------|------|------|-------|------|-----------|
| admin | admin123 | admin | 全部 | 全部 | 全部 |
| operator | operator123 | operator | patient-risk, medical-assistant | 2 个 | P001–P010 |
| viewer | viewer123 | viewer | 无 Run | 无 | 无 |

## Boundaries

- **Always**：Platform Run + executions 强登录；`AuthorizingToolPort` 在有登录态时校验 Tool/数据；ExecutionRecord 带 userId；中文 JavaDoc；TDD
- **Ask first**：8084 PostgreSQL；enterprise ↔ platform SSO；直连 API 默认强制鉴权
- **Never**：改 Graph/Workflow 编排；改 ai-core `ToolRegistry`；改 enterprise；密钥入库

## Success Criteria

- [ ] 未登录 Platform Run → 401
- [ ] viewer Run Agent → 403；operator 越权 Agent → 403
- [ ] operator 越权 patientId（如 P999）经 Tool → 403
- [ ] admin 可见全部 executions；operator 仅自己的
- [ ] `platform.security.enforce-direct-runs=false` 时直连 API 行为与 Week 8–11 一致
- [ ] 根聚合 test 全绿

## 后续周补项（Week 13 刻意不做）

| 项 | 补位周次 |
|----|----------|
| 8084 RBAC + ExecutionRecord PostgreSQL/Flyway | **第 16 周** |
| Prompt 过滤 / 输出脱敏 / Tool 内容白名单 | **第 14 周** |
| Gateway 统一鉴权、enterprise+platform SSO | **第 21 周** |
| 8082 patient-agent 收敛至 Platform | **第 21 周** |

## ADR

| 决策 | 选项 | 选择 | 理由 |
|------|------|------|------|
| 鉴权范围 | 全 API / Platform 强 + 直连软 | Platform 强 + 直连软 | Week 12 边界；最小侵入 |
| Tool 拦截 | 改 ToolRegistry / 装饰器 | AuthorizingToolPort | 不动 ai-core；patient-agent 不受影响 |
| 用户体系 | 与 enterprise 合并 / 独立 | 8084 独立用户 | YAGNI；第 21 周再 SSO |
| 持久化 | PostgreSQL / 内存 | 内存 Port | 与 Week 12 一致 |
