# Week 21 阅读提纲 — 多租户、统一身份与「平台级 Agent」

> 配套：`docs/specs/week-21.md`｜`docs/architecture/week-21-architecture.md`｜`notes/impl-logs/week-21.md`
> 目标不是背概念，而是回答「为什么这周只能这样做」。

## 1. SaaS 多租户：三种隔离强度

| 形态 | 做法 | 优点 | 代价 |
|------|------|------|------|
| 共享库共享表（**本周采用**） | 每行带 `tenant_key`，所有查询强制带谓词 | 迁移成本最低，一套 DDL | 漏写谓词就是跨租户泄露，靠代码规范 + 单测兜底 |
| 共享库独立 schema | 每租户一套 schema | 逻辑隔离更清晰 | Flyway 要为每租户跑一遍，连接池要切 schema |
| 独立库 | 一租户一库 | 最强隔离 | 运维成本随租户线性增长 |

本周选第一种的理由：平台当前只有一家演示机构 + 一家对照机构，数据量以「行」计；
用第三种会把时间全花在连接管理上。关键不是选哪种，而是**把隔离写成不可绕过的形状**——
所以 `PlatformMemoryPort` 每个读方法都要求传 `PlatformMemoryScope`，接口层面就没有「不带 scope 的查询」。

思考题：如果让你为「患者表」也加租户，你会把 `tenant_key` 加在 `platform_user` 上还是每张业务表上？（提示：前者只隔离账号，后者才隔离数据）

## 2. 统一身份：认证与授权要分开

- **认证（Authentication）**：你是谁。本周把「你是谁」收敛到平台一个入口（`/auth/verify`）。
- **授权（Authorization）**：你能做什么。enterprise 的文档归属、平台的 RBAC 各自负责，**不合并**。

为什么 enterprise 不直接用平台的 token：token 一旦跨系统共享，就出现「谁能撤销、多久过期、泄露了算谁的」三个问题；
只用一次性的「凭据校验」拿身份，会话留在各自系统，边界最清楚。

为什么不做共用用户表：见 Spec 三的选项对照——真正的成本不是建表，而是 `app_user` 上已有的外键与历史数据迁移。

思考题：SSO 登录时平台 `/auth/verify` 返回 401，enterprise 应该返回 401 还是 503？如果返回 401，用户会怎么做？

## 3. Agent 的「平台化」：从能跑到可治理

同一个能力，两种做法：

| 做法 | 得到什么 | 缺什么 |
|------|----------|--------|
| 直连 `/api/v1/multi-agent/runs`（Week 11） | 跑得通 | 无 RBAC、无执行记录、无审计、无指标、无记忆 |
| 注册为平台 Agent（Week 21） | 以上全有 | 多一层注册/权限配置 |

本周把患者分析做成 `AgentType.PATIENT_ANALYSIS` 后，**一行治理代码都没写**就获得了第 13–20 周的全部能力：
这是前面 20 周分层与端口设计的利息，不是本周的功劳。

思考题：`medical-assistant` 与 `patient-analysis` 的能力高度重叠，为什么还要留两个？删除其中一个的代价是什么？

## 4. 降级：把「谁生成的」写在数据里

本周 LLM 不可用时，患者分析仍返回 200，但：
- `degraded=true`；
- 报告正文顶部与结尾都标明「模型不可用 / 规则模板生成」；
- `usage` 记 0（不能假装消耗了 token）。

不这样做的后果：审计里分不清「模型说的」和「规则拼的」；Langfuse 里这次 trace 会缺失却查不出原因。

思考题：如果某个报告同时包含「规则结论」与「模型扩写」，`degraded` 该是 true 还是 false？你会在报告里怎么标注？

## 5. 记忆：为什么先不做 pgvector

| 维度 | 本周（文本向量 + Java 余弦） | pgvector |
|------|------------------------------|----------|
| 单测 | H2 就能跑 | 需要真实 PG + 扩展 |
| 库改动 | 只加表 | `CREATE EXTENSION` + `vector` 列 + HNSW 索引 |
| 规模 | 每用户几百条，候选集 500 | 百万级仍可 |

结论：**先保证隔离正确，再谈检索性能**。端口已经把存储形态隔离，换实现只改一个适配器。

思考题：如果某租户的记忆量涨到 100 万条，你会先加索引、先分表，还是先换 pgvector？判断依据是什么？

## 6. 本周必读代码（按顺序）

1. `platform/domain/model/PlatformMemoryScope.java` —— 隔离的最小单位；
2. `platform/domain/port/PlatformMemoryPort.java` —— 接口如何让「漏传 scope」编译不过；
3. `platform/infrastructure/persistence/JdbcPlatformMemoryAdapter.java` —— 谓词落在 SQL 里；
4. `platform/application/PlatformMemoryUseCase.java` —— admin 换 scope 的边界；
5. `analysis/domain/PatientAnalysisAgent.java` —— 确定性步骤 + 一次模型调用；
6. `enterprise/application/SsoLoginUseCase.java` —— 建链、开户、冲突三种分支；
7. `patient/infrastructure/deprecation/DeprecatedApiFilter.java` —— 弃用如何做到「零行为改动」。
