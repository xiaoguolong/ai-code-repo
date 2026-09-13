# Week 05 实现日志 — Agent 基础（ReAct 循环：Reason → Action → Observation）

- 日期：2026-09-13
- 批次：1
- 对应 Spec：docs/specs/week-05.md
- 对应阅读：无（Agent 概念为本周编码主线，独立笔记留待后续补）

## 1. 本周目标

把第 4 周「单轮问答 + RAG」升级为会自主规划、逐步执行、自我迭代的 Agent，落地独立模块 `apps/patient-agent`：

1. ReAct 循环（Thought → Action → Observation），多轮迭代直到产出 Final Answer。
2. 返回完整步骤轨迹（trace）+ 汇总 Token 用量。
3. 预留 `ToolPort`（第 6 周 Tool Calling 才实现）。
4. 无状态、无 DB、无登录的单机 Agent 演示；单测不打真实网络、无密钥入库。

## 2. 边界

- Always：ReAct 多轮循环、返回轨迹 + 汇总 Token、`ReActOutputParser` 纯函数可离线单测、`maxIterations` 兜底防死循环、中文 JavaDoc、单测不打真实网络、无密钥入库、`ToolPort` 仅接口预留
- Never：改 JAVA_HOME、密钥入库、用户输入拼系统提示、Controller 写 Prompt/业务规则、给 patient-agent 加 DB/登录、实现 Tool 调用（第 6 周）、抽共享库（本次已拍板暂不抽）

## 3. 增量架构

见 Spec `docs/specs/week-05.md` 的「增量架构」两张图（分层 / ReAct 时序）。

## 4. 新增类型清单

| 类型 | 名称 | 职责 |
|------|------|------|
| Domain | ReActAgent | ReAct 循环编排：组装上下文 → 调模型 → 解析 → 记录步骤 → 收敛/超限 |
| Domain | ReActOutputParser | 纯函数解析 Thought/Action/Observation/Final Answer |
| Domain | AgentTask / AgentStep / AgentResult / ReActTurn | 任务、单步轨迹、最终结果、单轮解析结果 |
| Domain | ToolCall / ToolDefinition / ToolResult | 第 6 周 Tool Calling 值对象（仅定义） |
| Domain | MessageRole / ChatMessage / ChatOptions / ChatResult / TokenUsage / PromptTemplate / PromptDescriptor | 模型层与 Prompt 值对象（自含复用） |
| Port | ToolPort | 工具契约，第 6 周实现，本周无适配器 |
| Port | ChatModelPort / PromptTemplatePort | 模型层 + Prompt（自含复用） |
| UseCase | AgentRunUseCase | 校验任务、生成 taskId、编排 ReActAgent |
| Config | AgentRuntimeConfig | model/temperature/maxTokens/maxIterations |
| Adapter | OpenAiCompatibleChatModelAdapter / OpenAiChatResponseMapper | OpenAI 兼容 Chat Completions（自含复用） |
| Adapter | ClasspathPromptTemplateAdapter | 加载 prompts/agent-v1.txt |
| Config | AgentProperties / LlmProperties / AppConfiguration | 配置映射 + Bean 装配 |
| DTO | AgentRunRequest / AgentRunResponse / AgentStepDto / AgentUsageDto / ApiResponse | 请求/响应体 |
| Exception | ChatModelException / InvalidChatRequestException / AgentLoopExceededException / AgentExecutionException | 领域异常 |

## 5. RED

- 命令：`mvn -Djdk.17.home=... test`（经 `run-maven-jdk17.ps1` 等价内联）
- 结果：编译失败（预期）
- 原因：先写 4 个测试类（`ReActOutputParserTest` / `ReActAgentTest` / `AgentRunUseCaseTest` / `OpenAiChatResponseMapperTest`），引用的 `ReActOutputParser`、`ReActTurn`、`ReActAgent`、`AgentTask`、`ChatResult`、`TokenUsage`、`OpenAiChatResponseMapper` 等符号尚未实现，报「找不到符号」。

