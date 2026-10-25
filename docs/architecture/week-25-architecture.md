# 架构：Week 25 — 知识库 Agent 3 薄接入（平台包装 enterprise RAG）

> 只画本周新增/改动的节点。第 1–24 周结构见 `docs/architecture/week-24-architecture.md`。

## 1. 组件与调用关系

```mermaid
flowchart TB
    subgraph P["平台 8084（治理方）"]
        BC["PlatformKnowledgeBindingController<br/>import / list / delete"]
        EC["PlatformExecutionController<br/>POST /agents/knowledge-qa/runs"]
        RUN["PlatformAgentRunner<br/>KNOWLEDGE_QA 分支"]
        QU["KnowledgeQaUseCase<br/>绑定校验（先于模型）"]
        AG["KnowledgeQaAgent<br/>检索 → 组装 → 模型 → 引用"]
        TL["KnowledgeSearchTool<br/>ToolRegistry + RBAC + Guardrail + 观测"]
        BP[("platform_knowledge_binding<br/>V9")]
        PORT["KnowledgeSearchPort"]
        ADP["EnterpriseKnowledgeSearchAdapter<br/>RestClient + 超时 + 502 映射"]
    end

    subgraph E["enterprise 8081（知识库方）"]
        IC["InternalKnowledgeController<br/>X-Internal-Token"]
        IU["InternalKnowledgeUseCase<br/>归属校验 + 向量检索"]
        KB[("knowledge_base")]
        DOC[("document")]
        CH[("document_chunk<br/>pgvector")]
        VS["VectorStorePort"]
        EM["EmbeddingModelPort"]
    end

    BC --> BP
    EC --> RUN --> QU --> AG --> TL
    TL --> BP
    TL --> PORT --> ADP
    ADP -->|"POST /api/v1/internal/knowledge/search"| IC
    ADP -->|"GET /api/v1/internal/knowledge/bases"| IC
    IC --> IU --> KB
    IU --> DOC
    IU --> EM
    IU --> VS --> CH
```

## 2. 问答时序（含三条失败路径）

```mermaid
sequenceDiagram
    participant Doc as 医生
    participant Ctl as Controller
    participant RU as PlatformExecutionUseCase
    participant QU as KnowledgeQaUseCase
    participant AG as KnowledgeQaAgent
    participant TL as KnowledgeSearchTool
    participant E as enterprise /internal
    participant LLM as 平台模型

    Doc->>Ctl: POST /agents/knowledge-qa/runs {knowledgeBaseId, question}
    Ctl->>RU: runAgent(userId, "knowledge-qa", input)
    RU->>RU: RBAC(agent) + Guardrail + 开 agent.run span
    RU->>QU: run(userId, kbId, question)
    QU->>QU: 查租户绑定
    alt 未绑定 / 已停用
        QU--xDoc: 404 PLATFORM_NOT_FOUND（不调模型、不烧 token）
    end
    QU->>AG: run(kbId, question)
    AG->>TL: execute(KnowledgeSearchTool, {knowledgeBaseId, query})
    TL->>TL: 再查绑定（第二道防线）+ 租户谓词
    alt enterprise 不可用 / 403 / 超时
        TL--xAG: ToolExecutionException
        AG--xDoc: 502 TOOL_EXECUTION_ERROR（执行记录 FAILED）
    end
    TL->>E: POST /internal/knowledge/search
    E-->>TL: hits[{documentId, name, chunkIndex, content, score}]
    alt 0 命中
        AG-->>Doc: 200 + 「未检索到相关内容」（不调模型）
    end
    AG->>AG: RagContextAssembler 组装上下文
    AG->>LLM: system(knowledge-qa) + 片段 + 问题
    alt 模型不可用 / 空返回
        LLM--xAG: ChatModelException
        AG-->>Doc: 200 + 原文摘录 + degraded=true
    end
    LLM-->>AG: 带引用答案
    AG-->>Doc: 200 + answer + citations + hitCount
```

## 3. 绑定导入时序

```mermaid
sequenceDiagram
    participant Admin as 管理员
    participant BC as BindingController
    participant BU as KnowledgeBindingUseCase
    participant ADP as KnowledgeSearchAdapter
    participant E as enterprise /internal
    participant DB as platform_knowledge_binding

    Admin->>BC: POST /knowledge-bindings/import
    BC->>BU: importFromEnterprise(adminUserId)
    BU->>BU: 校验 admin
    BU->>ADP: listBases(adminUserId)
    ADP->>E: GET /internal/knowledge/bases?platformUserId
    E->>E: sso_user_link 映射 → 企业用户
    E-->>ADP: {enterpriseUserId, bases[]}
    ADP-->>BU: catalog
    BU->>DB: upsert (tenant_key, knowledge_base_id) ← enterpriseUserId/name/description
    BU-->>Admin: {imported, updated, items}
```

- 导入幂等：`(tenant_key, knowledge_base_id)` 唯一索引 + 先查后写；
- 企业侧重命名只更新快照，不改变绑定身份；
- 一个租户可以绑定多个知识库；同一企业知识库不能绑到两个租户（`tenant_key` 在唯一键里，理论上可以，
  但**导入人必须是该租户管理员**，且检索按租户隔离，不会串数据）。

## 4. 失败语义（为什么这么设计）

| 情况 | 返回 | 理由 |
|------|------|------|
| 知识库未绑定到租户 | 404 `PLATFORM_NOT_FOUND` | 调用方拿不到该资源；在模型之前失败，不烧 token |
| enterprise 未配置内部令牌 | 403（企业侧）→ 平台 502 | fail closed，不提供无鉴权检索 |
| enterprise 不可用/超时 | 502 `TOOL_EXECUTION_ERROR` | 「检索坏了」不能伪装成「没搜到」 |
| enterprise 返回 404（归属不符） | 502（平台视角是上游拒绝） | 平台绑定可能过期；日志带 kbId 便于对账 |
| 检索 0 命中 | 200 + 明确文案 | 有依据地告诉用户「没有」 |
| 模型失败 | 200 + 摘录 + `degraded=true` | 至少给原文与出处，不编造 |

## 5. 数据流与合规

- **平台不落知识库正文**：绑定表只有租户/ID/名称；切片正文只出现在本次执行的 `output_json`（已过 Guardrail 输出脱敏）；
- **企业侧只读**：内部端点不写会话/Token/模型调用，避免两个服务各记一套账；
- **引用可追溯**：`citations[].documentId + chunkIndex + documentName` 足以回到企业原文；
- **租户隔离双重**：平台绑定谓词 + 工具内二次校验；企业侧再校验知识库归属。

## 6. YAGNI 陈述

- 不引入消息队列/缓存：知识库问答是同步请求，缓存答案反而带来「过期答案」合规风险；
- 不做流式：当前前端未接入，SSE 会扩大改造面；
- 不做权限表：租户内全可见已满足当前演示与单院场景，科室级授权等有真实需求再加；
- 不做自动同步：企业侧改名是低频操作，重新导入即可。
