# Week 27 实现日志 — 知识库运营化（绑定对账 + 审批超时提醒 + 检索指标）

> 来源：`docs/specs/week-27.md`（执行版 Spec）｜ 架构：`docs/architecture/week-27-architecture.md` ｜
> 接口：`docs/api/week-27-api.md` ｜ 迁移：`V10__platform_knowledge_sync_and_approval_reminder.sql`。

## 1. 本周目标

把两件「只能靠人记」的运营动作变成系统能力，并给知识库检索补专项指标：

1. **绑定对账**：企业侧改名/删库后平台绑定自动跟上；删库显式 404，不伪装成「没检索到」。
2. **审批超时提醒**：待审超过 24h 有站内提醒（审计 + 指标 + 冷却），但**绝不自动拒绝**。
3. **检索指标**：`knowledge.search.duration` / `knowledge.hit.count`，对账另出 2 个指标。

## 2. 边界

**做了**：

- V10：`platform_knowledge_binding` 两列（`sync_status` / `last_synced_at`）+ `platform_approval_reminder` 表 + 1 个索引；
- 对账用例（按「租户 + 企业归属人」分组）、每日调度、admin 手动端点、按企业用户列库的 enterprise 端点；
- 审批提醒用例（阈值/冷却/单次上限）、每小时调度、站内视图端点、审计 `APPROVAL_REMIND`；
- 5 个指标 + 直方图配置 + 指标契约测试；
- 42 个 platform 用例 + 5 个 enterprise 用例；
- **云端发布与真机验收**：enterprise 14:59:15 → platform 15:01:32（V10 15:01:27），
  对账 / 提醒 / 问答 / 指标 / 配置恢复终检 **PASS=29 FAIL=0**。

**没做（明确）**：

- 科室级知识库授权（`platform_role_knowledge`）、平台用户停用 TD-06、SSE、日报导出、HIS/LIS；
- 审批自动拒绝/自动升级；对账自动新增/删除绑定；指标告警规则（下周按噪声加）。

## 3. 增量架构

见 `docs/architecture/week-27-architecture.md`。一句话：

> 对账 = 按企业归属人分组拉清单 → 差异落库（ACTIVE/MISSING，失败不动）；
> 提醒 = 扫「PENDING 且超阈值且过冷却」→ 记账 + 审计 + 指标（状态不变）；
> 检索 = `MISSING` 直接 404 + Timer/Counter 埋点。

## 4. 新增/改动类型清单

### 4.1 platform（8084）

| 类型 | 文件 | 职责 |
|------|------|------|
| 枚举 | `KnowledgeBindingSyncStatus` | UNKNOWN / ACTIVE / MISSING；与 `enabled` 正交 |
| 模型 | `KnowledgeBinding`（改） | 加 `syncStatus/lastSyncedAt`；`synced/markMissing/retrievable`；保留 10 参构造器兼容 Week 25 |
| 端口 | `KnowledgeBindingPort`（改） | `updateSyncState`（只刷同步列）、`listAll`、`countBySyncStatus` |
| 端口 | `PlatformApprovalPort`（改） | `listPendingCreatedBefore` / `listAllPendingCreatedBefore` / `listRemindable` / `markReminded` |
| 用例 | `KnowledgeBindingReconcileUseCase` | 权限、分组、失败隔离、审计、指标汇总（194 行） |
| 协作类 | `KnowledgeBindingGroupReconciler` | 单组差异应用：改名保存 / 未变只刷同步列 / 缺失标 MISSING |
| 协作类 | `KnowledgeReconcileReporter` | 对账 4 个 Counter + MISSING Gauge 统一口径 |
| 用例 | `ApprovalReminderUseCase` | 阈值/冷却/上限、记账、审计、视图（admin 全量） |
| 视图 | `ApprovalReminderView` | 超时条目 + `overdueHours` |
| 调度 | `KnowledgeBindingReconcileScheduler` | 每日 02:15（可关） |
| 调度 | `ApprovalReminderScheduler` | 每小时 :05（可关） |
| 持久化 | `Jdbc/InMemoryKnowledgeBindingAdapter` | V10 两列 + 轻量同步更新 |
| 持久化 | `Jdbc/InMemoryPlatformApprovalAdapter` | 提醒记账（UPDATE→INSERT）/ 冷却查询 |
| 持久化 | `PlatformApprovalRowMapper`（抽出） | 审批行映射（控制适配器行数） |
| DTO | `KnowledgeReconcileResponse`、`ApprovalReminderResponse`；`KnowledgeBindingResponse`（改） | 对外契约 |
| 指标 | `MonitoringMetricNames` / `ObservabilityAttributes`（改） | 5 个新指标 + 2 个标签键 |
| 审计 | `AuditAction`（改） | `KNOWLEDGE_RECONCILE` / `APPROVAL_REMIND` |