## 6. GREEN

- 改动文件：新建模块 `apps/patient-agent` 全量（domain 14 模型 + 3 端口 + 4 异常 + 2 领域服务、application 1 用例 + 1 配置、controller 2 个、dto 5 个、infrastructure 5 个、application.yml + application-test.yml、prompts/agent-v1.txt、pom、run-maven-jdk17.ps1、.mvn/maven.config）
- 命令：`mvn -Djdk.17.home=... test`
- 结果：`Tests run: 16, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`
- 打包：`mvn -q -DskipTests package` 成功，产出 `patient-agent-0.1.0-SNAPSHOT.jar`（约 23 MB）

## 7. 重构

- 解析逻辑收敛为纯函数 `ReActOutputParser`，与循环解耦，可独立单测。
- 多轮 Token 汇总收敛为 `TokenUsage.plus`，避免循环内手写累加。
- 空白输出/超迭代分别用 `AgentExecutionException` / `AgentLoopExceededException` 表达，不塞进 `ChatModelException`。

## 8. 质量门禁

- [x] 编译（`-q test` 编译 + 打包均成功）
- [x] 单测 16/16（含 @WebMvcTest 控制器契约 2 条）
- [x] 无密钥入库（`api-key: ${LLM_API_KEY:}` 仅环境变量，源码无 sk-/硬编码密钥）
- [x] 分层未突破（Controller 无 SDK/SQL；domain 无 Spring 注解，ReActAgent/ReActOutputParser 由 AppConfiguration 装配）
- [x] public 类型中文 JavaDoc
- [x] 未改系统/用户 JAVA_HOME；沿用 JDK17 参数方式
- [x] YAGNI：无 Tool 实现、无 Memory/Redis、无 DB/登录、无 LangChain4j 编排框架
- [x] 无 System.out/err 打印

## 9. 验证证据

