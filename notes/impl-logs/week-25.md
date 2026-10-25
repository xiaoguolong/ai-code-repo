# Week 25 实现日志 — 知识库 Agent 3 薄接入（平台包装 enterprise RAG）

- 日期：2026-10-25
- 批次：1
- 对应 Spec：`docs/specs/week-25.md`
- 对应架构：`docs/architecture/week-25-architecture.md`
- 对应接口：`docs/api/week-25-api.md`
- 部署手册：`docs/deploy/week-25-knowledge.md`

## 1. 本周目标

按第 24 周结论落地「包装 enterprise RAG」：

1. **enterprise 只读内部端点**：`/api/v1/internal/knowledge/{bases,search}`，`X-Internal-Token` fail closed；
2. **平台知识库绑定（V9）**：租户 →（企业知识库 + 企业归属人）的映射，导入/列表/删除；
3. **平台 Agent 3（`knowledge-qa`）**：`KnowledgeSearchTool` 检索 → 平台模型生成**带引用**答案，
   走 RBAC / Guardrail / 执行记录 / 观测；未绑定 404、0 命中不调模型、模型失败降级摘录。

## 2. 边界

- **Always（已做）**：内部令牌 + 只读端点、V9、绑定导入/列表/删除、检索工具、Agent、单测/文档。
- **Never（未做）**：平台原生知识库（文档/切片/向量表）、代理 enterprise 问答接口、
  知识库自动同步、租户内按科室授权、pgvector 云端安装（运维窗口）。

## 3. 为什么只代理检索、不代理问答

`enterprise` 的 `POST /knowledge-bases/{id}/chats` 会自己调模型、写自己的会话与 Token 记录。
平台若直接转发，会丢掉 Guardrail、审计、执行记录与成本归属——等于在治理上开一个后门。
因此内部端点只返回切片，答案由平台生成（Prompt `knowledge-qa`，要求逐条带引用）。

## 4. 新增类型清单

| 层 | 类型 | 名称 |
|----|------|------|
| enterprise | 控制器/用例 | `InternalKnowledgeController`、`InternalKnowledgeUseCase` |
| enterprise | 安全/配置 | `InternalTokenGuard`、`InternalProperties`（`enterprise.internal.token`） |
| enterprise | 模型/DTO | `InternalKnowledgeSearchCommand`、`InternalKnowledgeHit/Result/Catalog/BaseView`、`InternalSearchRequest`、`InternalKnowledgeResponse` |
| platform/domain | 模型 | `KnowledgeBinding`、`KnowledgeSearchQuery/Hit`、`KnowledgeBaseCatalog(+Entry)`、`KnowledgeAnswer`、`KnowledgeCitation`、`KnowledgeStep` |
| platform/domain | Port | `KnowledgeBindingPort`、`KnowledgeSearchPort` |
| platform/domain | 服务 | `KnowledgeQaAgent` |
| platform/application | UseCase | `KnowledgeBindingUseCase`、`KnowledgeQaUseCase`、`KnowledgeImportResult`、`KnowledgeRuntimeConfig` |
| platform/infra | Adapter | `JdbcKnowledgeBindingAdapter`、`InMemoryKnowledgeBindingAdapter`、`EnterpriseKnowledgeSearchAdapter` |
| platform/infra | Tool | `KnowledgeSearchTool`（ToolRegistry 自动收集） |
| platform/infra | 配置 | `PlatformKnowledgeProperties`、`KnowledgeConfiguration` |
| platform/controller | API | `PlatformKnowledgeBindingController` + 4 个 DTO |
| 迁移 | V9 | `platform_knowledge_binding` |
| Prompt | `knowledge-qa-v1.txt` | 只依据片段 / 必带引用 / 不编造 |

## 5. RED（先写测试，真实失败记录）

| 失败 | 原因 | 修法 |
|------|------|------|
| `SpringAiAlibabaAgentApplicationTest.wiresFrameworkBeans` | 工具集从 3 变 4（新增 `KnowledgeSearchTool`），旧断言写死 3 | 断言改为 4，并显式断言包含 `KnowledgeSearchTool` |
| `PlatformSeedIdempotencyTest` / `PlatformRbacPermissionUpgradeInitializerTest` | Agent 6→7、operator 授权多了 `knowledge-qa` / `KnowledgeSearchTool` | 按新契约更新（契约变更，不是妥协） |

## 6. GREEN（改动文件）

- **enterprise**：`InternalKnowledgeUseCase` + 4 个模型/结果记录、`InternalKnowledgeController`、2 个 DTO、
  `InternalTokenGuard`/`InternalProperties`、`SaTokenConfig`（`/api/v1/internal/**` 白名单）、
  `AppConfiguration` 注册、`application.yml` + `.env.example`；
- **平台**：V9 迁移、`KnowledgeBinding` + Port + Jdbc/InMemory 适配器、
  `knowledge/**`（模型/端口/适配器/Agent/用例/配置/Prompt）、`KnowledgeSearchTool`、
  绑定控制器 + DTO、`AgentType.KNOWLEDGE_QA`、`PlatformAgentRunner` 第 8 分支与构造函数、
  seed（Agent/工具）、RBAC seed + 升级、Guardrail 参数白名单、`application.yml` + `.env.example`；
