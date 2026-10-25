# Week 24 实现日志 — Agent 2 运营分析 + Agent 4 随访 + 观测/一致性收口

- 日期：2026-10-25
- 批次：1（P0 全部批次合并交付）
- 对应 Spec：`docs/specs/week-24.md`
- 对应架构：`docs/architecture/week-24-architecture.md`
- 对应接口：`docs/api/week-24-api.md`
- 运维手册：`docs/deploy/week-24-ops.md`

## 1. 本周目标

在医疗 SaaS 平台上补齐 README 第 6 阶段的 **Agent 2（运营分析）** 与 **Agent 4（随访）**，
并收口第 23 周遗留的三笔 P0 债：

1. **Agent 2**：按窗口统计 `execution_record`（成功/失败/待审/拒绝/token/成本），6 条规则产出异常，
   模型写日报正文，模型不可用时降级为规则模板；
2. **Agent 4**：规则生成随访计划（HIGH 3 条 / MEDIUM 2 条 / LOW 1 条），任务落库、可查询/完成/取消，
   站内提醒按「逾期 + 即将到期」分组；**HIGH 审批通过后自动生成任务**；
3. **TD-01**：Grafana 失败率分母排除 `PENDING_APPROVAL`，并新增成功率曲线；
4. **TD-02**：审批决策加事务 + 每 10 分钟对账任务，消除 `APPROVED + PENDING_APPROVAL`；
5. **TD-03**：新增 `approval.pending.count` Gauge、每分钟采样与 `AiCodePendingApprovalBacklog` 告警。

## 2. 边界

- **Always（已做）**：上面 5 项 + V8 迁移 + 单测 + Spec/架构/API/Postman/运维手册/日志。
- **Never（未做，明确）**：Agent 3 知识库（避免与 enterprise RAG 重复建设）、pgvector 云端迁移、
  HIS/LIS 真实数据、短视频/邮件提醒、平台用户硬删、租户自建角色、多级审批、前端。

## 3. 增量架构

见 `docs/architecture/week-24-architecture.md`。一句话：

```
controller → application（OperationsAnalysis / FollowUpTask / Reconciliation）
           → domain（统计口径 / 异常规则 / 计划规则 / 任务状态机）
           ← infrastructure（执行记录时间窗查询 / 随访任务表 / Gauge / 调度 / 模型）
```

## 4. 新增类型清单

| 类型 | 名称 | 职责 |
|------|------|------|
| domain/model | `OperationsSummary`、`AgentRunSummary`、`OperationsAnomaly(+Code/Level)`、`OperationsDailyReport`、`OperationsAnalysisRequest` | 运营统计与日报 |
| domain/service | `OperationsStatistics` | **唯一**统计口径（成功率分母只含 COMPLETED+FAILED） |
| domain/service | `OperationsAnomalyDetector` | 6 条确定性规则（失败率/积压/token 突增/单 Agent 集中/无流量） |
| domain/service | `OperationsReportComposer` | 模型不可用时的规则日报模板 |
| domain/service | `FollowUpPlanner` | 风险 → 任务条目（时间/优先级/标题由规则决定） |
| domain/model | `FollowUpTask`、`FollowUpStatus`、`FollowUpPriority`、`FollowUpSource`、`FollowUpPlan(+Item)`、`FollowUpPlanResult`、`FollowUpStep` | 随访任务与计划 |
| domain/port | `FollowUpTaskPort` | 任务持久化 + 原子状态迁移 + 提醒查询 |
| domain/port（改） | `ExecutionRecordPort` | 增加按租户/用户 + 时间窗查询 |
| domain/port（改） | `PlatformApprovalPort` | 增加 `countAll` / `countByUser` |
| domain/port（改） | `AgentObservabilityPort` | 增加 `recordGauge` |
| application | `OperationsAnalysisUseCase`、`FollowUpTaskUseCase`、`FollowUpTaskActionUseCase`、`ApprovalReconciliationUseCase`、`PlatformApprovalSideEffects` | 用例编排 |
| infrastructure | `OperationsAnalysisAgent`、`FollowUpAgent` | Agent 运行时（规则 + 一次模型调用 + 降级） |
| infrastructure | `JdbcFollowUpTaskAdapter`、`InMemoryFollowUpTaskAdapter` | 任务持久化 |
| infrastructure | `PendingApprovalGaugeScheduler`、`ApprovalReconciliationScheduler` | 采样 / 对账 |
| infrastructure | `PlatformOperationsProperties`、`PlatformFollowUpProperties`、`PlatformApprovalProperties` | 配置 |
| controller/dto | `PlatformFollowUpController`、`FollowUpTaskResponse/ListResponse/ReminderResponse/CompleteRequest/CancelRequest` | 协议 |
| 迁移 | `V8__platform_followup_task.sql` | 随访任务表 + 2 索引 + 1 唯一索引 |
| Prompt | `prompts/operations-daily-report-v1.txt`、`prompts/follow-up-plan-v1.txt` | 版本化系统提示 |

