# Week 11 实现日志 — Multi Agent（Supervisor + 医疗助手四角色）

- 日期：2026-09-29
- 批次：1
- 对应 Spec：docs/specs/week-11.md

## 1. 本周目标

1. 新增 **Supervisor 多 Agent Graph**：Supervisor 规则调度 **数据 / 分析 / 报告 / 随访** 四个 Worker
2. Agent 间通过 `OverAllState` 共享 patient / metrics / riskLevel / report / followUpPlan
3. 暴露 `POST /api/v1/multi-agent/medical-assistant/runs`
4. **不改** `FrameworkAgentGraph`、`PatientRiskWorkflow`

## 2. 边界

- Always：规则 Supervisor；同步 run；工具/模型经端口；中文 JavaDoc；单测 fake 端口
- Never：改第 8–10 周既有图；抽 ai-core 平台端口；密钥入库

## 3. 增量架构

```
START → supervisor ⇄ data_agent / analysis_agent / report_agent / followup_agent → END
```

Supervisor 路由：`MedicalAssistantSupervisor.planNext(state)` 按 state 缺项依次派工。

## 4. 新增类型清单

| 类型 | 名称 | 职责 |
|------|------|------|
| 枚举 | `SupervisorRoute` | DATA / ANALYSIS / REPORT / FOLLOWUP / FINISH |
| 模型 | `MedicalAssistantStep` / `MedicalAssistantResult` | 轨迹与结果 |
| 领域 | `MedicalAssistantSupervisor` | 规则路由 |
| 领域 | `MedicalAssistantSupervisorGraph` | Graph 编排 |
| 用例 | `MedicalAssistantUseCase` | 校验 + 启动 |
| 控制器 | `MedicalAssistantController` | REST |
| DTO | `MedicalAssistantRunRequest` / `MedicalAssistantRunResponse` | 协议 |
| Prompt | `medical-report-v1.txt` / `medical-followup-v1.txt` | 报告 / 随访 |

## 5. RED

- 先写 `MedicalAssistantSupervisorTest`、`MedicalAssistantSupervisorGraphTest`、Controller/UseCase 测试
- 编译失败（引用尚未实现的 Graph / Controller）

## 6. GREEN

- 命令：`.\run-maven-jdk17.ps1 test`
- 结果：ai-core 69 / enterprise 21 / patient 19 / spring-ai-alibaba-agent **46**，`BUILD SUCCESS`（合计 **155** 测试）
- Graph 模式：Worker 执行后无条件回到 Supervisor；Supervisor 第五轮 route=FINISH 结束

## 7. 质量门禁

- [x] 根聚合 `test` 全绿
- [x] 第 8–10 周测试不回退
- [x] 分层：Controller → UseCase → Domain Graph
- [x] public 类型中文 JavaDoc
- [x] YAGNI：无 checkpoint / 无 LLM Supervisor

## 8. 验证证据

```
Tests run: 46 (spring-ai-alibaba-agent)
BUILD SUCCESS
```

新增测试类：

- `MedicalAssistantSupervisorTest`（5）
- `MedicalAssistantSupervisorGraphTest`（2）
- `MedicalAssistantUseCaseTest`（3）
- `MedicalAssistantControllerTest`（2）

## 9. 后续

- 第 12 周：Agent 注册 / Workflow 平台化；可考虑 LLM 结构化 Supervisor 路由
- Postman：`docs/postman/week-11.postman_collection.json`