```
Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

新增测试覆盖：

- domain：ReActOutputParser（含 Final Answer 全字段 / 非终止 / 缺字段补空 / 空白 / 多行答案）；ReActAgent（收敛返回答案+2 步+汇总 Token、超迭代抛 AgentLoopExceededException、空白输出抛 AgentExecutionException）
- application：AgentRunUseCase（空白任务 400、委托返回、taskId 非空）
- infrastructure：OpenAiChatResponseMapper（content/model/usage、缺 usage 记 0、无 choices 抛异常）
- controller：AgentController（200 + data 信封、空白任务 400 + VALIDATION_ERROR）

## 10. 风险与下周输入

- 本周 Action 为模型内部推理（观察值来自模型自述），非真实工具；第 6 周把 `ReActAgent` 的 Action 升级为调用 `ToolPort`，观察值来自工具返回。
- ReAct 输出解析依赖模型的固定文本格式（Thought/Action/Observation/Final Answer），模型不按格式输出时可能一直不收敛，靠 `maxIterations` 兜底；后续可引入结构化输出（Structured Output / Function Calling）替代文本解析。
- 无上下文记忆：每步把助手输出追加回消息列表作为「工作记忆」，但跨任务无持久记忆（第 7 周 Agent Memory）。
- 无状态、无鉴权：执行轨迹随响应返回，持久化执行记录与权限属第 12 周 Agent 平台。
- **已排入第 6 周计划**：抽取 `ai-core` 共享库，enterprise-knowledge-agent 与 patient-agent 改为依赖它。后续约定：**新应用在旧模块基础上改造，不再复制代码**。抽取范围不只是模型层/Prompt，而是把第 1–4 周沉淀的横切能力一次性收敛，避免越积越乱：
  1. **模型层**：`ChatModelPort`/`ChatMessage`/`ChatOptions`/`ChatResult`/`TokenUsage`/`MessageRole` + `OpenAiCompatibleChatModelAdapter` + `OpenAiChatResponseMapper` + `LlmProperties`
  2. **Prompt 层**：`PromptTemplatePort`/`PromptTemplate`/`PromptDescriptor` + `ClasspathPromptTemplateAdapter`
  3. **Embedding**：`EmbeddingModelPort` + `HashingEmbeddingAdapter`/`OpenAiCompatibleEmbeddingAdapter` + `EmbeddingProperties`
  4. **向量库**：`VectorStorePort`/`DocumentChunk`/`VectorSearchHit` + `InMemoryVectorStoreAdapter`/`PgVectorStoreAdapter`
  5. **RAG 通用件**：`TextChunker`/`RagContextAssembler`
  6. **OCR**：`DocumentOcrPort`/`OcrResult`/`OcrFileType` + `PaddleOcrVlAdapter`/`PaddleOcrResponseMapper`/`OcrMarkdownImageProcessor` + `OcrProperties`
  7. **Token 登录鉴权**：`AuthTokenPort`/`PasswordHasher`/`User` + `SaTokenAuthTokenAdapter`/`SaTokenMd5PasswordHasher`/`SaTokenConfig` + `AuthProperties`
  8. **文件存储**：`FileStoragePort`/`FileReference`/`FileContent`/`StorageType` + `LocalFileStorageAdapter`/`FileUrlAssembler` + `FileProperties`
  9. **审计（Token 统计）**：`AuditPort`/`TokenRecord`/`TokenStats`

## 11. 决策记录 ADR

| 决策 | 选项 | 选择 | 理由 |
|------|------|------|------|
| 共享库抽取 | 抽 ai-core / 模块内自含 | 暂不抽库（模块内自含复制） | 用户拍板：等最后一个应用再统一抽，避免跨模块重构 |
| Action 语义 | 纯推理循环 + ToolPort 预留 / 内置演示假 Tool | 纯推理循环 + ToolPort 预留 | 第 6 周才做 Function Calling；本周 Action=模型内部推理，可离线单测 |
| 编排框架 | Spring AI / LangChain4j Agent / 手写 ReAct | 手写 ReAct 循环 | 第 8 周才引框架；手写透明可测、符合分层 |
| 执行轨迹存储 | 落库 / 随响应返回 | 随响应返回（无状态） | 本周无 DB；持久化属第 12 周 Agent 平台 |
| patient-agent 鉴权 | 登录 / 公开 | 公开 | 单机 Agent 演示；企业鉴权已由第 4 周 enterprise 承载 |

---

# 批次 2 — 联调修复与调优

- 日期：2026-09-13
- 背景：IDEA 启动 patient-agent（8082）真实调用后，首次请求返回 500 `AGENT_EXECUTION_ERROR`（`agent produced empty output`）。

## 修复 1：模型返回空 content 的兜底与诊断

- 现象：`ChatModelPort.chat` 调用成功但 `content` 为空，`ReActAgent` 判定执行失败。
- 改动（TDD）：
  - `OpenAiChatResponseMapper`：`content` 为空时回退读取 `reasoning_content`（DeepSeek reasoner 兼容）。
  - `OpenAiCompatibleChatModelAdapter`：content 为空时 WARN 记录原始响应（不含密钥），便于定位。
- 验证：`OpenAiChatResponseMapperTest` 新增 `fallsBackToReasoningContentWhenContentEmpty`，全量 17/17 通过。

## 修复 2：截断与循环重复

- 现象：真实响应 `answer` 末尾被截断（`max-tokens: 1024`）；steps 1/3/4 的 thought 重复退化；observation 混入自我指涉文本。
- 改动：
  - `application.yml`：`max-tokens` 1024 → 2048，缓解最终答案截断。
  - `prompts/agent-v1.txt`：强化收敛规则——每回合 Thought 必须有新进展、禁止重复、信息足以结论即输出 Final Answer、信息不足直接在 Final Answer 说明。
- 备注：文本格式 ReAct 的重复/格式漂移是固有短板，根治需第 6 周结构化输出（Function Calling）。
