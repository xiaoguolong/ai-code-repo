# Week 02 架构图 — spring-ai-demo

> 对应 Spec：`docs/specs/week-02.md`  
> 对应代码：`apps/spring-ai-demo`

## 1. 分层架构

```mermaid
flowchart TB
    subgraph 接入层["接入层"]
        C["ChatController<br/>POST /api/v1/chats"]
        MC["MessageController<br/>GET /chats/{id}/messages"]
        PC["PromptController<br/>GET /prompts"]
        TSC["TokenStatsController<br/>GET /token-stats"]
    end

    subgraph 应用层["应用层"]
        UC["ChatUseCase"]
        LUC["ListMessagesUseCase"]
        PUC["ListPromptsUseCase"]
        TSU["TokenStatsUseCase"]
    end

    subgraph 领域层["领域层"]
        PP["PromptTemplatePort"]
        MP["ChatModelPort"]
        AP["AuditPort"]
        MemP["MemoryPort"]
        CP["ConversationPort"]
        SOP["StructuredOutputPort"]
        CASM["ChatContextAssembler"]
    end

    subgraph 基础设施层["基础设施层"]
        PT["ClasspathPromptTemplateAdapter<br/>LangChain4j"]
        LLM["OpenAiCompatibleChatModelAdapter<br/>手写 HTTP"]
        AUD["MyBatisAuditAdapter<br/>Fluent-MyBatis"]
        MemIn["InMemoryMemoryAdapter"]
        MemRed["RedisMemoryAdapter<br/>Spring Data Redis"]
        Conv["MyBatisConversationAdapter<br/>Fluent-MyBatis"]
        JSON["JacksonStructuredOutputAdapter<br/>Jackson"]
    end

    subgraph 外部依赖["外部依赖"]
        PG["PostgreSQL"]
        RD["Redis"]
        DS["DeepSeek / OpenAI 兼容 API"]
    end

    C --> UC
    MC --> LUC
    PC --> PUC
    TSC --> TSU

    UC --> PP
    UC --> MP
    UC --> AP
    UC --> MemP
    UC --> CP
    UC --> SOP
    UC --> CASM

    LUC --> CP
    PUC --> PP
    TSU --> AP

    PP --> PT
    MP --> LLM
    AP --> AUD
    MemP --> MemIn
    MemP --> MemRed
    CP --> Conv
    SOP --> JSON

    AUD --> PG
    Conv --> PG
    MemRed --> RD
    LLM --> DS
```

## 2. 一次聊天请求的完整数据流

```mermaid
sequenceDiagram
    participant U as Client
    participant C as ChatController
    participant UC as ChatUseCase
    participant CP as ConversationPort
    participant MP as MemoryPort
    participant PP as PromptTemplatePort
    participant CASM as ChatContextAssembler
    participant LLM as ChatModelPort
    participant SOP as StructuredOutputPort
    participant AP as AuditPort

    U->>C: POST /api/v1/chats
    C->>UC: ChatCommand

    UC->>CP: ensureSession(sessionId, model)
    CP->>PG: INSERT/SELECT chat_session

    UC->>MP: load(sessionId)
    alt Redis 命中
        MP-->>UC: 返回历史消息
    else Redis 未命中
        UC->>CP: list(sessionId)
        CP->>PG: SELECT chat_message
        CP-->>UC: 返回历史消息
        UC->>MP: replace(sessionId, 历史)
    end

    UC->>PP: load(template)
    PP->>PT: classpath prompts/{name}-v1.txt
    PT-->>PP: PromptTemplate
    PP-->>UC: 系统提示

    UC->>CASM: assemble(system, history, user, maxWindow)
    CASM-->>UC: messages[]

    UC->>LLM: chat(messages, options)
    LLM->>DS: HTTP /chat/completions
    DS-->>LLM: content + usage
    LLM-->>UC: ChatResult

    UC->>CP: append(user)
    UC->>CP: append(assistant)
    CP->>PG: INSERT chat_message

    UC->>MP: replace(sessionId, 新窗口)

    alt responseFormat=json
        UC->>SOP: parseObject(content)
        SOP-->>UC: Map<String, Object>
    end

    UC->>AP: record(ChatAuditRecord)
    AP->>PG: INSERT token_record

    UC-->>C: ChatOutcome
    C-->>U: 200 + ApiResponse
```

## 3. 各层职责

| 层 | 职责 | 示例类 |
|----|------|--------|
| 接入层 | HTTP 路由、参数校验、协议转换 | `ChatController` |
| 应用层 | 业务用例编排、事务边界 | `ChatUseCase` |
| 领域层 | 领域模型、端口接口、业务规则 | `ChatMessage`、`MemoryPort`、`ChatContextAssembler` |
| 基础设施层 | 外部服务适配、数据库、缓存、文件 | `JpaConversationAdapter`、`RedisMemoryAdapter` |

## 4. 中间件使用位置

| 中间件 | 对应代码 | 说明 |
|--------|----------|------|
| PostgreSQL | `infrastructure.persistence.*`<br>`infrastructure.persistence.mapper.*`<br>`db/migration/V2__chat_tables.sql` | 三张表：chat_session、chat_message、token_record |
| Redis | `infrastructure.memory.RedisMemoryAdapter` | 可选，存 `chat:memory:{sessionId}` |
| LangChain4j | `infrastructure.prompt.ClasspathPromptTemplateAdapter` | Prompt 模板渲染 |
| Jackson | `infrastructure.structured.JacksonStructuredOutputAdapter` | JSON 输出解析 |
| OpenAI 兼容 API | `infrastructure.llm.OpenAiCompatibleChatModelAdapter` | 第1周保留 |

## 5. 配置与实现对应关系

```yaml
llm.api-key:            → OpenAiCompatibleChatModelAdapter
llm.base-url:           → OpenAiCompatibleChatModelAdapter
llm.model:              → ChatRuntimeConfig / ChatModelPort

spring.datasource.*:    → MyBatisConversationAdapter / MyBatisAuditAdapter
spring.flyway.*:        → db/migration/V2__chat_tables.sql
spring.data.redis.*:    → RedisMemoryAdapter

chat.memory-provider:   → MemoryPort 实现切换
chat.audit-provider:    → AuditPort 实现切换（mybatis / memory）
chat.max-memory-messages: → ChatContextAssembler 截断窗口
```

## 6. YAGNI 边界

本周**没有引入**以下能力，后续周次按需扩展：

- RAG / Embedding / 向量数据库
- Agent / Tool Calling / Workflow
- 登录鉴权 / RBAC
- 网关 / 注册中心 / Kafka
- OpenTelemetry / Langfuse / SkyWalking