### 4.2 enterprise（8081）

| 类型 | 文件 | 职责 |
|------|------|------|
| 控制器 | `InternalKnowledgeController`（改） | `/bases` 支持 `platformUserId` \| `enterpriseUserId`，**恰好一个** |
| 用例 | `InternalKnowledgeUseCase`（改） | `listBasesByEnterpriseUser`；校验本地用户存在（不存在 = 404，平台计 failed） |

### 4.3 配置

```yaml
platform.knowledge.reconcile: enabled / cron(0 15 2 * * *) / batch-limit(500)
platform.approval.reminder:   enabled / cron(0 5 * * * *) / threshold-hours(24)
                              / cooldown-hours(12) / limit(200)
management.metrics.distribution.percentiles-histogram.knowledge.search.duration: true
management.metrics.distribution.maximum-expected-value.knowledge.search.duration: 5s
```

## 5. RED

本周的 RED 是**结构性**的：新增端口方法、构造器参数与 V10 列在实现前不存在，测试无法编译。
因此采用「先定契约（Spec/接口文档）→ 同步写实现与断言」的顺序，而不是逐条先跑红。

为避免「测试只证明实现能跑」，用例刻意锁定了 6 条容易写错的语义：

1. 企业侧改名 → `name` 更新且 `syncStatus=ACTIVE`；
2. 企业侧删库 → 行仍在、`MISSING`、`updatedAt` 不被改；
3. 上游失败 → 状态与 `lastSyncedAt` **不变**、只计 `failed`；
4. 企业侧新增 → `unbound>0` 但绑定表不新增行；
5. 提醒后 → `status=PENDING`、`decidedBy=null`、`decidedAt=null`；
6. 冷却窗口内第二次调度 → 0 条提醒，13h 后可再次提醒。

## 6. GREEN

### 6.1 对账

- 分组键 `(tenantKey, enterpriseUserId)`：同一企业归属人只调一次企业清单；
- `unbound` 用**全量绑定**计算（不受 batch-limit 截断影响），只统计不建绑定；
- 元数据变化 → `save(synced(...))`；未变化/缺失 → `updateSyncState`（只改两列）；
- 单组异常 `catch RuntimeException` → `failed += group.size()`，不中断其它组；
- 手动对账：仅 admin、仅本租户、写审计；调度对账：全租户、不写审计、日志 + 指标。

### 6.2 提醒

- `remindDue()`：`now-24h` 之前创建、`PENDING`、且（无提醒记录或 `last_reminded_at <= now-12h`）；
- 每条：`markReminded`（UPDATE 0 行再 INSERT）+ 审计（userId=0 表示系统）+ Counter；
- 单条写失败只 WARN，继续下一条；视图与调度解耦（`listPendingCreatedBefore`）。

### 6.3 检索语义与指标

- `KnowledgeQaUseCase`：未绑定 404 → 停用 404 → **MISSING 404**（消息含 removed on enterprise side）；
- `KnowledgeSearchTool`：同样的第二道防线；成功 `recordCounter(hit.count, size)` +
  `recordDuration(SUCCESS)`，异常只记 `FAILURE` 耗时。

### 6.4 enterprise

- `bases` 两个参数恰好一个，否则 400；按企业用户列库先 `userPort.findById`，
  用户不存在 → 404（平台计 failed，不标 MISSING）。

## 7. 重构

| 重构 | 原因 | 结果 |
|------|------|------|
| 拆出 `KnowledgeBindingGroupReconciler` | 对账用例 256 行 > 200 | 用例 194 行，差异逻辑可独立推演 |
| 拆出 `KnowledgeReconcileReporter` | 指标口径混在用例里 | 4 个 Counter + Gauge 单点维护 |
| 拆出 `PlatformApprovalRowMapper` | JDBC 适配器加提醒后 212 行 | 适配器 185 行 |
| 拆出 `KnowledgeSearchTelemetry` | 检索工具 204 行 | 工具 196 行，指标口径集中 |
| `KnowledgeBinding` 保留 10 参构造器 | Week 25 调用点/测试不被迫改 | 旧代码零改动，默认 `UNKNOWN` |

## 8. 质量门禁

