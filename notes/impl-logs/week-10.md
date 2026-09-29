# Week 10 实现日志 — Human In The Loop（高风险人工审核 → 暂停 → 恢复）

- 日期：2026-09-29
- 批次：1
- 对应 Spec：docs/specs/week-10.md
- 对应阅读：docs/specs/week-01~09.md、notes/week-01-llm-basics.md（补读）

## 1. 本周目标

在第 9 周 `PatientRiskWorkflow` 上接入 **Human-in-the-Loop**：

1. HIGH 风险在 `human_review` 节点前 `interruptBefore` 暂停，返回 `PENDING_APPROVAL`
2. `POST /runs/{workflowId}/resume` 人工审核后继续（`escalate → generate_report`）或拒绝（`REJECTED`）
3. `GET /runs/{workflowId}` 查询 checkpoint 状态
4. 中/低风险路径与第 9 周行为一致（同步 `COMPLETED`）

## 2. 边界

- Always：`MemorySaver` + `threadId=workflowId`；仅 HIGH 触发 HITL；工具/模型仍经端口；中文 JavaDoc；单测 fake 端口；无密钥入库
- Never：改 `FrameworkAgentGraph`；抽 ai-core `WorkflowPort`；低风险强制人工审核；改 demo 工具行为

## 3. 增量架构

见 Spec `docs/specs/week-10.md` 与 `docs/architecture/week-10-architecture.md`。

```
judge_risk →(urgent)→ human_review ⏸ →(approved)→ escalate → generate_report
                      └→(rejected)→ END
```

## 4. 新增/变更类型清单

| 类型 | 名称 | 职责 |
|------|------|------|
| 枚举 | `WorkflowStatus` | COMPLETED / PENDING_APPROVAL / REJECTED |
| 异常 | `WorkflowNotFoundException` / `WorkflowNotPendingException` | 404 / 409 领域异常 |
| 领域服务 | `PatientRiskWorkflow`（改） | `MemorySaver` + `interruptBefore`；`start`/`resume`/`getRun`；新增 `human_review` 节点 |
| 模型 | `PatientRiskWorkflowResult`（改） | 增加 `status` 字段 |
| 用例 | `PatientRiskUseCase`（改） | `resume` / `getRun` |
| 控制器 | `PatientRiskWorkflowController`（改） | GET /runs/{id}、POST /runs/{id}/resume |
| DTO | `PatientRiskResumeRequest`；`PatientRiskRunResponse`（+status） | 协议体 |
| 异常处理 | `GlobalExceptionHandler`（改） | WORKFLOW_NOT_FOUND / WORKFLOW_NOT_PENDING |

## 5. RED

- 命令：`.\run-maven-jdk17.ps1 -pl apps/spring-ai-alibaba-agent -am test`
- 结果：编译失败（预期）
- 原因：先写 HITL 测试（`PatientRiskWorkflowTest` 新增 pause/resume/get 用例；Controller/UseCase 测试扩展），引用尚未创建的 `WorkflowStatus`、`PatientRiskResumeRequest`、`resume`/`getRun` 等符号。

## 6. GREEN

- 命令：`.\run-maven-jdk17.ps1 test`
- 结果：ai-core 69 / enterprise 21 / patient 19 / spring-ai-alibaba-agent 34，`BUILD SUCCESS`
- 关键修复：
  1. `graph-core 1.0.0.2` 的 `resume` 中节点收到的是 `CompiledGraph.overAllState()`，需在 `resume` 前 `graph.overAllState().updateState(approved)` 注入人工决策
  2. `invoke`/`resume` 返回值来自独立 `resumeState` 对象，最终业务状态须从 `graph.getState(config).state()` 读取（含 `escalated`/`report`/`humanApproved`）

## 7. 重构

- 抽取 `resolveApproval` / `toResult` / `requirePendingSnapshot`，保持 `PatientRiskWorkflow` 可读
- `run()` 保留为 `start()` 别名，兼容第 9 周测试与调用方

## 8. 质量门禁

- [x] 根聚合 `test` 全绿（143 个测试）
- [x] 无密钥入库
- [x] 分层未突破：Controller 只做校验/协议转换；HITL 编排在领域层
- [x] public 类型中文 JavaDoc
- [x] 未改 `JAVA_HOME`
- [x] YAGNI：未引 RedisSaver；未改 FrameworkAgentGraph

## 9. 验证证据

```
[INFO] spring-ai-alibaba-agent ............................ SUCCESS
[INFO] BUILD SUCCESS
Tests run: 34 (spring-ai-alibaba-agent)
```

新增/扩展测试：

- `PatientRiskWorkflowTest`（8）：MEDIUM 同步 COMPLETED；HIGH 暂停 PENDING；approve→COMPLETED+escalated+report；reject→REJECTED 无 report；getRun；非待审 resume 409 领域异常；未知 workflow 404
- `PatientRiskUseCaseTest`（4）：resume 委派、空白 workflowId 拦截
- `PatientRiskWorkflowControllerTest`（5）：status 字段、resume/get 端点

## 10. 风险与下周输入

- **MemorySaver 进程内**：重启丢失 checkpoint；落 Redis/DB 留第 12 周平台化
- **graph-core resume 语义**：节点读 `CompiledGraph.overAllState()` 而非 `resumeState`；后续 HITL 扩展须注意此框架行为
- **下周（第 11 周 Multi Agent）**：Supervisor 编排多个 Agent，可复用 Workflow 图模式

## 11. 决策记录 ADR

| 决策 | 选项 | 选择 | 理由 |
|------|------|------|------|
| 暂停点 | interruptBefore escalate / human_review | interruptBefore human_review | 审核在加急/报告之前，符合「高风险任务人工审核」 |
| 存储 | MemorySaver / RedisSaver | MemorySaver | YAGNI；单测不依赖 Redis |
| 结果读取 | invoke 返回值 / getState | getState(config).state() | resume 后 invoke 返回的 state 对象不含节点写入的 escalated/report |
| API | SSE 流式 / sync+resume | sync start + resume/get | 聚焦 HITL 核心，不引入 SSE 复杂度 |
