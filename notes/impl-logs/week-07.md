# Week 07 实现日志 — Agent Memory（短期会话记忆 + 长期向量记忆）

- 日期：2026-09-20
- 批次：1
- 对应 Spec：docs/specs/week-07.md
- 对应阅读：无（Agent Memory 为本周编码主线，独立笔记留待后续补）

## 1. 本周目标

让第 6 周无状态的 `patient-agent` 具备记忆：

1. **短期记忆**：同一 `sessionId` 的多轮 user/assistant 上下文，按 `memory.max-messages` 截断后随每次任务注入。默认进程内实现，可切 Redis（`memory.short-term-provider=redis`）。
2. **长期记忆**：把每次任务的「任务 + 结论」向量化保存，新任务到来时按余弦相似度召回相关历史任务注入上下文。
3. 记忆契约与适配器下沉 `libs/ai-core`（与第 6 周 Tool 契约同理），`patient-agent` 只做编排。

## 2. 边界

- Always：短期 user/assistant 窗口 + 截断；长期「任务 + 结论」语义召回；历史任务只作 user 消息数据（禁止拼 system）；记忆端口化（内存/Redis/向量可替换）；中文 JavaDoc；单测不打真实网络、不连真实 Redis；无密钥入库
- Never：改 `JAVA_HOME`；密钥入库；用户输入拼系统提示；Controller 写 Prompt/业务规则；ai-core 依赖 app 模块；长期记忆落 DB/pgvector（patient-agent 仍无 DB）

## 3. 增量架构

见 Spec `docs/specs/week-07.md` 与 `docs/architecture/week-07-architecture.md`。

## 4. 新增/变更类型清单

### ai-core（com.aicode.core）

| 类型 | 名称 | 职责 |
|------|------|------|
| Model | `MemoryRecord` | 长期记忆记录：memoryId/sessionId/task/answer/createdAt |
| Model | `MemoryHit` | 语义检索命中：record + score |
| Port | `MemoryPort` | 短期会话记忆：load/append/clear，永不返回 null |
| Port | `LongTermMemoryPort` | 长期记忆：save/search |
| Domain | `MemoryWindow` | 纯函数：保留最近 N 条历史 |
| Domain | `MemoryContextAssembler` | 召回历史任务 + 当前任务拼 user 消息（不进 system） |
| Domain | `VectorMath` | 余弦相似度纯函数，供向量库与向量记忆复用 |
| Config | `MemoryProperties` | `memory.*`：provider / max-messages / long-term-top-k / redis-ttl-hours |
| Adapter | `InMemoryConversationMemoryAdapter` | 进程内短期记忆（默认） |
| Adapter | `RedisConversationMemoryAdapter` | Redis 短期记忆（TTL，`@ConditionalOnClass` 守卫） |
| Adapter | `InMemoryLongTermMemoryAdapter` | 内存向量长期记忆（复用 `EmbeddingModelPort`） |

- 变更：`AiCoreConfiguration` 注册 `MemoryProperties`；`InMemoryVectorStoreAdapter` 复用 `VectorMath.cosine`（去重）；ai-core `pom.xml` 新增 `spring-boot-starter-data-redis`（optional）。

### patient-agent

- 变更：`AgentTask`（+`sessionId`）、`AgentResult`（+`sessionId`/`recalledMemories`）、`ReActAgent`（`run(task, history, recalled)` 装配记忆上下文）、`AgentRunUseCase`（加载/截断/召回 → 运行 → 写回短期 + 保存长期）、`AgentRuntimeConfig`（+`maxMemoryMessages`/`longTermTopK`）、`AppConfiguration`（映射 `MemoryProperties`）、`AgentRunRequest`（+可选 `sessionId`）、`AgentRunResponse`（+`sessionId`/`recalledMemories`）、`AgentController`（透传 sessionId）
- 依赖：`spring-boot-starter-data-redis`；`application.yml` 新增 `memory` / `embedding` / `spring.data.redis` 配置

## 5. RED

- 命令：`.\run-maven-jdk17.ps1 -q -pl libs/ai-core test`
- 结果：编译失败（预期）
- 原因：先写 6 个 ai-core 测试（MemoryWindowTest / MemoryContextAssemblerTest / VectorMathTest / InMemoryConversationMemoryAdapterTest / InMemoryLongTermMemoryAdapterTest / RedisConversationMemoryAdapterTest），引用的 `MemoryRecord`、`MemoryHit`、`MemoryWindow`、`MemoryContextAssembler`、`VectorMath`、`InMemory*`、`RedisConversationMemoryAdapter` 尚未实现，且 `org.springframework.data.redis.core` 包不存在，报「找不到符号 / 程序包不存在」。

## 6. GREEN

- 改动文件：ai-core 新增 11 个类 + 2 处变更 + pom；patient-agent 变更 9 个类 + pom + yml；新增测试 6 个（ai-core）+ 更新 4 个（patient）
- 命令（根聚合）：`.\run-maven-jdk17.ps1 test`
- 结果：`ai-core 59 / enterprise 21 / patient 18` 全绿，`BUILD SUCCESS`

## 7. 重构

- 抽出 `VectorMath.cosine`，`InMemoryVectorStoreAdapter` 与 `InMemoryLongTermMemoryAdapter` 共用，消除重复余弦实现。
- 记忆读写留在 `AgentRunUseCase`（应用层编排），`ReActAgent` 只负责「给定上下文跑循环」，保持领域服务可单测。
- 召回历史任务复用 RAG 同构思路（`MemoryContextAssembler` ~ `RagContextAssembler`），统一「检索上下文作为 user 数据」的约定。