- **脚本/文档**：`deploy/app/cloud-accept-week25.sh`、`docs/deploy/week-25-knowledge.md`、
  Spec/架构/API/Postman/README/索引。

## 7. 重构

- `KnowledgeRuntimeConfig` 最初放在 `knowledge.infrastructure`，domain 的 Agent 引用它造成层次反向；
  改为 `knowledge.application`（与 `PatientAnalysisRuntimeConfig` 一致）；
- `PlatformAgentRunner` 继续用「多构造器兼容」：3 参 / 5 参 / 8 参（`@Autowired`），既有测试无需改；
- 绑定校验放两层：用例层（模型调用前 404）+ 工具层（防绕过）；这是有意的重复，不是遗漏。

## 8. 质量门禁

- [x] 编译：`test-compile` BUILD SUCCESS
- [x] 单测：根聚合 **924** 全绿（见第 9 节）
- [x] JDK：未改 `JAVA_HOME`
- [x] 分层：enterprise 内部端点只读、不调模型；平台领域无 HTTP 类型；SQL 只在适配器
- [x] 安全：令牌不入库、不打印；`MessageDigest.isEqual`；未配置即 403
- [x] YAGNI：无新依赖（RestClient 已有）、无 MQ/缓存/流式
- [x] 数据库脚本：V9 双库可跑；H2 首次 `now at version v9`
- [x] 日志：本文 + `docs/logs-index.md`

## 9. 验证证据

### 9.1 根聚合

```
ai-core 90 / enterprise 65 / patient 21 / spring-ai-alibaba-agent 648 / agent-gateway 100
合计 924，Failures 0，Errors 0，BUILD SUCCESS
（Week 24 基线 885 → 924，本周 +39：enterprise +10、platform +29）
原始日志：logs/week-25/test/target-week25-full.log
```

### 9.2 V9（H2）

```
Successfully applied 9 migrations ... now at version v9
后续上下文：Successfully validated 9 migrations
```

### 9.3 关键行为

| 行为 | 测试 |
|------|------|
| 内部端点无令牌/错令牌 403；未映射 404；他人库 404；topK 夹取 | `InternalKnowledgeUseCaseTest`（6）/ `InternalKnowledgeControllerTest`（4） |
| 绑定导入幂等、改名更新、租户隔离、admin-only、删除范围 | `KnowledgeBindingUseCaseTest`（6） |
| 工具：未绑定/停用/无 Run 范围拒绝；topK 夹取；JSON 契约 | `KnowledgeSearchToolTest`（6） |
| Agent：带引用答案、0 命中不调模型、模型降级、工具失败 | `KnowledgeQaAgentTest`（5） |
| 用例：绑定校验先于模型、入参校验、无范围拒绝 | `KnowledgeQaUseCaseTest`（6） |
| HTTP：导入→列表→问答 200；未绑定 404；viewer 403；删除权限；401 | `PlatformKnowledgeHttpTest`（6） |

## 10. 风险与下一步

| # | 项 | 说明 |
|---|----|------|
| 1 | **云端 pgvector 未装** | 代码就绪但云端检索会在 SQL 层失败（502）；按 `docs/deploy/week-25-knowledge.md` 装扩展 + 补 V6 |
| 2 | 云端未发布/未验收 | jar 与 `.env` 未动；`cloud-accept-week25.sh` 已备 |
| 3 | 绑定是快照 | 企业侧改名/删库需重新导入 |
| 4 | 租户内全可见 | 科室级隔离需要 `platform_role_knowledge` |
| 5 | 依赖 enterprise 在线 | 无熔断/缓存；中断即 502（有意，不缓存医疗答案） |
| 6 | 引用正文出站 | 已过 Guardrail 输出脱敏；如需更严格可只回传摘要 |

> 第 1、2 项（pgvector、云端发布）与第 24 周遗留（V8 验收、TD-04/TD-05）已统一排入
> [`docs/specs/week-26-candidates.md`](../../docs/specs/week-26-candidates.md) 的 P0 上线收口周。

## 11. ADR

| 决策 | 选项 | 选择 | 理由 |
|------|------|------|------|
| 包装范围 | 问答 / 仅检索 | 仅检索 | 平台生成答案才能进治理链 |
| 身份 | 会话透传 / 内部令牌 + 绑定快照 | 后者 | 不要求逐用户 SSO；令牌可轮换、fail closed |
| 租户 | 改企业库 / 平台绑定表 | 绑定表 | 不动企业 schema |
| 检索失败 | 空结果 / 502 | 502 | 「没搜到」与「检索坏了」必须区分 |
| 0 命中 | 调模型 / 直接返回 | 直接返回 | 无依据作答=编造 |
| 模型失败 | 报错 / 摘录降级 | 摘录降级 | 用户至少拿到原文与出处 |
| 网关位置 | 每用户映射 / 绑定记 enterpriseUserId | 后者 | 一次导入、全租户可用 |
