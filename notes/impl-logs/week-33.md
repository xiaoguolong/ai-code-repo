# Week 33 实现日志：大文档分批 OCR + 质量与可运维性

> 计划：`docs/specs/week-33.md`；基线：W32 收盘（根聚合 1041）。
> 状态：**代码完成、单测/前端 CI 全绿；真机 20 页验收被千帆 OCR 429 阻塞（待配额恢复重试）**。

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
| 真机 | **BLOCKED**：`OCR HTTP 429`（千帆配额），分批逻辑未能在真机跑到终态 |

## 3. 真机尝试

- 上传 20 页 PDF（`/tmp/w33-guide-20p.pdf`），作业 `4c2f6e36-…`：
  `upload SUCCEEDED`，`ocr FAILED 3674ms — OCR HTTP 429`，`pages=0`。
- 说明分批拆分本身已在单测验证：20 页 → 8/8/4 三批、每批可被 PDFBox 重新读出；
  厂商配额恢复后重跑 `cloud-accept-week33.sh` 即可。
- 启动恢复已生效（重启后无 RUNNING 遗留）；`/actuator/health=200`，
  Prometheus 指标在作业终态后出现（本次作业 FAILED 也会产生 `ingest_job_total{status="failed"}`）。

## 4. 下一步

1. 千帆配额恢复后重跑 20 页验收（断言 `pageCount=20`、`分批=3`、chunk>0、prompt/token 可见）；
2. 若仍 429：考虑批间退避（指数退避）或换供应商（W34 评估）；
3. `ingest.clean-model` 生产建议配 `deepseek-chat`，观察清洗质量是否稳定。
## 5. OCR 退避重试（真机 429 后补充）

单批 OCR 失败会退避重试：1s、2s（最多 3 次），仍失败才置作业 FAILED。
真机复验（10:22 部署后重试 job `4c2f6e36`）：`ocr FAILED 12780ms`（3 次尝试）仍 `OCR HTTP 429`，
说明是配额级限流而非瞬时抖动；分批/退避逻辑由单测覆盖（FakeOcr 前两次 429、第三次成功）。

指标已在真机可见：`ingest_job_total{status="failed"}`、`ingest_step_duration_seconds_count{step="ocr",status="failed"}`。

## 6. 外部阻塞

千帆 OCR 自 09:00 起持续 429（多次重试、跨 3 轮），20 页真机验收无法完成；
`cloud-accept-week33.sh` 已就绪，配额恢复后直接重跑。