# Week 12 实现日志 — Agent 平台 V1

- 日期：2026-09-30
- 批次：1
- 对应 Spec：docs/specs/week-12.md

## 1. 本周目标

1. Agent 注册与配置（`/api/v1/platform/agents`）
2. Tool 目录管理（`/api/v1/platform/tools`）
3. Workflow 目录管理（`/api/v1/platform/workflows`）
4. 执行记录 + Platform Run 调度（`/api/v1/platform/executions`、`/agents/{agentKey}/runs`）
5. 启动 seed：3 Agent + 2 Tool + 2 Workflow

## 2. 边界

- Always：端口 + 内存适配器；Platform Run 写 ExecutionRecord；Week 8–11 直连 API 不变
- Never：改 Graph 编排；PostgreSQL（留后续）；密钥入库

## 3. 增量架构

见 `docs/architecture/week-12-architecture.md`。

## 4. 新增类型清单

| 类型 | 名称 | 职责 |
|------|------|------|
| Port | AgentRegistryPort / ToolCatalogPort / WorkflowRegistryPort / ExecutionRecordPort | 平台持久化契约 |
| Adapter | InMemory*Adapter ×4 | Week 12 V1 内存实现 |
| Service | PlatformAgentRunner | agentType → Week 8–11 UseCase |
| UseCase | PlatformAgent/Tool/Workflow/Execution UseCase | 业务编排 |
| Controller | Platform*Controller ×4 | REST |
| Seed | PlatformSeedDataInitializer | 预置元数据 |

## 5. RED

- 先写 PlatformAgentUseCaseTest、PlatformExecutionUseCaseTest、PlatformAgentRunnerTest、PlatformAgentControllerTest
- 编译失败（引用尚未实现的 platform 包）

## 6. GREEN

- 命令：`.\run-maven-jdk17.ps1 test`
- 结果：ai-core 69 / enterprise 21 / patient 19 / spring-ai-alibaba-agent **57**，`BUILD SUCCESS`（合计 **166**）
- 修复：`FrameworkAgentResult` 字段为 `totalSteps` 非 `stepCount`

## 7. 质量门禁

- [x] 根聚合 test 全绿
- [x] Week 8–11 测试不回退
- [x] 分层未突破
- [x] 无密钥入库

## 8. 验证证据

```
Tests run: 57 (spring-ai-alibaba-agent)
BUILD SUCCESS
```

## 9. 后续

- PostgreSQL + Flyway 替换内存适配器
- Workflow checkpoint 落库（Week 10 MemorySaver）
- RBAC（Week 13）

## 10. ADR

| 决策 | 选择 | 理由 |
|------|------|------|
| 持久化 | 内存 Port | YAGNI；8084 原无 DB |
| 执行记录 | 仅 Platform Run | 最小侵入 |