## 5. RED（先写测试）

新增测试先写、先跑，记录到的真实失败（摘录）：

| 失败 | 原因 | 修法 |
|------|------|------|
| `OperationsAnalysisAgentTest.returnsModelReportWithStatisticsAndAnomalies`（2 例） | 断言 3 条记录就命中失败率规则，但规则要求分母 ≥ 5 | 测试数据改为 6 成功 + 4 失败 + 1 待审；断言失败率 0.4 |
| `PlatformFollowUpHttpTest`（5 例 `expected 200 but was 403`） | 用了 `P777`，超出 operator 的数据域（`P001–P010`） | 改用 `P007` |
| `PlatformFollowUpHttpTest.runsAgent...` `total expected 3 but was 10` | 同一 Spring 上下文/H2 库在类内复用，任务累计 | `@BeforeEach` 清理本类患者的任务 |
| `PlatformFollowUpHttpTest.otherTenant...` DuplicateKey | 复用 `doctor_b` 用户名（另一个测试已建） | 改用独立用户 `doctor_w24` |
| `ApprovalReconciliationUseCaseTest` finishedAt 不等 | 对账写入的是审批决策时间（NOW-60s），不是当前时间 | 断言改为 `decidedAt` |
| `PlatformSeedIdempotencyTest` / `PlatformRbacPermissionUpgradeInitializerTest`（4 例） | 新增 `followup` Agent 与 `operations-analysis` 注册，旧断言写死 4 个 Agent / 精确授权集合 | 按新契约更新断言（6 个 Agent；operator 授权含 `followup`） |
| 编译错误 3 处 | `ExecutionRecordPort` / `AgentObservabilityPort` 加方法后，测试里的手写实现缺失 | 补 `listByTenantBetween/listByUserBetween`、`recordGauge` |

> 说明：seed 测试的 4 处更新是**契约变更**（新增 Agent 是本周需求），不是为了让红灯变绿的妥协。

## 6. GREEN（改动文件）

### 6.1 主代码（按层）

- **domain**：`operations/**`（9 个类）、`followup/**`（8 个类）、`platform/domain/port/{ExecutionRecordPort,PlatformApprovalPort}`、
  `AgentType`、`AuditAction`、`PlatformPermissionChecker`（+`requireFollowUpAccess`）；
- **application**：`OperationsAnalysisUseCase`、`FollowUpTaskUseCase`、`FollowUpTaskActionUseCase`、
  `ApprovalReconciliationUseCase`、`PlatformApprovalSideEffects`、`PlatformApprovalDecisionUseCase`（事务 + 任务副作用）；
- **infrastructure**：2 个 Agent、2 个任务适配器、2 个调度器、3 个配置类、2 个 JDBC 适配器扩展、
  `Micrometer/Noop/SkyWalking` 的 `recordGauge`、`MonitoringMetricNames`、`V8` 迁移；
- **controller/dto**：`PlatformFollowUpController` + 5 个 DTO；
- **seed**：`PlatformSeedDataInitializer`（+2 Agent）、`PlatformRbacSeedInitializer` / `PlatformRbacPermissionUpgradeInitializer`（+`followup`）；
- **config**：`application.yml`（operations / followup / approval 三段）、`.env.example`（全部可选键）。

### 6.2 运维/观测资产

- `deploy/monitoring/grafana/dashboards/aicode-platform-overview.json`：
  失败率分母改 `execution_status=~"COMPLETED|FAILED"`；面板改「成功率 / 失败率」双值；新增「待审审批积压」；
- `deploy/monitoring/prometheus/alerts.yml`：失败率告警同口径 + 新增 `AiCodePendingApprovalBacklog`；
- `deploy/app/ttl-backfill-week24.sql`（TD-05）、`deploy/app/rollback-drill.sh`（TD-04）、
  `deploy/app/cloud-accept-week24.sh`（云端验收，待执行；已通过 `bash -n` 语法检查）。

### 6.3 命令

```powershell
# 单模块
.\run-maven-jdk17.ps1 -pl apps/spring-ai-alibaba-agent -am test
# 根聚合
.\run-maven-jdk17.ps1 test
```

## 7. 重构

- `PlatformApprovalDecisionUseCase` 原本 202 行，加事务 + 随访任务会破 200 行红线：
  抽出 `PlatformApprovalSideEffects`（记忆 + 任务，各自失败降级）与 `ApprovalDecision.followUpTaskIds`；
  决策用例回到 **约 195 行**，只负责权限、状态机、事务边界。
