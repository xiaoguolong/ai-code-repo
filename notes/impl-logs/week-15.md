# Week 15 实现日志 — Agent Evaluation

- 日期：2026-09-30
- 批次：1
- 对应 Spec：docs/specs/week-15.md

## 1. 本周目标

1. ai-core 落地 `EvalPort` + `DefaultEvalAdapter`（EXACT_MATCH / CONTAINS_ALL / REGEX / NOT_CONTAINS / GUARDRAIL_BLOCKED）
2. Platform Eval：classpath 数据集加载、批量 Run、自动评分报告
3. 预置 `guardrail-regression` / `rag-keyword-smoke` / `prompt-format-smoke` 三套测试集
4. REST API + 单测（TDD RED → GREEN）

## 2. 边界

- Always：规则评分；中文 JavaDoc；TDD；Week 8–14 编排不改
- Never：LLM-as-Judge；enterprise 在线 RAG Eval；PostgreSQL 数据集

## 3. 增量架构

见 `docs/architecture/week-15-architecture.md`。

## 4. 新增类型清单

| 类型 | 名称 | 职责 |
|------|------|------|
| Port | EvalPort | 单 case 评分 + 报告聚合 |
| Model | EvalExpectation / EvalCase / EvalDataset / EvalReport | 评估领域模型 |
| Adapter | DefaultEvalAdapter | 规则驱动评分 |
| Port | EvalDatasetPort / EvalRunPort | 数据集与报告持久化 |
| Loader | ClasspathEvalDatasetLoader | 从 resources/eval 加载 JSON |
| UseCase | PlatformEvalUseCase | 批量评估编排 |
| Controller | PlatformEvalController | Eval REST API |

## 5. RED

- 先写 DefaultEvalAdapterTest、PlatformEvalUseCaseTest
- 编译失败（EvalPort 尚未实现）

## 6. GREEN

- 命令：`.\run-maven-jdk17.ps1 test`
- 结果：`BUILD SUCCESS`（根聚合全绿）
- 新增单测：DefaultEvalAdapterTest **10**、PlatformEvalUseCaseTest **6**

## 7. 质量门禁

- [x] 根聚合 test 全绿
- [x] Week 8–14 测试不回退
- [x] 无密钥入库

## 8. 验证证据

验收场景：
- `EXACT_MATCH` / `CONTAINS_ALL` / `REGEX` / `NOT_CONTAINS` 规则评分
- `guardrail-regression` 批量 Run → passRate = 1.0
- `rag-keyword-smoke` fixture 评分 → passRate = 1.0
- operator 越权 Agent Eval → PlatformAccessDeniedException

Postman：`docs/postman/week-15.postman_collection.json`

## 9. 风险与下周输入

- 规则评分无法衡量语义质量；第 17–18 周 OTel/Langfuse 可补 trace 级分析
- Agent case 依赖 LLM 稳定性；生产 Eval 需 mock 或固定 seed

## 10. ADR

| 决策 | 选择 | 理由 |
|------|------|------|
| 评分引擎 | 规则 | YAGNI；可测 |
| 端口位置 | ai-core | 横切复用 |
| RAG Eval | fixture | 本周建框架 |
