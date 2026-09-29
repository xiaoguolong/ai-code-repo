# Week 09 实现日志 — Workflow（患者风险分析多节点流程）

- 日期：2026-09-29
- 批次：1
- 对应 Spec：docs/specs/week-09.md
- 对应阅读：无（Workflow 为本周编码主线，独立笔记留待后续补）

## 1. 本周目标

在 `spring-ai-alibaba-agent` 上，用 Spring AI Alibaba Graph 的**多节点固定流程（State/Node/Edge/Graph + 条件边）**实现「患者风险分析」：查询患者 → 查询指标 → 风险判断 → 生成报告。与第 8 周「模型带工具循环（ReAct）」对照——本周节点按固定顺序执行、工具由节点直接经 `ToolPort` 调度、风险判断为确定性规则，高风险经条件边分支加急。

## 2. 边界

- Always：固定顺序执行；工具节点直接经 `ToolPort` 调度（不经过模型选工具）；风险判断用纯规则 `PatientRiskAssessor`；报告经 `ChatModelPort`，患者/指标数据作为 user 消息；`ToolPort` 仍是工具执行唯一咽喉点；中文 JavaDoc；单测不打真实网络（fake `ToolPort` + 脚本化 `ChatModelPort`）；无密钥入库
- Never：改第 8 周 `FrameworkAgentGraph` / `PatientLookupTool` / `HealthMetricTool` 行为；跨 app 依赖；抽 ai-core `WorkflowPort`；风险阈值散落魔法数字；用户数据拼 system 提示；改 `JAVA_HOME`

## 3. 增量架构

见 Spec `docs/specs/week-09.md` 与 `docs/architecture/week-09-architecture.md`。图结构：

```
START → query_patient → query_metrics → judge_risk
  →(条件边) urgent→escalate→generate_report；routine→generate_report
generate_report → END
```

## 4. 新增/变更类型清单

### workflow.domain（com.aicode.framework.workflow.domain）

| 类型 | 名称 | 职责 |
|------|------|------|
| 领域服务 | `PatientRiskWorkflow` | `StateGraph` 编排 5 节点 + 条件边，经端口调工具/模型/提示词 |
| 领域服务 | `PatientRiskAssessor` | 纯规则风险判断：`assess(HealthMetrics) → RiskAssessment`，阈值常量内聚 |
| 领域服务 | `PatientRiskToolResultMapper` | 工具 JSON 解析为值对象 + 组装工具入参 JSON（无状态，SRP 抽取） |

### workflow.domain.model

| 类型 | 名称 | 职责 |
|------|------|------|
| 值对象 | `PatientProfile` / `HealthMetrics` | 患者基础信息 / 健康指标 |
| 枚举 | `RiskLevel` | LOW/MEDIUM/HIGH + 中文标签 |
| 值对象 | `RiskAssessment` | 风险等级 + 判断依据 |
| 值对象 | `PatientRiskWorkflowResult` | 流程结果（workflowId/patient/metrics/riskLevel/report/usage/model） |

### workflow.application / controller / dto

| 类型 | 名称 | 职责 |
|------|------|------|
| 配置 | `PatientRiskRuntimeConfig` | 模型名/采样参数 |
| 用例 | `PatientRiskUseCase` | 校验 patientId → 调 Workflow |
| 控制器 | `PatientRiskWorkflowController` | `POST /api/v1/workflows/patient-risk/runs` |
| DTO | `PatientRiskRunRequest` / `PatientRiskRunResponse` / `PatientProfileDto` / `HealthMetricsDto` | 协议体 |

### 配置与提示词

- `AppConfiguration`（改）：新增 `patientRiskAssessor` / `patientRiskToolResultMapper` / `patientRiskRuntimeConfig` / `patientRiskWorkflow` 四个 Bean
- `prompts/patient-risk-report-v1.txt`（新增）：报告生成系统提示
- `SpringAiAlibabaAgentApplicationTest`（改）：新增三个 workflow Bean 装配断言

## 5. RED

- 命令：`.\run-maven-jdk17.ps1 -pl apps/spring-ai-alibaba-agent -am test`
- 结果：编译失败（预期）
- 原因：先写 4 个测试（`PatientRiskAssessorTest` / `PatientRiskWorkflowTest` / `PatientRiskUseCaseTest` / `PatientRiskWorkflowControllerTest`），引用 `com.aicode.framework.workflow.*` 尚未创建的类，报「程序包 com.aicode.framework.workflow.domain / .model / .application 不存在」「找不到符号 PatientRiskAssessor / PatientRiskWorkflow / PatientRiskUseCase / PatientRiskWorkflowController」。ai-core 69 测试仍全绿，属编译期 RED。

## 6. GREEN

- 命令：`.\run-maven-jdk17.ps1 -pl apps/spring-ai-alibaba-agent -am test`
- 结果：`spring-ai-alibaba-agent 24`（含新增 13 条），`BUILD SUCCESS`
- 改动文件：新增 14 个生产类（模型 5 + 领域服务 3 + 应用 2 + 控制器 1 + DTO 4，减去 `PatientRiskRuntimeConfig` 计入 application）+ 1 个提示词 + AppConfiguration 改动
- 关键修复：`OverAllState.value(key, null)` 的 `null` 默认值导致泛型 `T` 无法推断（编译报「无法使用 Optional<T> 与 PatientProfile 一起」），改用带 `Class` 的重载 `state.value(KEY_PATIENT, PatientProfile.class).orElse(null)`。

