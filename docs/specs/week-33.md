# Spec: Week 33 — 大文档分批 OCR + 入库质量与可运维性

> 基线：Week 32 收盘（根聚合 **1041**、真机 PDF/OCR 入库 PASS、企业页节点视图上线）。
> 来源：W32 真机未闭环项 + 用户反馈（10 页上限、清洗夹带模型分析、平台绑定页看不到文档）。
> 状态：**代码完成（2026-11-01）；真机 20 页验收被千帆 OCR 429 阻塞，待配额恢复重试**。

## Objective

1. **大文档完整入库**：>10 页 PDF 自动按 **8 页/批**拆分 OCR，合并文本与页数，
   修复千帆单次 10 页上限（20 页文档应得到 `pageCount=20`）。
2. **清洗质量可控**：clean 步骤模型可配（默认非推理模型），输出做长度/前缀校验，
   异常时降级为 OCR 原文；减少"分析过程"混入正文。
3. **可靠性**：进程重启把遗留 `RUNNING/PENDING` 置 `FAILED`（可重试）；
   补 HTTP 契约用例（multipart/403/409/429）。
4. **可观测**：enterprise 接入 actuator + Micrometer/Prometheus，导出
   Job 状态、Job 耗时、Step 耗时的指标（Prometheus 本机抓取，不对外暴露）。
5. **治理/UX**：平台绑定页对 operator 给出"绑定由 admin 导入、文档在企业端"的引导；
   admin 可手动清理某 Job 的 prompt 原文（永久保留但不等于不可删）。

## 1. 分批 OCR（核心）

- 依赖：`org.apache.pdfbox:pdfbox:3.0.3`（仅 PDF 拆分；图片仍单批）。
- 新增 `PdfPageSplitter`（infrastructure/ocr）：
  ```java
  record Batches(List<byte[]> chunks, List<Integer> pageCounts, int totalPages) {}
  Batches split(byte[] pdf, int batchPages);
  ```
  用 `Loader.loadPDF` + 逐页 `importPage` 到新 `PDDocument`，每 `batchPages` 页一个包。
- `IngestPipeline.ocrStep`：
  1. 读文件字节；PDF 且页数 > batch → 拆分；否则单批；
  2. 每批 base64 调 OCR，`OcrResult.pageCount` 累加，markdown 用 `\n\n` 合并；
  3. step 输出摘要：`页数=20，分批=3，字符=...`；
  4. 任一批失败 → 整步失败（已成功批的结果不落库，保持简单）。
- 配置：`ingest.ocr-batch-pages=8`（预留 2 页余量）；`ingest.max-pages=200` 仍为总量上限。
- 非 PDF（png/jpg）：不拆分，行为不变。

## 2. 清洗质量

- `ingest.clean-model`：默认空 = 用 `llm.model`；生产建议 `deepseek-chat`（非推理）。
  首批调用失败（模型不存在/400）时自动回退 `llm.model` 并记 step 错误摘要。
- 输出校验（每个分段）：
  - 空/长度 < 原文 20% → 用原文；
  - 以"我们需要/用户要求/分析/好的"开头 → 截到首个 markdown 标题（已有），找不到则原文；
  - 结果仍含"用户要求清洗"等指令复述 → 原文。
- step 里记录实际使用的模型（输出摘要），prompt 原文仍只存模板。

## 3. 启动恢复与契约测试

- `IngestJobRecovery`（`ApplicationRunner`）：
  `UPDATE ingest_job SET status='FAILED', error_message='restarted' WHERE status IN ('RUNNING','PENDING')`；
  写一条 WARN 日志；不自动重跑（避免重启风暴）。
- HTTP 契约（MockMvc，enterprise）：
  - `POST /knowledge-bases/{id}/ingest-jobs` multipart 201/202 + 空文件 400；
  - 非 owner 列表/详情/分块 → 403；
  - `retry` 非 FAILED → 409；`cancel` 终态 → 409；
  - 线程池满 → 429（用假 launcher 抛 `TaskRejectedException` 的用例覆盖）。

## 4. 可观测（enterprise 8081）

- 依赖：`spring-boot-starter-actuator` + `micrometer-registry-prometheus`。
- 暴露：`/actuator/health`、`/actuator/prometheus`（仅本机；nginx 不反代 actuator）。
- 指标：
  - `ingest_job_total{status}`（终态计数）
  - `ingest_job_duration_seconds`（成功 Job 耗时）
  - `ingest_step_duration_seconds{step,status}`（每步耗时）
- Prometheus 加 target `127.0.0.1:8081`；Grafana 面板（可选，至少留查询语句）。

## 5. 治理与 UX

- `DELETE /api/v1/ingest-jobs/{jobId}/prompt`：**admin** 才可；把该 Job 所有 step 的
  `prompt_text` 置空（保留 token/耗时/版本），返回清空条数；写 WARN 日志。
- 平台「知识库」页（operator）：加一条提示"绑定由 admin 导入；文档在企业端上传/查看"，
  并给 `/ent/knowledge` 链接；admin 保持「导入绑定/手动对账」。

## 6. 排期（5 天）

| 天 | 交付 | 门禁 |
|----|------|------|
| D1 | PDFBox 拆分 + 流水线分批 + 单测（20 页 → 3 批） | enterprise 单测 |
| D2 | clean-model 配置 + 校验/降级 + 模型回退 | 单测 + 真机一次 |
| D3 | 启动恢复 + HTTP 契约用例 | MockMvc 全绿 |
| D4 | actuator/Prometheus + 指标 + 清理 prompt 接口 | 本机指标可见 |
| D5 | 平台绑定页提示 + 文档/Postman + 真机 20 页验证 | 根聚合 + web-ci |

## 7. 验收

- 20 页 PDF → `SUCCEEDED`，`pageCount=20`（分批 3 批）、`chunkCount>0`、6 节点齐全；
- clean 节点 prompt 原文 + token>0；输出不含"我们需要回答"等分析前缀；
- 重启 enterprise 后无 `RUNNING` 遗留（全部 FAILED 可重试）；
- `/actuator/prometheus` 有 `ingest_job_total` / `ingest_step_duration_seconds`；
- 契约用例覆盖 400/403/409/429；管理员可清空 prompt（其它角色 403）；
- 既有 W32 功能不回归；根聚合 ≥1041，`tools/web-ci.ps1` 全绿。

## 8. 风险与边界

| 风险 | 缓解 |
|------|------|
| PDFBox 增加 jar 体积/内存 | 流式拆分，单批 ≤8 页，及时 close；heap 768m 已够 |
| 分批后厂商仍限流 | 批间 sleep 500ms；429 直接失败可重试 |
| 拆分后页眉页脚重复 | clean 提示词已要求去重；必要时 later 去重 |
| clean 换模型输出风格变化 | 校验+降级，保留 `ingest.clean-model` 可回退 |
| 指标暴露风险 | 只监听本机 8081，nginx 不反代 `/actuator` |

**不做**：扫描版 PDF 的版面还原、多栏/表格结构化、OCR 厂商切换、按页重试（整步重试）、
科室级授权、TLS、容器化。