| 门禁 | 结果 |
|------|------|
| 编译（platform + enterprise，含测试源码） | 通过（无输出 = BUILD SUCCESS） |
| platform 模块全量 | **690 用例，0 失败**（Week 26 基线 648，+42） |
| enterprise 模块全量 | **70 用例，0 失败**（Week 26 基线 65，+5） |
| 根聚合 | **971 用例，0 失败**（Week 26 基线 924，+47） |
| H2 迁移 | 首次 `Successfully applied 10 migrations ... now at version v10`；重启 `validated 10 migrations` |
| 超 200 行类 | 本周边界内 0 个（见第 7 节） |
| 禁用/跳过用例 | 0（未使用 `@Disabled`） |
| 云端终检 | **PASS=29 FAIL=0**（服务 / V10 对象 / 对账 / 提醒证据 / 指标 / 配置恢复 / 资源） |

> 根聚合分布：ai-core 90 / enterprise 70 / patient 21 / platform 690 / gateway 100 = **971**。

## 9. 验证证据

| 证据 | 位置 |
|------|------|
| 新增用例首轮（69 个 platform 过滤集） | `logs/week-27/test/target-week27-new-tests.log` |
| enterprise 定向（15 个） | `logs/week-27/test/target-week27-enterprise-tests.log` |
| platform 全量（690） | `logs/week-27/test/target-week27-platform-full2.log` |
| 根聚合（971） | `logs/week-27/test/target-week27-full.log` |
| 首次 V10 迁移 | platform 全量日志 `Successfully applied 10 migrations ... now at version v10` |
| 接口示例 | `docs/postman/week-27.postman_collection.json`（8 个请求） |
| 云端巡检 | `logs/week-27/deploy/target-week27-precheck.log` |
| 云端备份 | `logs/week-27/deploy/target-week27-backup.log`（`/opt/ai-code/backup/week27-20261025-145732`） |
| 云端发布 | `logs/week-27/deploy/target-week27-deploy-{enterprise,platform}.log` |
| 云端验收 | `logs/week-27/acceptance/target-week27-accept.log`（PASS=28，1 处脚本断言误判） |
| 提醒/指标证据 | `logs/week-27/acceptance/target-week27-reminder-metrics-check.log` |
| 配置恢复 | `logs/week-27/acceptance/target-week27-cleanup-restore.log` |
| 云端终检 | `logs/week-27/acceptance/target-week27-postcheck.log`（**PASS=29 FAIL=0**） |

## 10. 风险与下周输入

| # | 风险 / 待办 | 影响 | 下一步 |
|---|-------------|------|--------|
| 1 | 云端已应用 V10（15:01:27） | — | 已解决；回滚用 `/opt/ai-code/backup/week27-20261025-145732/{platform,enterprise}-app.jar` + `deploy-app.ps1 -Rollback` |
| 2 | 调度对账默认每日 02:15 | 企业侧改名最长 1 天可见 | 需要更快可改 cron；或让 admin 手动对账 |
| 3 | `MISSING` 绑定需要人工清理 | 长期残留会让运营困惑 | 观察一周；若量大再加「批量清理」端点 |
| 4 | 提醒只覆盖站内查询 | 不主动推送 | 前端接入后加轮询/WebSocket；短信/邮件等供应商 |
| 5 | 指标未配告警 | 对账失败/提醒风暴只能看盘 | 下周按真实噪声加 2 条 Prometheus 规则（见架构第 6 节） |
| 6 | 多实例部署 | 调度重复执行（对账幂等、提醒有冷却） | 多实例前加分布式锁或改单实例调度 |
| 7 | 科室级授权仍未做 | 一租户内知识库全可见 | 等真实组织架构需求（P1 #2） |

## 11. 决策记录 ADR

| 决策 | 选项 | 选择 | 理由 |
|------|------|------|------|
| 对账用哪个身份 | 导入人 `platformUserId` / 绑定里的 `enterpriseUserId` | **企业用户** | 导入人离职/解绑不应让对账失效 |
| 删库处理 | 自动删绑定 / 标 MISSING / 忽略 | **标 MISSING** | 保留历史与审计；检索显式 404；删除仍由 admin 决策 |
| 新增库处理 | 自动绑定 / 只统计 | **只统计 `unbound`** | 绑定决定租户可见性，不能自动扩散 |
| 上游失败 | 标 UNKNOWN / 保持原状态 | **保持** | 一次抖动不能把线上知识库全判死 |
| 提醒存储 | 扩 `platform_approval` / 新表 | **新表** | 提醒是运维记账，不污染审批聚合与状态机 |
| 超时动作 | 自动拒绝 / 只提醒 | **只提醒** | 高风险患者分析不能被系统静默终止 |
| 提醒去重 | 每次都发 / 冷却窗口 | **12h 冷却** | 兼顾不遗漏与不刷屏；视图始终可查 |
| 指标标签 | 租户/知识库 ID / 只 outcome | **只 outcome/result** | 避免序列随租户/知识库数膨胀 |

