# Week 33 实现日志：大文档分批 OCR + 质量与可运维性

> 计划：`docs/specs/week-33.md`；基线：W32 收盘（根聚合 1041）。
> 状态：**已完成并真机 PASS**（2026-11-01，配额恢复后复验）。

## 1. 交付

| 项 | 文件 | 说明 |
|----|------|------|
| PDF 分批 | `PdfPageSplitter` + `pdfbox:3.0.3` | 逐页拆后按 8 页/批合并；图片不拆 |
| 流水线分批 | `IngestPipeline.ocrStep` | 每批 OCR、批间 500ms、markdown `\n\n` 合并、页数按批累加；节点摘要 `页数=20，分批=3` |
| clean 模型 | `ingest.clean-model` | 空=沿用 `llm.model`；调用失败自动回退默认模型 |
| 清洗验收 | `acceptCleaned` | 太短（<原文 20%）或复述指令 → 退回 OCR 原文 |
| 启动恢复 | `IngestJobRecovery` + `IngestJobPort.failUnfinished` | 启动把 `RUNNING/PENDING` 置 FAILED（`restarted before completion`） |
| 契约用例 | `CreateIngestJobUseCaseTest` | 400 空文件、403 越权/不存在、429 队列满（作业落 FAILED）、幂等命中 |
| 指标 | actuator + micrometer-prometheus | `ingest_job_total{status}`、`ingest_job_duration_seconds`、`ingest_step_duration_seconds{step,status}`；仅本机 `/actuator/*` |
| prompt 治理 | `AdminIngestJobController` + `clearPrompt` | `DELETE /api/v1/admin/ingest-jobs/{id}/prompt`，X-Admin-Token，保留 token/耗时 |
| 平台提示 | `KnowledgeView.vue` | operator 显示"绑定由 admin 导入；文档在企业端" + 企业页链接 |

## 2. 验证

| 范围 | 结果 |
|------|------|
| enterprise | **103** 全绿（含分批 4+1、模型 1、清洗验收 3、恢复 1、清理 1、契约 6、OCR 退避 1） |
| ai-core | 108 全绿 |
| 前端 | typecheck 0 / Vitest 23 / build 成功 |
| 真机 | **PASS**：20 页 PDF → `pageCount=20`、`分批=3`、59 块；OCR 30.4s、clean 131.8s（13 段）、embed 5.1s、index 5.2s；prompt token 8697/13312 |

## 3. 真机验收（PASS，2026-11-01 12:25）

配额恢复后重试作业 `4c2f6e36-…`，一次通过：

| 步骤 | 耗时 | 输出 |
|------|------|------|
| upload | 0ms | bytes 已落盘 |
| ocr | 30.4s | 页数=20，分批=3（8/8/4），字符=19172 |
| clean | 131.8s | 13 段，清洗后 26228 字，prompt 8697 / completion 13312，模型 deepseek-v4-pro |
| chunk | 0ms | 块数=59 |
| embed | 5.1s | 向量数=59 |
| index | 5.2s | documentId=ingest-4c2f6e36…，块数=59 |

- 分块接口复核：**59 块**，首块以 `## Diabetes Follow-up Guideline (Rev 4)` 开头（无"我们需要"前缀）；
- 指标：`ingest_job_total{succeeded}=2`、`{failed}=2`（含此前 429 失败），
  `ingest_step_duration_seconds_count` 六个步骤齐全。

## 4. 跨轮阻塞与恢复（记录）

- 09:00–12:00 千帆持续 429，连续 3 轮目标重试失败（含 1s/2s 退避、每次最多 3 次）；
- 期间补充了批内退避重试与 `deploy/app/cloud-accept-week33.sh`，代码未再改动；
- 12:24 用户确认充值恢复额度后，重跑同一作业即 PASS。

## 5. 剩余建议

1. 生产配 `ingest.clean-model=deepseek-chat`，观察清洗质量（本次 13 段耗时 131.8s）；
2. 跨批仍固定 500ms，可按配额调整；单批已有 1s/2s 退避；
3. W34 候选：按批续跑（当前整步重试会重跑已成功批次）、OCR 供应商备份。