- 随访用例拆成 `FollowUpTaskUseCase`（生成/查询/提醒）与 `FollowUpTaskActionUseCase`（完成/取消），
  避免单类超过 200 行。
- `PlatformAgentRunner` 采用「多构造器兼容」策略：3 参 / 5 参 / 7 参，Spring 只认 `@Autowired` 的 7 参，
  既有 10+ 个测试无需改动（与第 21/23 周同一做法）。

## 8. 质量门禁

- [x] 编译：`test-compile` BUILD SUCCESS
- [x] 单测：根聚合 **884** 用例全绿（见第 9 节）
- [x] JDK：未改 `JAVA_HOME`，全部通过 `-Djdk.17.home` / 脚本
- [x] 文档：新增 public 类型均有 JavaDoc（中文，写职责与约束）
- [x] 分层：Controller 只调 UseCase；统计规则在 domain；SQL 只在适配器；领域层无 Web 类型
- [x] 安全：无密钥入库；日报只送聚合数给模型（测试断言 prompt 不含 patientId）
- [x] YAGNI：无新依赖（无 MQ/Quartz/Redis/前端）
- [x] 数据库脚本：V8 双库可跑（`CREATE TABLE/INDEX IF NOT EXISTS`，无 CLOB/JSONB/部分索引）
- [x] 日志：本文 + `docs/logs-index.md` 已归档原始测试日志

## 9. 验证证据

### 9.1 根聚合测试

```
ai-core 90 / enterprise 55 / patient 21 / spring-ai-alibaba-agent 619 / agent-gateway 100
合计 885，Failures 0，Errors 0，BUILD SUCCESS
（Week 23 基线 798 → 885，本周 +87）
原始日志：logs/week-24/test/target-week24-full.log
```

### 9.2 V8 在 H2（MODE=PostgreSQL）首次启动

```
09:48:48.262 INFO o.f.core.internal.command.DbMigrate
  - Successfully applied 8 migrations to schema "public", now at version v8
后续上下文：Successfully validated 8 migrations × 11 次（只校验不重跑）
```

### 9.3 关键行为（测试即证据）

| 行为 | 测试 |
|------|------|
| 失败率分母只含 COMPLETED+FAILED；operator 只看自己 | `OperationsStatisticsTest` / `OperationsAnalysisUseCaseTest` |
| 6 条异常规则的命中与不误报 | `OperationsAnomalyDetectorTest`（12 例） |
| 日报两条降级路径；prompt 不含患者信息 | `OperationsAnalysisAgentTest`（6 例） |
| 运营日报 HTTP：admin 200 / operator 403 / 窗口越界 400 | `PlatformOperationsHttpTest`（4 例） |
| 随访计划三档规则 | `FollowUpPlannerTest`（6 例） |
| 任务落库、隔离、审批幂等、提醒分组 | `FollowUpTaskUseCaseTest`（10 例） |
| 完成/取消、重复 409、越权 403、404 | `FollowUpTaskActionUseCaseTest`（8 例） |
| 随访 HTTP 全链路（运行→列表→提醒→完成→409→403） | `PlatformFollowUpHttpTest`（8 例） |
| 审批通过 → 3 条任务；拒绝 0 条；写失败不影响审批 | `PlatformApprovalFollowUpTest`（4 例） |
| 对账补齐 `APPROVED/REJECTED + PENDING_APPROVAL` | `ApprovalReconciliationUseCaseTest`（6 例） |
| **事务回滚**：执行终态写失败 → 审批回 PENDING、任务不残留 | `PlatformApprovalTransactionTest`（H2 + 真实事务管理器，1 例） |
| Gauge 导出名/最新值/失败不抛 | `MonitoringMetricsContractTest`（+1）、`PendingApprovalGaugeSchedulerTest`（2 例） |

### 9.4 面板与告警资产校验

- `aicode-platform-overview.json` 可被 `ConvertFrom-Json` 解析，18 个面板；
- 「Agent 成功率 / 失败率」两个 target：A 失败率、B 成功率，分母均为 `execution_status=~"COMPLETED|FAILED"`；
- 新增「待审审批积压」面板（阈值 10 橙 / 20 红），与原 16 个面板不重叠（y=44，文本面板下移到 y=49）；
- `alerts.yml` 新增 `AiCodePendingApprovalBacklog`（`approval_pending_count > 20` 持续 30m）。

## 10. 风险、已知边界与下周输入