## 7. 重构

- 抽取 `PatientRiskToolResultMapper`：把工具 JSON 的序列化/反序列化从 Workflow 移出，单一职责，`PatientRiskWorkflow` 从 295 行降到 259 行（与第 8 周 `FrameworkAgentGraph` 的 233 行同级，剩余长度为 5 节点图 DSL 固有样板）。
- `Map.of` 节点返回只含本节点写回的键（partial state），与第 8 周一致；`patient`/`metrics` 等「后续产生」的键不进 `input`，避免向图状态写入 null。
- 报告生成的 user 消息用手写可读文本（患者/指标/风险/加急指引），不依赖 Jackson 序列化领域对象。

## 8. 质量门禁

- [x] 根聚合 `test` 全绿（ai-core 69 / enterprise 21 / patient 19 / spring-ai-alibaba-agent 24 = 133）
- [x] 无密钥入库（模型密钥仅 `spring.ai.openai.api-key=${LLM_API_KEY:}` 环境变量，未新增）
- [x] 分层未突破：controller 只做校验/协议转换；Workflow 经端口调工具与模型；风险规则在领域层 `PatientRiskAssessor`；工具执行仍走 `ToolPort`
- [x] public 类型中文 JavaDoc
- [x] 未改系统/用户 `JAVA_HOME`（仅 `-Djdk.17.home`）
- [x] 无 System.out/err 打印（Graph 内部 ERROR 日志为故意抛异常用例的预期输出）
- [x] YAGNI：未抽 ai-core `WorkflowPort`；未引 `CheckpointSaver`；未改第 8 周代码与工具

## 9. 验证证据

```
[INFO] Reactor Summary for ai-code-repo 0.1.0-SNAPSHOT:
[INFO] ai-core ............................................ SUCCESS
[INFO] enterprise-knowledge-agent ......................... SUCCESS
[INFO] patient-agent ...................................... SUCCESS
[INFO] spring-ai-alibaba-agent ............................ SUCCESS
[INFO] ai-code-repo ....................................... SUCCESS
[INFO] BUILD SUCCESS
```

新增测试覆盖：

- `PatientRiskAssessorTest`（6）：收缩压/糖化血红蛋白达高危阈值→HIGH、demo 数据（148/92/8.6/7.9）→MEDIUM、全正常→LOW、中风险阈值闭区间（140/90/7.0/7.0）→MEDIUM、三档 justification 永不空白
- `PatientRiskWorkflowTest`（3）：中风险走 routine（escalated=false，报告/用量/模型正确）、高风险走 escalate（escalated=true，报告生成）、工具异常 `ToolExecutionException` 被正确解包
- `PatientRiskUseCaseTest`（2）：空 patientId 拦截、trim 后委派
- `PatientRiskWorkflowControllerTest`（2）：200 结构（patient/metrics/riskLevel/riskLabel/escalated/report/model/usage）、空 patientId 400 `VALIDATION_ERROR`
- `SpringAiAlibabaAgentApplicationTest`（改）：新增 `PatientRiskAssessor`/`PatientRiskWorkflow`/`PatientRiskUseCase` 装配断言

## 10. 风险与下周输入

- **数据不一致（已知）**：`patient-agent` 的 `PatientDataPort`（P001 68 岁，指标无糖化血红蛋白）与 `spring-ai-alibaba-agent` 的 demo 工具（P001 62 岁，含 hba1c）数据不同。第 9 周复用后者（同模块 + 有 hba1c），统一患者数据留待第 12 周平台化 / 第 21–24 周实战。
- **下周（第 10 周 Human-in-the-loop）**：`StateGraph` 已支持 `HumanFeedback` / `interruptMessage`，可为「高风险任务人工审核 → 恢复」打底；`CheckpointSaver`（恢复所需）届时引入。
- **Workflow 引擎抽象**：`PatientRiskWorkflow` 直接使用 Alibaba `StateGraph`，抽 ai-core 通用 `WorkflowPort` 留待第 12 周 Agent 平台。

## 11. 决策记录 ADR

| 决策 | 选项 | 选择 | 理由 |
|------|------|------|------|
| 流程载体 | 新增模块 / 扩展 spring-ai-alibaba-agent | 扩展 spring-ai-alibaba-agent（新增 `workflow.*` 包） | 复用 graph-core 依赖与端口，README「新应用在旧模块基础上改造」 |
| 编排形态 | 模型带工具循环（沿用第 8 周）/ 固定多节点图 | 固定多节点图（StateGraph） | 本周学 Workflow，与第 8 周 ReAct 对照 |
| 工具调度 | 节点经模型选工具 / 节点直接经 `ToolPort` | 节点直接经 `ToolPort` | 固定流程无需模型选工具，工具执行仍走唯一咽喉点 |
| 风险判断 | 模型判断 / 确定性规则 | 确定性规则 `PatientRiskAssessor` | 医疗风险可解释可测试，业务规则不进基础设施 |
| 高风险分支 | 多报告节点 / 单报告节点 + `escalate` 标记节点 | 单 `generate_report` + `escalate` | 报告逻辑唯一，条件边真实分支，DRY 兼顾 |
| 工具 JSON 解析 | Workflow 内联 / 独立 mapper | 独立 `PatientRiskToolResultMapper` | SRP，避免 Workflow 内散落 Jackson 细节，类行数收敛 |
