# Week 06 架构图 — ai-core 共享库 + patient-agent（Tool Calling）

> 对应 Spec：`docs/specs/week-06.md`  
> 对应代码：`libs/ai-core`、`apps/enterprise-knowledge-agent`、`apps/patient-agent`

## 1. 模块依赖关系

```mermaid
flowchart LR
    subgraph apps["apps"]
        PA["patient-agent<br/>第6周 Tool Calling"]
        EKA["enterprise-knowledge-agent<br/>第4周 RAG 知识库"]
        SAD["spring-ai-demo<br/>第1-3周（不动，保留自含）"]
    end
    subgraph libs["libs"]
        CORE["ai-core<br/>横切能力共享库"]
    end
    PA --> CORE
    EKA --> CORE
```

## 2. ai-core 分层（com.aicode.core）

```mermaid
flowchart TB
    subgraph core["libs/ai-core (com.aicode.core)"]
        M["domain.model<br/>ChatMessage/ChatOptions/ChatResult/FinishReason/TokenUsage/MessageRole<br/>PromptTemplate/PromptDescriptor<br/>DocumentChunk/VectorSearchHit/TokenRecord/TokenStats/User<br/>OcrResult/OcrFileType/FileContent/FileReference/StorageType<br/>ToolCall/ToolDefinition/ToolResult"]
        P["domain.port<br/>ChatModelPort/PromptTemplatePort/EmbeddingModelPort/VectorStorePort<br/>DocumentOcrPort/FileStoragePort/AuthTokenPort/PasswordHasher<br/>AuditPort/ImageDownloadPort/ToolPort"]
        D["domain<br/>Tool/ToolRegistry/TextChunker/RagContextAssembler/FileUrlAssembler/OcrMarkdownImageProcessor"]
        E["domain.exception<br/>ChatModelException/EmbeddingException/OcrException/FileStorageException<br/>AuthenticationException/NotFoundException/InvalidChatRequestException/ToolExecutionException"]
        I["infrastructure<br/>llm/prompt/embedding/vector/ocr/security/storage 适配器"]
        C["infrastructure.config<br/>LlmProperties/EmbeddingProperties/RagProperties/OcrProperties/AuthProperties/FileProperties/PromptProperties + AiCoreConfiguration"]
    end
```

## 3. patient-agent Tool Calling 数据流

```mermaid
sequenceDiagram
    participant U as Client
    participant C as AgentController
    participant UC as AgentRunUseCase
    participant A as ReActAgent
    participant M as ChatModelPort(ai-core)
    participant R as ToolRegistry(ToolPort)
    participant T as PatientTool/ReportTool/HealthDataTool/OrderTool
    participant D as PatientDataPort(内存种子)

    U->>C: POST /api/v1/agents/runs {task}
    C->>UC: run(task)
    UC->>A: run(AgentTask)
    A->>M: chat(messages, options, tools=definitions())
    M-->>A: ChatResult(finishReason, toolCalls)
    loop finishReason == tool_calls
        A->>R: execute(toolCall)
        R->>T: dispatch by name
        T->>D: 查询患者/报告/指标/医嘱
        D-->>T: 业务数据
        T-->>R: ToolResult(JSON)
        R-->>A: ToolResult
        A->>A: append assistant(tool_calls) + tool(tool_call_id, result)
        A->>M: chat(messages, options, tools)
        M-->>A: ChatResult
    end
    A-->>UC: AgentResult(answer, steps, totalUsage)
    UC-->>C: AgentResult
    C-->>U: 200 data {taskId, answer, steps, totalSteps, model, usage}
```

## 4. 各层职责

| 层 | 职责 | 示例类 |
|----|------|--------|
| 接入层 | HTTP 路由、参数校验、协议转换 | `AgentController`、`GlobalExceptionHandler` |
| 应用层 | 用例编排：任务校验、taskId 生成 | `AgentRunUseCase` |
| 领域层 | Function Calling 循环编排、值对象、端口 | `ReActAgent`、`AgentStep`、`PatientDataPort` |
| 工具层 | 具体业务工具（定义 JSON Schema + 执行） | `PatientTool`/`ReportTool`/`HealthDataTool`/`OrderTool` |
| ai-core | 模型/Prompt/工具契约/Embedding/向量/RAG/OCR/鉴权/文件/审计 | `ToolRegistry`、`OpenAiCompatibleChatModelAdapter` 等 |
| 数据层 | 患者业务数据（内存种子，可替换 DB） | `InMemoryPatientDataAdapter` |

## 5. 工具调用链（关键咽喉点）

`ReActAgent` → `ToolPort`（`ToolRegistry`）→ 按 name 分发 → 具体 `Tool` → `PatientDataPort`。

`ToolRegistry` 是**所有工具执行的唯一咽喉点**，后续权限（第 13 周）、脱敏/Guardrails（第 14 周）、限流在此统一拦截。

## 6. 配置与实现对应关系

```yaml
prompt.version:        → ClasspathPromptTemplateAdapter（ai-core，对应 prompts/{name}-v1.txt）
llm.base-url/api-key:  → OpenAiCompatibleChatModelAdapter（ai-core）
agent.max-iterations:  → AgentRuntimeConfig.maxIterations（循环兜底）
```

## 7. YAGNI 边界

- 患者业务数据本周为**内存种子 + 端口**，DB 持久化留待第 9/12 周
- `spring-ai-demo` 未收敛到 ai-core，保持独立
- `LocalFileStorageAdapter`/`MyBatisAuditAdapter` 等绑定 app 持久化的适配器留在 enterprise
- SaToken 拦截器（路径规则）留在 enterprise，ai-core 只提供 Sa-Token 适配器
