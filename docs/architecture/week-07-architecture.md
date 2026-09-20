# Week 07 架构图 — Agent Memory（短期会话记忆 + 长期向量记忆）

> 对应 Spec：`docs/specs/week-07.md`
> 对应代码：`libs/ai-core`、`apps/patient-agent`

## 1. 模块依赖关系

```mermaid
flowchart LR
    subgraph apps["apps"]
        PA["patient-agent<br/>第5-7周（Agent + Memory）"]
        EKA["enterprise-knowledge-agent<br/>第4周（RAG）"]
        SAD["spring-ai-demo<br/>第1-3周（不动）"]
    end
    subgraph libs["libs"]
        CORE["ai-core<br/>第6周抽取 + 第7周 Memory"]
    end
    PA --> CORE
    EKA --> CORE
```

## 2. ai-core 记忆分层（本周新增）

```mermaid
flowchart TB
    subgraph core["libs/ai-core (com.aicode.core)"]
        direction TB
        MM["domain.model<br/>上周：ChatMessage/ChatResult/ToolCall...<br/>新增：MemoryRecord / MemoryHit"]
        MP["domain.port<br/>上周：ChatModelPort/ToolPort/EmbeddingModelPort...<br/>新增：MemoryPort / LongTermMemoryPort"]
        MD["domain 服务<br/>新增：MemoryWindow / MemoryContextAssembler / VectorMath"]
        MI["infrastructure.memory<br/>新增：InMemoryConversationMemoryAdapter<br/>RedisConversationMemoryAdapter<br/>InMemoryLongTermMemoryAdapter"]
        MC["infrastructure.config<br/>新增：MemoryProperties（AiCoreConfiguration 注册）"]
        MM --- MP --- MD --- MI --- MC
    end
```

## 3. patient-agent 记忆数据流

```mermaid
sequenceDiagram
    participant U as Client
    participant C as AgentController
    participant UC as AgentRunUseCase
    participant SP as MemoryPort(短期)
    participant LP as LongTermMemoryPort(长期)
    participant E as EmbeddingModelPort
    participant A as ReActAgent
    participant M as ChatModelPort
    participant R as ToolRegistry

    U->>C: POST /api/v1/agents/runs {sessionId?, task}
    C->>UC: run(sessionId, task)
    UC->>SP: load(sessionId)
    SP-->>UC: 历史消息
    UC->>UC: MemoryWindow.trim(history, max-messages)
    UC->>LP: search(task, topK)
    LP->>E: embed(task)
    E-->>LP: query 向量
    LP-->>UC: MemoryHit[]（相似历史任务）
    UC->>A: run(AgentTask, history, recalled)
    A->>A: messages = system + history + user(召回记忆 + 当前任务)
    loop Function Calling
        A->>M: chat(messages, options, tools)
        M-->>A: tool_calls / final answer
        A->>R: execute(toolCall)（第6周链路）
        R-->>A: ToolResult
    end
    A-->>UC: AgentResult(answer, steps, usage, recalledMemories)
    UC->>SP: append(sessionId, user + assistant)
    UC->>LP: save(MemoryRecord(task, answer))
    UC-->>C: AgentResult
    C-->>U: 200 data {sessionId, answer, steps, recalledMemories, usage}
```

## 4. 各层职责

| 层 | 职责 | 示例类 |
|----|------|--------|
| 接入层 | HTTP 路由、参数校验、协议转换 | `AgentController`、`GlobalExceptionHandler` |
| 应用层 | 用例编排：任务校验、sessionId、记忆读写 | `AgentRunUseCase` |
| 领域层 | Function Calling 循环 + 上下文装配 | `ReActAgent`、`AgentTask`、`AgentResult` |
| 记忆层（ai-core） | 记忆契约、窗口截断、召回拼装、余弦 | `MemoryPort`、`LongTermMemoryPort`、`MemoryWindow`、`MemoryContextAssembler`、`VectorMath` |
| 记忆适配（ai-core） | 内存 / Redis 短期，内存向量长期 | `InMemoryConversationMemoryAdapter`、`RedisConversationMemoryAdapter`、`InMemoryLongTermMemoryAdapter` |
| ai-core 其余 | 模型 / Prompt / 工具契约 / Embedding / 向量 / RAG / OCR / 鉴权 / 文件 / 审计 | 第 6 周已收敛 |

## 5. 关键咽喉点与安全

- 短期记忆只存 `user` / `assistant` 文本；`system` 提示始终来自 `prompts/agent-v1.txt`。
- 召回的历史任务通过 `MemoryContextAssembler` 作为 **user 消息正文** 注入（与 `RagContextAssembler` 同构），绝不拼进 system 提示，避免用户输入污染指令。
- `memory.max-messages` 限制会话窗口，防止上下文无限增长、Token 失控。
- `RedisConversationMemoryAdapter` 键带前缀 + TTL，脏数据回退空列表，不抛异常打断主流程。

## 6. 配置与实现对应关系

```yaml
memory.short-term-provider: memory|redis   → MemoryPort 适配器选择
memory.long-term-provider: memory          → LongTermMemoryPort 适配器选择
memory.max-messages: 20                    → AgentRuntimeConfig.maxMemoryMessages → MemoryWindow
memory.long-term-top-k: 3                  → AgentRuntimeConfig.longTermTopK → LongTermMemoryPort.search
memory.redis-ttl-hours: 24                 → RedisConversationMemoryAdapter TTL
embedding.provider: hashing|openai         → InMemoryLongTermMemoryAdapter 的向量来源（ai-core，第6周已有）
```

## 7. YAGNI 边界

- 长期记忆本周为**内存向量 + 端口**，不落 pgvector / DB（patient-agent 仍无 DB，留第 12 周）。
- 不改第 6 周工具链与 `ToolRegistry`。
- 不给 `enterprise-knowledge-agent` 接记忆（会话历史已由 `ConversationPort` 落库，属第 4 周范畴）。
- `spring-ai-demo` 保持独立，不收敛到 ai-core。