## 12. 云端发布与真机验收（2026-10-25）

### 12.1 顺序与结果

| 步骤 | 时间（CST） | 结果 |
|------|-------------|------|
| 只读巡检 | 14:57:08 | 平台 v9 / enterprise 1..7；绑定 1；无待审；两侧令牌一致；无 V10 |
| 备份 | 14:57:32 | `/opt/ai-code/backup/week27-20261025-145732`（171M，两库 dump 非空） |
| 发布 enterprise | 14:59:15 | `/api/v1/auth/me` 401；按企业用户列库 200、缺参 400 |
| 发布 platform | 15:01:32 | health 200；V10 于 15:01:27 `now at version v10` |
| 手动对账 | 15:03:27 | `groups=1 / checked=1 / updated=1 / failed=0`；绑定 `UNKNOWN → ACTIVE` |
| 超时提醒 | 15:04:00 | 回填 30h 待审 → `reminder_count=1`、审计 `APPROVAL_REMIND`、指标 =1、审批仍 `PENDING` |
| 知识库问答 | 15:03 / 15:06 | `COMPLETED / hitCount=2 / citations=2`（两次） |
| 终检 | 15:08 | **PASS=29 FAIL=0** |

备份同时包含两个旧 jar 与 `.env`；`app.jar.prev` 也保留，回滚只需
`.\tools\deploy-app.ps1 -Target platform -Rollback`（enterprise 同理），或从备份目录取 jar。

### 12.2 真机抓出的 3 个脚本/环境问题（非产品缺陷）

1. **`.env` 不能当 shell 脚本 source**：临时把提醒 cron 改成 `0 */1 * * * *` 写进 `.env` 后，
   验收脚本 `source .env` 时把 `*/1` 当命令执行（`*/1: No such file or directory`），
   `set -e` 下直接中断。`.env` 是 Spring 的 properties 源，不是 shell；
   临时改 cron 应走 systemd drop-in/环境变量，或在 source 前剥离该行（本次已恢复 `.env` 并校验与备份一致）。
2. **Bash `EUID` 是只读变量**：检查脚本用 `EUID` 接企业用户 ID 失败，改用 `ENT_UID`。
3. **`pipefail` + `grep -q` 的 SIGPIPE**：`echo 139KB 的 scrape | grep -q` 会让 grep 提前退出、
   上游收到 SIGPIPE，管道返回 141，指标断言随机失败（与 Week 26 同款）。
   修复：改 bash 模式匹配 / 直接 grep 文件，并给 scrape 加 3 次重试（`curl --max-time 30`）。

另：Micrometer 计数器随进程重启归零属正常；终检前重新跑了一次对账 + 问答生成指标
（`knowledge_search_duration/hit_count/reconcile`），提醒计数器以重启前
`target-week27-reminder-metrics-check.log` 为证（DB 行与审计仍在）。

### 12.3 最终生产状态

- 三个服务 `active`，health 200 / enterprise 401；Flyway `v10`；
  `sync_status` / `last_synced_at` 列、`platform_approval_reminder` 表与外键存在；
- 绑定 `ACTIVE` 且 `lastSyncedAt` 非空；超时提醒行 `count=1`、审计 1 条、审批仍 `PENDING`；
- 演示用 P002 待审审批保留（`created_at` 已复原为 15:03:44，未超时），可直接在审批页决策；
- `.env` 与备份逐字节一致，临时 cron 已移除；可用内存 > 500MB、根盘 < 90%。

### 12.4 下周输入

- 加 2 条 Prometheus 规则（对账 failed 连续、MISSING 持续 > 0），按真实噪声调；
- `MISSING` 批量清理 / `unbound` 明细仍等运营反馈；
- Week 28 主题：TD-06 平台用户停用 + P2 类拆分；
- **Week 29 主题：最小可用前端**（已定；平台 8 页 + 可选企业知识库页，同域 nginx；不做 SSE），
  计划见 `docs/specs/week-29-candidates.md`；SSE/科室授权/导出/HIS-LIS 顺延 30+。
