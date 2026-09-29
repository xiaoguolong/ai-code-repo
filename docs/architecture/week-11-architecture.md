# Architecture: Week 11 — Multi Agent（Supervisor 医疗助手）

## 1. 上下文

| 模块 | 职责 |
|------|------|
| `apps/spring-ai-alibaba-agent` | 第 8 周 ReAct Graph、第 9–10 周 PatientRisk Workflow |
| **本周增量** | Supervisor 多 Agent Graph + REST |

## 2. 分层

```
Controller (MedicalAssistantController)
    ↓
Application (MedicalAssistantUseCase)
    ↓
Domain (MedicalAssistantSupervisorGraph, MedicalAssistantSupervisor)
    ↓ Ports
ChatModelPort / ToolPort / PromptTemplatePort
    ↓
Infrastructure (Spring AI 适配器、PatientLookupTool、HealthMetricTool)
```

## 3. 领域模型

### 3.1 共享 State 键

| 键 | 类型 | 写入者 |
|----|------|--------|
| `runId` / `patientId` / `task` | String | 输入 |
| `patient` | PatientProfile | data_agent |
| `metrics` | HealthMetrics | data_agent |
| `riskLevel` / `justification` | RiskLevel / String | analysis_agent |
| `report` | String | report_agent |
| `followUpPlan` | String | followup_agent |
| `route` | String | supervisor（条件边） |
| `steps` | List&lt;MedicalAssistantStep&gt; | 各节点 |
| `usage` / `model` | TokenUsage / String | report / followup |

### 3.2 Supervisor 路由规则

`MedicalAssistantSupervisor.planNext(state)`：

1. `patient` 或 `metrics` 缺失 → `DATA`
2. `riskLevel` 缺失 → `ANALYSIS`
3. `report` 空白 → `REPORT`
4. `followUpPlan` 空白 → `FOLLOWUP`
5. 否则 → `FINISH`

## 4. Agent 角色

| Agent | 节点名 | 职责 | 依赖 |
|-------|--------|------|------|
| Supervisor | `supervisor` | 读 state、规划 route、记轨迹 | `MedicalAssistantSupervisor` |
| 数据 Agent | `data_agent` | 调 PatientLookup + HealthMetric 工具 | `ToolPort` |
| 分析 Agent | `analysis_agent` | `PatientRiskAssessor.assess` | 纯领域 |
| 报告 Agent | `report_agent` | LLM 生成报告 | `ChatModelPort` + `medical-report` prompt |
| 随访 Agent | `followup_agent` | LLM 生成随访计划 | `ChatModelPort` + `medical-followup` prompt |

## 5. 与第 9–10 周 Workflow 对比

| 维度 | PatientRiskWorkflow | MedicalAssistantSupervisorGraph |
|------|---------------------|--------------------------------|
| 编排 | 固定线性 + 条件分支 | Supervisor 循环调度 |
| HITL | 有（HIGH） | 无 |
| Agent 数 | 单流程节点 | 1 Supervisor + 4 Worker |
| checkpoint | MemorySaver | 无（同步 run） |

## 6. 安全与 Prompt

- 患者数据只进 **user** 消息；system 来自版本化 prompt 文件
- 用户 `task` 字段当数据，不当 system 指令拼接

## 7. 测试策略

- **领域**：fake `ChatModelPort` / `ToolPort`，断言 steps 顺序与 LLM 调用次数
- **控制器**：`@WebMvcTest` mock UseCase
- **装配**：`SpringAiAlibabaAgentApplicationTest` 增加 Multi Agent Bean
