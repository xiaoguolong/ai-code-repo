# Architecture: Week 14 — AI 安全（Guardrails）

## 1. 包结构

```
libs/ai-core/
  domain/port/GuardrailPort.java                    ← 本周新增
  domain/model/GuardrailContext.java              ← 本周新增
  domain/exception/GuardrailViolationException.java
  infrastructure/config/GuardrailProperties.java
  infrastructure/security/
    DefaultGuardrailAdapter.java
    GuardrailToolPort.java
    PiiMasker.java

apps/spring-ai-alibaba-agent/
  platform/application/PlatformGuardrailService.java  ← 本周新增（Run 入出参编排）
  controller/GlobalExceptionHandler.java            ← 新增 GUARDRAIL_VIOLATION 映射
  infrastructure/config/AppConfiguration.java       ← Tool 链：RBAC → Guardrail → Registry
```

## 2. 拦截链路

```mermaid
flowchart TB
    subgraph PlatformRun["Platform Run（8084）"]
        PR[POST /platform/agents/key/runs]
        UC[PlatformExecutionUseCase]
        GS[PlatformGuardrailService]
    end

    subgraph Guardrail["Guardrail 层（ai-core）"]
        GP[GuardrailPort]
        DA[DefaultGuardrailAdapter]
        GT[GuardrailToolPort]
    end

    subgraph ToolChain["Tool 执行链"]
        ATP[AuthorizingToolPort<br/>Week 13 RBAC]
        GTP[GuardrailToolPort<br/>Week 14]
        REG[ToolRegistry]
    end

    PR --> UC
    UC --> GS
    GS -->|validate input| GP
    UC --> RUN[PlatformAgentRunner]
    RUN --> GP
    UC -->|sanitize output| GS

    RUN --> ToolChain
    ATP --> GTP --> REG
    GP --> DA
    GT --> GP
```

## 3. 数据流（Run 一次）

```mermaid
sequenceDiagram
    participant C as Client
    participant UC as PlatformExecutionUseCase
    participant G as PlatformGuardrailService
    participant R as PlatformAgentRunner
    participant T as GuardrailToolPort

    C->>UC: POST runs + input
    UC->>UC: RBAC（Week 13）
    UC->>G: validateRunInput
    G->>G: 注入检测 / 长度 / 字符
    UC->>R: run(agent, input)
    R->>T: ToolPort.execute
    T->>T: 参数键白名单 + 注入检测
    T-->>R: 脱敏后 ToolResult
    R-->>UC: PlatformRunOutput
    UC->>G: sanitizeRunOutput
    G-->>UC: 脱敏 output
    UC-->>C: 200 + 脱敏结果
```

## 4. 与既有模块关系

| 模块 | 变更 |
|------|------|
| ai-core | 新增 GuardrailPort + 适配器 + Tool 装饰器 |
| spring-ai-alibaba-agent | Platform Run 集成；Tool 链加长一节 |
| enterprise / patient-agent | 无变更（可后续依赖 ai-core Guardrail） |
| Week 8–11 Graph | 无编排变更；经 ToolPort 间接受益 |

## 5. YAGNI 陈述

- 不用 LLM 做二次分类（规则足够演示注入/PII）。
- 不用外部 Guardrails 服务（NeMo 等留后续）。
- Tool 白名单仅校验**参数键名**，不校验值域（值域由 RBAC patientId + Bean Validation 覆盖）。