## 8. 质量门禁

- [x] 根聚合 `clean test` 全绿（98 个测试）
- [x] 无密钥入库（`api-key: ${LLM_API_KEY:}` / `${EMBEDDING_API_KEY:}` 仅环境变量）
- [x] 分层未突破：ai-core 无 Spring Boot 启动类；Controller 无 SDK/SQL；记忆读写经端口
- [x] public 类型中文 JavaDoc
- [x] 未改系统/用户 `JAVA_HOME`（仅 `-Djdk.17.home`）
- [x] 无 System.out/err 打印
- [x] YAGNI：未引 Sa-Token Redis 会话、未给 patient-agent 加 DB、`spring-ai-demo`/enterprise 未改动

## 9. 验证证据

```
[INFO] Reactor Summary for ai-code-repo 0.1.0-SNAPSHOT:
[INFO] ai-core ............................................ SUCCESS [  9.696 s]
[INFO] enterprise-knowledge-agent ......................... SUCCESS [ 17.823 s]
[INFO] patient-agent ...................................... SUCCESS [ 17.316 s]
[INFO] ai-code-repo ....................................... SUCCESS [  0.001 s]
[INFO] BUILD SUCCESS
```

新增测试覆盖：

- ai-core：`MemoryWindow`（超窗保留最近 N、窗口内拷贝、null/空、非正数）、`MemoryContextAssembler`（拼历史任务进 user、无命中原样返回）、`VectorMath`（相同/正交/零向量/长度不齐）、`InMemoryConversationMemoryAdapter`（追加顺序、会话隔离、清空、未知会话空）、`InMemoryLongTermMemoryAdapter`（相关排前、topK、空查询/空库）、`RedisConversationMemoryAdapter`（缺 key、JSON 解析、脏数据回退、TTL 写入、清除）
- patient：`ReActAgent`（含历史 + 召回记忆进 user 消息、system 不被污染、recalledMemories 计数）、`AgentRunUseCase`（sessionId 生成、历史截断、召回透传、短期写回 + 长期保存、空任务 400）、`AgentController`（sessionId/recalledMemories 响应、空任务 400）、`PatientAgentApplicationTest`（MemoryPort/LongTermMemoryPort 装配）

## 10. 风险与下周输入

- **长期记忆为进程内**：重启即丢失；落 pgvector / DB 留待第 12 周 Agent 平台。
- **召回质量依赖 Embedding**：默认 `hashing` 仅词面匹配（中文需空格分词），生产须切 `embedding.provider=openai`。
- **全局召回无租户隔离**：patient-agent 单用户演示；多用户隔离留待第 13 周权限体系。
- **Redis 未做真实联调**：适配器用 Mock 模板单测覆盖协议与 TTL；生产启用需提供 Redis 并执行 `MEMORY_SHORT_TERM_PROVIDER=redis`。
- **下周（第 8 周 Spring AI Alibaba）**：记忆端口可被框架 Agent/Workflow 复用；注意保持 `MemoryPort` 与框架 Memory 抽象的映射。

## 11. 决策记录 ADR

| 决策 | 选项 | 选择 | 理由 |
|------|------|------|------|
| 记忆契约位置 | patient-agent / ai-core | ai-core | 与第 6 周 Tool 契约同理，记忆是 Agent 通用基础设施，第 11/12 周复用 |
| 短期默认实现 | Redis / 内存 | 内存默认、Redis 可切 | 单测不依赖本机 Redis；开箱即用，Redis 显式开启 |
| Redis 依赖形态 | 常规依赖 / optional | ai-core `optional` + `@ConditionalOnClass`，patient 显式声明 | 规范 5.8.1：仅个别适配器用的依赖不向 app 传递 |
| 召回记忆拼装位置 | system 提示 / user 消息 | user 消息 | 历史任务含用户输入，严禁进 system；与 `RagContextAssembler` 同构 |
| 长期记忆检索范围 | 按会话 / 全局 | 全局 | 长期记忆应跨会话生效；单用户无越权风险，隔离留第 13 周 |
| 记忆编排位置 | ReActAgent 内 / UseCase | UseCase 编排、ReActAgent 只装上下文 | 记忆读写是用例职责；领域服务保持「给定上下文跑循环」便于单测 |

---

# 批次 2 — 校验边界修复（sessionId 长度）

- 日期：2026-09-20
- 背景：Postman 第 6 条「sessionId 超长 → 400」实际用了一串**恰好 100 字符**的 `sessionId`，命中 `@Size(max = 100)` 的上界故返回 200 SUCCESS，与文档不符。

## 修复

- `docs/postman/week-07.postman_collection.json`：第 6 条改用 **120 字符** sessionId，并重命名为「sessionId 超长（120 字符）→ 400」。
- `AgentControllerTest` 新增 `rejectsSessionIdLongerThan100`：120 字符 sessionId → 400 + `VALIDATION_ERROR`（TDD：先加测试确认契约，再同步 Postman 数据）。

## 验证

- 命令：`.\run-maven-jdk17.ps1 -pl apps/patient-agent -am test`
- 结果：`ai-core 59 / patient 19` 全绿，`BUILD SUCCESS`
- 说明：`sessionId=100` 属合法上界（返回 200）；`>100` 才触发 Bean Validation 400。
