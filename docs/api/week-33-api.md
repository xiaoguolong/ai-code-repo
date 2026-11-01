# Week 33 API / 运维变化

> 基线：Week 32 入库 API（`docs/api/week-32-api.md`）。本周**不新增业务路由**，
> 只新增 1 个运维路由，并改变"页数"的语义与若干配置。

## 1. 大文档分批（语义变化）

- `POST /api/v1/knowledge-bases/{kbId}/ingest-jobs` 行为不变；
- 上传 PDF 时后端按 `ingest.ocr-batch-pages`（默认 **8**）拆分后逐批 OCR；
- 作业详情里的 `pageCount` 现在是**全部批次累加的页数**（20 页 PDF → `pageCount=20`）；
- OCR 节点 `outputSummary` 形如：`页数=20，分批=3，字符=18342`；
- 任一批 OCR 失败 → 整步 `FAILED`，作业可 `retry`（会从头重跑，含 OCR）。

## 2. 清洗模型可配

| 配置 | 默认 | 说明 |
|------|------|------|
| `INGEST_CLEAN_MODEL` | 空（沿用 `LLM_MODEL`） | 建议 `deepseek-chat`（非推理）；调用失败自动回退默认模型 |
| `INGEST_OCR_BATCH_PAGES` | `8` | 每批 OCR 页数，必须 ≤ 厂商单次上限（10） |

清洗结果校验：输出过短（<原文 20%）或复述指令（"用户要求清洗/只输出清洗后的"）时，
该段退回 OCR 原文。

## 3. 运维：清空 prompt 原文

```http
DELETE /api/v1/admin/ingest-jobs/{jobId}/prompt
X-Admin-Token: <ADMIN_TOKEN>
```

```json
{"code":"SUCCESS","message":"OK","data":3}
```

- 只清 `ingest_job_step.prompt_text`，保留 `prompt_version` / `prompt_tokens` / `completion_tokens` / 耗时；
- 令牌未配置或错误 → 403（fail closed）；仅在回环 8081 使用。

## 4. 指标（Prometheus，仅本机）

| 端点 | 说明 |
|------|------|
| `GET :8081/actuator/health` | 存活 |
| `GET :8081/actuator/prometheus` | 指标 |

| 指标 | 标签 | 说明 |
|------|------|------|
| `ingest_job_total` | `status` | 终态作业计数（succeeded/failed/cancelled） |
| `ingest_job_duration_seconds` | `status` | 作业耗时（有 started/finished 时） |
| `ingest_step_duration_seconds` | `step,status` | 每步耗时（upload/ocr/clean/chunk/embed/index） |

nginx **不**反代 `/actuator`；Prometheus 抓 `127.0.0.1:8081`。

## 5. 启动恢复

进程启动时把遗留 `RUNNING/PENDING` 置 `FAILED`（`error_message=restarted before completion`），
页面可点「从失败步重试」；不会自动重跑。