| # | 项 | 说明 / 后续 |
|---|----|-------------|
| 1 | 运营统计在应用层聚合（上限 5000 条） | 数据量上来换 SQL 聚合；当前 `truncated=true` 会明示 |
| 2 | 随访计划是演示规则 | 接真实指南时只改 `FollowUpPlanner`；模型不能改任务条目 |
| 3 | `@Transactional` 只对 JDBC 模式生效 | 内存模式（单测/演示）行为退化为尽力而为，Spec 已写 |
| 4 | 对账与采样都是单实例假设 | 对账幂等、采样可重复；多实例前加分布式锁 |
| 5 | 未做 Agent 3 / TD-06 用户停用 / TD-05 执行 | 见 Spec 第九、十二节；TD-05 脚本已就绪待运维执行 |
| 6 | TD-04 回滚演练只有脚本 | 需维护窗口；演练记录表在 `docs/deploy/week-24-ops.md` 8.3，未填 |
| 7 | 云上尚未发布本批次 | 未执行 V8 真库与云端验收；手册已备（第 2–3 节） |

> 下周（第 25 周起）输入：Agent 3 形态决策（包装 enterprise RAG vs 平台原生）、
> 云端 V8 发布与 `cloud-accept-week24.sh` 验收、TD-04 演练、真实 HIS/LIS 数据源评估。

## 11. 决策记录（ADR）

| 决策 | 选项 | 选择 | 理由 |
|------|------|------|------|
| 统计实现 | SQL 聚合 / 应用层算 | 应用层（上限 5000） | 双适配器一份规则、可单测；演示量级够 |
| 失败率分母 | 全部状态 / 只 COMPLETED+FAILED | 只 COMPLETED+FAILED | 待审不是失败；与 Grafana 同口径 |
| 运营可见性 | 所有人看全租户 / admin 全租户+普通用户只看自己 | 后者 | 与审批/执行列表同一套语义 |
| 随访任务生成 | 模型生成任务 / 规则生成+模型写说明 | 后者 | 临床时间点不能被幻觉改写 |
| 审批联动失败 | 回滚审批 / WARN+审计+可重跑 | 后者 | 审批已生效，任务可补 |
| 一致性 | 分布式事务 / 本地事务+对账 | 本地事务+对账 | 单库单实例，对账兜底历史脏数据 |
| 待办指标维度 | 按租户标签 / 全局 | 全局 | 租户增长会打爆时间序列 |
| 提醒 | 通知表 / 查询视图 | 查询视图 | 没有推送渠道，已读状态是空转（YAGNI） |

## 12. 本周新增测试（87）

| 测试类 | 例数 | 覆盖 |
|--------|------|------|
| `OperationsStatisticsTest` | 7 | 状态计数、分母口径、token、成本、分组、截断 |
| `OperationsAnomalyDetectorTest` | 12 | 6 条规则、阈值边界、小样本、排序、非法阈值 |
| `OperationsAnalysisAgentTest` | 6 | 口径装配、模型成功、两条降级、prompt 不含患者 |
| `OperationsAnalysisUseCaseTest` | 7 | admin/operator 可见性、窗口、积压、token 突增、参数校验、截断 |
| `PlatformOperationsHttpTest` | 4 | admin 200 / operator 403 / 400 / 401 |
| `FollowUpPlannerTest` | 6 | HIGH/MEDIUM/LOW、未知风险、大小写、排序 |
| `FollowUpAgentTest` | 5 | 规则任务、模型文本、两条降级、工具失败 |
| `FollowUpTaskUseCaseTest` | 10 | 落库/范围、隔离、过滤、详情权限、审批幂等、提醒 |
| `FollowUpTaskActionUseCaseTest` | 8 | 完成/取消、409、403、404、备注上限、空 ID |
| `PlatformFollowUpHttpTest` | 8 | 运行→列表→详情→提醒→完成→409→跨租户→viewer 403→400/401 |
| `PlatformApprovalFollowUpTest` | 4 | 批准 3 任务、拒绝 0、写失败不影响审批、重复批准 |
| `ApprovalReconciliationUseCaseTest` | 6 | APPROVED/REJECTED 补齐、终态跳过、缺失跳过、幂等、limit |
| `PlatformApprovalTransactionTest` | 1 | **事务回滚**：执行终态写失败 → 审批仍 PENDING、任务不残留 |
| `PendingApprovalGaugeSchedulerTest` | 2 | Gauge 值、采样失败不抛 |
| `MonitoringMetricsContractTest`（增补） | +1 | Gauge 导出名/最新值/`# TYPE gauge` |
| `PlatformApprovalHttpTest`（增补断言） | 0 | 批准响应含 `followUpTaskIds`（3 条） |
| `PlatformRbac*` / `PlatformSeedIdempotencyTest`（契约更新） | 0 | 6 个 Agent、operator 含 `followup` |
