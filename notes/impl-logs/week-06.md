# Week 06 实现日志 — ai-core 共享库抽取 + Tool Calling（原生 Function Calling）

- 日期：2026-09-19
- 批次：1
- 对应 Spec：docs/specs/week-06.md

## 1. 本周目标

1. 抽取 `libs/ai-core` 共享库（包 `com.aicode.core`），收敛第 1–4 周横切能力；`enterprise-knowledge-agent` 与 `patient-agent` 改为依赖它，删除各自内部复制。
2. 在 `patient-agent` 落地 Tool Calling（机制 A：原生 Function Calling），实现 `PatientTool/ReportTool/HealthDataTool/OrderTool`。

## 2. 边界

- Always：原生 Function Calling（tools/tool_calls）；工具调用为结构化事件入 trace；`ToolRegistry` 单一分发点；参数 JSON Schema 校验；中文 JavaDoc；单测不打真实网络、无密钥入库；ai-core 无 main
- Never：改 JAVA_HOME、密钥入库、用户输入拼系统提示、ai-core 依赖 app 模块、给 patient-agent 加 DB/登录/Redis

## 3. 增量架构

见 Spec `docs/specs/week-06.md` 与 `docs/architecture/week-06-architecture.md`。

## 4. 新增/变更类型清单

### ai-core（com.aicode.core，60 拷贝 + 6 新增）

- 从 enterprise 收敛：模型（DocumentChunk/VectorSearchHit/TokenRecord/TokenStats/User/OcrResult/OcrFileType/FileContent/FileReference/StorageType）、端口（Embedding/Vector/Ocr/File/Auth/Audit/ImageDownload）、异常、TextChunker/RagContextAssembler/FileUrlAssembler/OcrMarkdownImageProcessor、embedding/vector/ocr/security/storage 适配器、Embedding/Rag/Ocr/Auth/File 配置
- 从 patient 收敛（第 5 周新版）：ChatMessage/ChatOptions/ChatResult/TokenUsage/MessageRole/PromptTemplate/PromptDescriptor、ChatModelPort/PromptTemplatePort、OpenAiCompatibleChatModelAdapter/OpenAiChatResponseMapper、ClasspathPromptTemplateAdapter、LlmProperties
- 新增：`PromptProperties`、`FinishReason`、`Tool`、`ToolRegistry`、`ToolExecutionException`、`AiCoreConfiguration`
- 模型升级：`MessageRole`+TOOL、`ChatMessage`+toolCallId/toolCalls、`ChatResult`+toolCalls/finishReason、`ToolCall`+id、`ToolDefinition`+parameters（JSON Schema）、`ChatModelPort`+tools 重载

### patient-agent

- 升级：`ReActAgent`（原生 Function Calling 循环）、`AgentStep`（工具调用轨迹）、`AgentStepDto`/`AgentRunResponse`、`GlobalExceptionHandler`（+TOOL_EXECUTION_ERROR）
- 新增：`PatientDataPort`/`InMemoryPatientDataAdapter`、`Patient`/`Report`/`HealthMetric`/`Order`、`PatientTool`/`ReportTool`/`HealthDataTool`/`OrderTool`/`PatientToolSupport`
- 删除：`ReActOutputParser`、`ReActTurn`（文本解析被原生 Function Calling 取代）

### enterprise-knowledge-agent

- 删除 43 个重复类，import 收敛到 ai-core；`AppConfiguration` 精简（只留 MyBatis/时钟/运行时配置/OcrMarkdownImageProcessor）；`ChatAppProperties` 去掉 `systemPromptVersion`

## 5. RED

- 先写 spec + 抽取，再逐模块编译；未先写测试（本次为大规模重构 + 增量功能，重构先行）。

## 6. GREEN

- 命令（根聚合）：`.\run-maven-jdk17.ps1 clean install`（等价 `mvn -Djdk.17.home=... clean install`）
- 结果：`ai-core 36 / enterprise 21 / patient 16` 全绿，`BUILD SUCCESS`
- 单模块独立构建需先 `mvn install` ai-core 到本地仓库

## 7. 重构

- 用 UTF-8 无 BOM 的 .NET File API 做批量包名替换，避免 PowerShell `Set-Content -Encoding UTF8` 引入 BOM 与中文乱码（首次尝试因编码问题全部中文注释损坏，已重做）。
- 搬移「绑定 app 持久化」的适配器决定：`LocalFileStorageAdapter`/`MyBatis*Adapter` 留在 enterprise（绑定 file_record 表与 fluent-mybatis）；`SaTokenConfig`（路径规则）留在 enterprise。

## 8. 质量门禁

- [x] 根聚合 `clean test` 全绿（68 个测试）
- [x] 无密钥入库（`api-key: ${LLM_API_KEY:}` 仅环境变量）
- [x] 分层未突破：ai-core 无 Spring Boot 启动类；Controller 无 SDK/SQL
- [x] public 类型中文 JavaDoc
- [x] 未改系统/用户 JAVA_HOME
- [x] 无 System.out/err 打印
- [x] `spring-ai-demo` 未被改动（保持独立）

## 9. 验证证据

```
[INFO] Reactor Summary for ai-code-repo 0.1.0-SNAPSHOT:
[INFO] ai-core ............................................ SUCCESS [ 11.419 s]
[INFO] enterprise-knowledge-agent ......................... SUCCESS [ 21.518 s]
[INFO] patient-agent ...................................... SUCCESS [ 28.448 s]
[INFO] ai-code-repo ....................................... SUCCESS [  2.028 s]
[INFO] BUILD SUCCESS
```

新增测试覆盖：

- ai-core：`OpenAiChatResponseMapper`（tool_calls/finish_reason 解析、reasoning_content 回退）、`ToolRegistry`（按 name 分发、未知工具、非法参数）
- patient：`ReActAgent`（工具调用后收敛返回答案+轨迹、超迭代、空白输出）、`PatientTool`（定义、命中、未命中）、`BusinessToolsTest`（Report/HealthData/Order 返回业务数据）、`PatientAgentApplicationTest`（完整上下文冒烟：ai-core + ToolRegistry + 4 工具 + ReActAgent 装配）

## 10. 风险与下周输入

- **业务数据仍是内存种子**：`PatientDataPort` + `InMemoryPatientDataAdapter`，DB 持久化留待第 9 周 Workflow 或第 12 周平台前引入。
- **Thought 轨迹丢失**：原生 Function Calling 不再输出显式 `Thought`，trace 只记录工具调用（name/arguments/result）。如需推理过程，后续可引入 reasoning 模型或让模型在工具调用前附一句说明。
- **ai-core 依赖已拆分**：jdbc / sa-token 标记为 `optional` + `@ConditionalOnClass` 守卫，patient-agent 不再被强制拉入；web / langchain4j 为两 app 真实共享，保留为常规依赖。enterprise 自行声明 jdbc / sa-token。
- **下周（第 7 周 Agent Memory）**：把 `ReActAgent` 的跨任务上下文/历史任务接入 Redis/向量记忆；工具调用轨迹可作为长期记忆的素材。
