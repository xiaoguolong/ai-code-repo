# Spec: Week 15 — Agent Evaluation（测试集 / 标准答案 / 自动评分）

## Objective

在 ai-core 落地 **`EvalPort`** 规则评分能力，并在 `spring-ai-alibaba-agent`（8084）集成：**测试问题集**、**标准答案/期望**、**自动评分报告**。覆盖 Agent / RAG / Prompt / Guardrail 四类回归场景；与 Week 14 Guardrail 攻击集衔接，建立可重复的质量门禁。

## Tech Stack

- JDK 17、Spring Boot 3.4.5、Maven
- ai-core：`EvalPort`、`DefaultEvalAdapter`、评分模型
- 复用：Week 12 Platform Run / `PlatformAgentRunner` / Week 14 `PlatformGuardrailService`
- 数据集：classpath JSON（`resources/eval/*.json`），内存 Port 加载

## Commands

- 根聚合：`.\run-maven-jdk17.ps1 test`
- 单模块：`.\run-maven-jdk17.ps1 -pl apps/spring-ai-alibaba-agent -am test`
- ai-core：`.\run-maven-jdk17.ps1 -pl libs/ai-core test`
- 启动：`apps/spring-ai-alibaba-agent` 下 `.\run-maven-jdk17.ps1 spring-boot:run`（8084）
- Postman：导入 `docs/postman/week-15.postman_collection.json`

## 增量架构

见 `docs/architecture/week-15-architecture.md`。

## 评分能力矩阵

| 准则 | 适用场景 | 通过条件 |
|------|----------|----------|
| EXACT_MATCH | Agent / Prompt | 实际输出与期望值一致（忽略首尾空白） |
| CONTAINS_ALL | RAG / Agent | 实际输出包含全部关键词 |
| REGEX | Prompt 格式 | 实际输出匹配正则 |
| NOT_CONTAINS | Prompt 安全 | 实际输出不含禁用词 |
| GUARDRAIL_BLOCKED | Guardrail 回归 | 输入触发 `GuardrailViolationException` |

## 预置数据集

| datasetKey | 类别 | 说明 |
|------------|------|------|
| guardrail-regression | GUARDRAIL | Week 14 注入/超长攻击集回归 |
| rag-keyword-smoke | RAG | 关键词覆盖（fixture 评分，不调用 LLM） |
| prompt-format-smoke | PROMPT | 格式/禁用词（fixture 评分） |

## API（端口 8084）

| 资源 | Method | Path | 鉴权 |
|------|--------|------|------|
| Dataset | GET | /api/v1/platform/eval/datasets | 登录 |
| Dataset | GET | /api/v1/platform/eval/datasets/{key} | 登录 |
| Eval Run | POST | /api/v1/platform/eval/datasets/{key}/runs | 登录 + Agent 权限 |
| Eval Run | GET | /api/v1/platform/eval/runs/{runId} | 登录（本人或 admin） |

详见 `docs/api/week-15-api.md`。

## Boundaries

- **Always**：`EvalPort` 在 ai-core；评分规则可测、无 LLM-as-Judge；中文 JavaDoc；TDD
- **Ask first**：LLM 裁判评分；Langfuse 集成；enterprise 8083 在线 RAG Eval
- **Never**：改 Graph/Workflow 编排；改 RBAC/Guardrail 逻辑；密钥入库

## Success Criteria

- [ ] `EXACT_MATCH` / `CONTAINS_ALL` / `REGEX` / `NOT_CONTAINS` 单测全绿
- [ ] `guardrail-regression` 数据集 Run → 全部 case 通过（注入被拦截）
- [ ] `rag-keyword-smoke` fixture 评分 → passRate = 1.0
- [ ] POST eval run 返回 `EvalReport`（含 case 明细与 passRate）
- [ ] 根聚合 test 全绿，Week 8–14 测试不回退

## 与 Week 14 关系

| 维度 | Week 14 Guardrail | Week 15 Eval |
|------|-------------------|--------------|
| 关注点 | 实时拦截 | 离线回归验证 |
| 输入 | 单次 Run | 批量测试集 |
| 输出 | 400 / 脱敏 | passRate + 明细报告 |
| 衔接 | 规则实现 | `guardrail-regression` 攻击集 |

## ADR

| 决策 | 选项 | 选择 | 理由 |
|------|------|------|------|
| 评分引擎 | LLM Judge / 规则 | 规则 | YAGNI；可测；无额外 API 成本 |
| 端口位置 | ai-core / platform | ai-core | 横切；patient-agent / enterprise 可复用 |
| 数据集存储 | PostgreSQL / classpath JSON | classpath JSON + 内存 Port | 与 Week 12–13 一致 |
| RAG Eval | 在线 8083 / fixture | fixture + 关键词 | 本周建框架；在线 RAG 留后续 |
| Guardrail case | 全 Run / 仅校验 | 仅 `PlatformGuardrailService` | 不测 LLM；聚焦护栏 |

## Open Questions

无。
