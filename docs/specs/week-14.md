# Spec: Week 14 — AI 安全（Guardrails / Prompt 过滤 / 脱敏 / Tool 白名单）

## Objective

在 ai-core 落地 **`GuardrailPort`** 横切能力，并在 `spring-ai-alibaba-agent`（8084）集成：**Prompt 注入过滤**、**输入校验**、**输出 PII 脱敏**、**Tool 参数键白名单**。与 Week 13 RBAC **正交**——RBAC 管「谁可以调用」，Guardrail 管「内容是否安全」。

## Tech Stack

- JDK 17、Spring Boot 3.4.5、Maven
- ai-core：`GuardrailPort`、`DefaultGuardrailAdapter`、`GuardrailToolPort`
- 复用：Week 8–13 Platform Run / `AuthorizingToolPort` / `ToolRegistry`
- 配置：`guardrail.*`（`application.yml`）

## Commands

- 根聚合：`.\run-maven-jdk17.ps1 test`
- 单模块：`.\run-maven-jdk17.ps1 -pl apps/spring-ai-alibaba-agent -am test`
- ai-core：`.\run-maven-jdk17.ps1 -pl libs/ai-core test`
- 启动：`apps/spring-ai-alibaba-agent` 下 `.\run-maven-jdk17.ps1 spring-boot:run`（8084）
- Postman：导入 `docs/postman/week-14.postman_collection.json`

## 增量架构

见 `docs/architecture/week-14-architecture.md`。

## Guardrail 能力矩阵

| 能力 | 拦截点 | 违例行为 |
|------|--------|----------|
| Prompt 注入过滤 | Run input（task 等字符串） | 400 `GUARDRAIL_VIOLATION` |
| 输入长度/字符校验 | Run input + Tool 参数字符串 | 400 |
| 输出 PII 脱敏 | Run output + Tool result | 替换为掩码后返回 |
| Tool 参数键白名单 | ToolPort.execute 前 | 400 / ToolExecutionException |

## 配置（默认）

```yaml
guardrail:
  enabled: true
  max-input-length: 8192
  block-prompt-injection: true
  sanitize-output: true
  allowed-tool-argument-keys:
    - patientId
    - task
```

## Boundaries

- **Always**：`GuardrailPort` 在 ai-core；Platform Run 必经 Guardrail；中文 JavaDoc；TDD
- **Ask first**：LLM 侧二次分类器；NeMo Guardrails 外部服务；全 API 强制 Guardrail 关闭开关
- **Never**：改 Graph/Workflow 编排；改 RBAC 逻辑；密钥入库；用 Guardrail 替代 RBAC

## Success Criteria

- [ ] 含「ignore previous instructions」的 task → 400 `GUARDRAIL_VIOLATION`
- [ ] 超长 input（>8192）→ 400
- [ ] 输出含手机号 `13812345678` → 脱敏为 `138****5678`
- [ ] Tool 参数含非白名单键（如 `sql`）→ 拒绝
- [ ] `guardrail.enabled=false` 时行为与 Week 13 一致
- [ ] 根聚合 test 全绿，Week 8–13 测试不回退

## 与 Week 13 关系

| 维度 | Week 13 RBAC | Week 14 Guardrail |
|------|--------------|-------------------|
| 关注点 | 身份 + 授权 | 内容安全 |
| 拦截 | Agent/Tool/patientId 权限 | 注入/长度/PII/参数键 |
| 违例 | 401/403 | 400 |
| Tool 链 | AuthorizingToolPort 外层 | GuardrailToolPort 内层（RBAC 之后） |

## ADR

| 决策 | 选项 | 选择 | 理由 |
|------|------|------|------|
| 端口位置 | ai-core / platform | ai-core | 横切能力；patient-agent 可复用 |
| Tool 链顺序 | RBAC→Guardrail / Guardrail→RBAC | RBAC→Guardrail | 先拒未授权，再检内容 |
| 注入检测 | LLM 分类 / 规则 | 规则（正则） | YAGNI；可测；无外部依赖 |
| 直连 Run | 强制 / 可关 | `guardrail.enabled` 全局 | 内容安全应对所有入口一致 |

## Open Questions

无。
