# Week 03 架构图 — RAG 知识库

> 对应 Spec：`docs/specs/week-03.md`  
> 对应代码：`apps/spring-ai-demo`

## 1. 分层架构

```mermaid
flowchart TB
    subgraph 接入层["接入层"]
        DC["DocumentController<br/>POST/GET /api/v1/documents"]
        RC["RagController<br/>POST /api/v1/rag/chats"]
        CC["ChatController<br/>POST /api/v1/chats（已有）"]
    end

    subgraph 应用层["应用层"]
        IUC["IngestDocumentUseCase"]
        AUC["AskKnowledgeUseCase"]
        LDC["ListDocumentsUseCase"]
        CUC["ChatUseCase（已有）"]
    end

    subgraph 领域层["领域层"]
        EP["EmbeddingModelPort"]
        VP["VectorStorePort"]
        DP["DocumentPort"]
        TC["TextChunker"]
        RCA["RagContextAssembler"]
        MP["ChatModelPort（已有）"]
        PP["PromptTemplatePort（已有）"]
    end

    subgraph 基础设施层["基础设施层"]
        HE["HashingEmbeddingAdapter<br/>默认 离线确定性"]
        OE["OpenAiCompatibleEmbeddingAdapter"]
        IM["InMemoryVectorStoreAdapter<br/>默认 余弦检索"]
        PG["PgVectorStoreAdapter<br/>仅生产"]
        MBD["MyBatisDocumentAdapter"]
        LLM["OpenAiCompatibleChatModelAdapter（已有）"]
        PT["ClasspathPromptTemplateAdapter（已有）"]
    end

    subgraph 外部依赖["外部依赖"]
        PGD[(PostgreSQL + pgvector)]
        EMB["Embedding API（OpenAI 兼容）"]
        DS["DeepSeek"]
    end

    DC --> IUC
    DC --> LDC
    RC --> AUC
    CC --> CUC

    IUC --> EP
    IUC --> VP
    IUC --> DP
    IUC --> TC
    AUC --> EP
    AUC --> VP
    AUC --> RCA
    AUC --> MP
    AUC --> PP
    LDC --> DP

    EP --> HE
    EP --> OE
    VP --> IM
    VP --> PG
    DP --> MBD
    MP --> LLM
    PP --> PT

    PG --> PGD
    MBD --> PGD
    OE --> EMB
    LLM --> DS
```

## 2. 文档入库数据流

```mermaid
sequenceDiagram
    participant U as Client
    participant DC as DocumentController
    participant IUC as IngestDocumentUseCase
    participant DP as DocumentPort
    participant TC as TextChunker
    participant EP as EmbeddingModelPort
    participant VP as VectorStorePort

    U->>DC: POST /api/v1/documents
    DC->>IUC: IngestDocumentCommand(name, content)
    IUC->>IUC: 校验 name/content 非空白
    IUC->>DP: create(documentId, name)
    DP->>PGD: INSERT document
    IUC->>TC: split(content, chunkSize, overlap)
    TC-->>IUC: List<String> chunks
    loop 每个 chunk
        IUC->>EP: embed(chunk)
        EP-->>IUC: float[]
        IUC->>VP: put(chunk, vector)
        VP->>PGD: INSERT document_chunk（仅 pgvector 实现）
    end
    IUC-->>DC: IngestDocumentOutcome(documentId, chunkCount)
    DC-->>U: 200 data
```

## 3. RAG 问答数据流

