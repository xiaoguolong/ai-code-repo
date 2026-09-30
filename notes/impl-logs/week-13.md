# Week 13 实现日志 — 权限体系（RBAC / Agent / Tool / 数据域）

- 日期：2026-09-30
- 批次：1
- 对应 Spec：docs/specs/week-13.md

## 1. 本周目标

1. 8084 平台独立用户 + Sa-Token 登录（`/api/v1/platform/auth`）
2. Flat RBAC：Role → allowedAgentKeys / allowedToolKeys / allowedPatientIds
3. Platform Run **强 gate** + ExecutionRecord 按 userId 隔离
4. `AuthorizingToolPort` 装饰 `ToolRegistry`（有登录态时校验 Tool + patientId）
5. 直连 Run API **软 gate**（`platform.security.enforce-direct-runs=false` 默认）
6. Seed：admin / operator / viewer 三角色 + 三用户

## 2. 边界

- Always：内存 Port；Platform Run 强鉴权；中文 JavaDoc；TDD；Week 8–11 Graph 不改
- Never：改 ai-core ToolRegistry；改 enterprise；8084 PostgreSQL；Graph 编排

## 3. 增量架构

见 `docs/architecture/week-13-architecture.md`。

## 4. 新增类型清单

| 类型 | 名称 | 职责 |
|------|------|------|
| Model | PlatformUser / PlatformRole | 平台用户与角色授权 |
| Port | PlatformUserPort / PlatformRolePort | 用户/角色持久化 |
| Service | PlatformPermissionChecker | Agent/Tool/数据/execution 校验 |
| Security | AuthorizingToolPort / DirectRunAuthInterceptor | Tool 装饰器 + 直连软 gate |
| Config | PlatformSaTokenConfig / PlatformSecurityProperties | Sa-Token 拦截 + 开关 |
| UseCase | PlatformAuthUseCase / PlatformRoleUseCase | 登录与角色查询 |
| Controller | PlatformAuthController / PlatformRoleController | REST |
| Seed | PlatformRbacSeedInitializer | admin/operator/viewer |

## 5. RED

- 先写 PlatformPermissionCheckerTest、AuthorizingToolPortTest、PlatformAuthUseCaseTest、PlatformExecutionUseCaseTest（RBAC 场景）
- 编译失败（引用尚未实现的 RBAC 类）

## 6. GREEN

- 命令：`.\run-maven-jdk17.ps1 test`
- 结果：ai-core 69 / enterprise 21 / patient 19 / spring-ai-alibaba-agent **72**，`BUILD SUCCESS`（合计 **181**）
- 修复：WebMvcTest 禁用 `platform.security.web-interceptors.enabled=false`；`@Primary` 标记 `frameworkToolPort` 避免双 Bean

## 7. 质量门禁

- [x] 根聚合 test 全绿
- [x] Week 8–12 测试不回退
- [x] 分层未突破（装饰器在外层，不改 ToolRegistry）
- [x] 无密钥入库

## 8. 验证证据

```
Tests run: 72 (spring-ai-alibaba-agent)
BUILD SUCCESS (181 total)
```

Seed 账号：`admin/admin123`、`operator/operator123`、`viewer/viewer123`

## 9. 后续（显式补项，见 code-implementation-spec §7.4）

| 项 | 周次 |
|----|------|
| 8084 RBAC + ExecutionRecord PostgreSQL/Flyway | 第 16 周 |
| Prompt 过滤 / 脱敏 / Tool 内容白名单 | 第 14 周 |
| Gateway 统一鉴权 + SSO | 第 19 / 21 周 |
| 8082 patient-agent 收敛至 Platform | 第 21 周 |

## 10. ADR

| 决策 | 选择 | 理由 |
|------|------|------|
| 鉴权范围 | Platform 强 + 直连软 | Week 12 边界；最小侵入 |
| Tool 拦截 | AuthorizingToolPort 装饰器 | 不动 ai-core |
| 用户体系 | 8084 独立 | YAGNI；第 21 周 SSO |
| 持久化 | 内存 Port | 与 Week 12 一致 |