```mermaid
sequenceDiagram
    participant U as Client
    participant RC as RagController
    participant AUC as AskKnowledgeUseCase
    participant EP as EmbeddingModelPort
    participant VP as VectorStorePort
    participant RCA as RagContextAssembler
    participant PP as PromptTemplatePort
    participant MP as ChatModelPort

    U->>RC: POST /api/v1/rag/chats
    RC->>AUC: KnowledgeQuestion(question, topK)
    AUC->>EP: embed(question)
    EP-->>AUC: queryVector
    AUC->>VP: search(queryVector, topK)
    VP-->>AUC: List<VectorSearchHit>
    AUC->>PP: load("rag")
    PP-->>AUC: system 提示
    AUC->>RCA: buildUserMessage(hits, question)
    RCA-->>AUC: user 消息（参考资料 + 问题）
    AUC->>MP: chat(system + user, options)
    MP->>DS: /chat/completions
    DS-->>MP: content + usage
    MP-->>AUC: ChatResult
    AUC-->>RC: KnowledgeAnswer(answer, sources)
    RC-->>U: 200 data
```

## 4. 各层职责

| 层 | 职责 | 示例类 |
|----|------|--------|
| 接入层 | HTTP 路由、参数校验、协议转换 | `DocumentController`、`RagController` |
| 应用层 | 用例编排：入库 / 检索增强问答 | `IngestDocumentUseCase`、`AskKnowledgeUseCase` |
| 领域层 | 端口接口、切片、上下文组装 | `EmbeddingModelPort`、`TextChunker`、`RagContextAssembler` |
| 基础设施层 | 厂商适配、向量库、数据库、文件 | `HashingEmbeddingAdapter`、`PgVectorStoreAdapter`、`MyBatisDocumentAdapter` |

## 5. 中间件使用位置

| 中间件 | 对应代码 | 说明 |
|--------|----------|------|
| pgvector | `infrastructure.vector.PgVectorStoreAdapter`<br>`db/postgresql/V4__document_chunk_pgvector.sql` | 仅生产；`RAG_VECTOR_STORE=pgvector` 时启用 |
| PostgreSQL | `infrastructure.persistence.MyBatisDocumentAdapter`<br>`db/migration/V3__document_table.sql` | `document` 表 |
| Embedding（阿里云百炼） | `infrastructure.embedding.OpenAiCompatibleEmbeddingAdapter` | `EMBEDDING_PROVIDER=openai` 时启用，默认 `qwen3.7-text-embedding-flash`（1024 维） |
| 离线向量化 | `infrastructure.embedding.HashingEmbeddingAdapter` | 默认，确定性词袋哈希 |
| LangChain4j | 不涉及（本周 Embedding 走手写 HTTP） | Prompt 渲染沿用第 2 周 |
| DeepSeek | `infrastructure.llm.OpenAiCompatibleChatModelAdapter`（已有） | RAG 最终回答 |

## 6. 配置与实现对应关系

```yaml
embedding.provider:      → HashingEmbeddingAdapter / OpenAiCompatibleEmbeddingAdapter 切换
embedding.base-url:      → OpenAiCompatibleEmbeddingAdapter
embedding.api-key:       → OpenAiCompatibleEmbeddingAdapter（密钥，只走环境变量）
embedding.model:         → OpenAiCompatibleEmbeddingAdapter
embedding.dimension:     → HashingEmbeddingAdapter 向量维度（与 pgvector 列一致，固定 1024）

rag.vector-store:        → VectorStorePort 实现切换（memory / pgvector）
rag.default-top-k:       → AskKnowledgeUseCase 默认检索条数
rag.chunk-size:          → TextChunker 切片窗口
rag.chunk-overlap:       → TextChunker 切片重叠

spring.flyway.locations: → db/migration（共享）+ db/postgresql（仅生产）
```

## 7. YAGNI 边界

本周**没有引入**以下能力，后续周次按需扩展：

- 意图识别 / 路由（先识别意图再决定是否检索，属第 5 周 Agent）
- PDF / Word / Office 解析（Tika / POI，本周纯文本）
- RAG 问答的 Token 审计（留待第 4 周企业知识库 Agent）
- 跨端口入库事务（入库中断的孤儿切片处理，留待第 4 周）
- 登录鉴权 / RBAC、多知识库隔离
