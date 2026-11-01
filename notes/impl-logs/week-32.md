# Week 32 实现日志：PDF/OCR 入库 + 步骤级过程管理

> 计划：`docs/specs/week-32.md`；接口：`docs/api/week-32-api.md`。
> 状态：**已完成并真机验收 PASS**（2026-11-01）。

## 1. 交付范围

| 层 | 文件 | 说明 |
|----|------|------|
| 迁移 | `V8__ingest_job.sql` | `ingest_job`（含 `ocr_markdown`/`clean_markdown`/`content_hash`）+ `ingest_job_step`（prompt 原文/token/耗时） |
| 领域 | `IngestJob`、`IngestJobStep`、`IngestJobStatus`、`IngestStepStatus`、`IngestStepKey` | 状态机、步骤枚举、幂等字段 |
| 端口 | `IngestJobPort`、`IngestJobLauncher` | 存储与异步启动解耦 |
| 持久化 | `JdbcIngestJobAdapter` | H2/PostgreSQL 兼容；步骤先 UPDATE 再 INSERT |
| 流水线 | `IngestPipeline` | upload→ocr→clean→chunk→embed→index；逐步落 Step；失败/取消/重试 |
| 用例 | `CreateIngestJobUseCase`、`IngestJobQueryUseCase`、`RetryIngestJobUseCase`、`CancelIngestJobUseCase` | 鉴权、sha256 幂等、入队、重试、取消 |
| 接口 | `IngestJobController` + 4 个 DTO | 建/列表/详情/重试/取消；队列满 429 |
| 执行器 | `IngestExecutorConfiguration`、`IngestJobExecutor` | 2 线程/64 队列，拒绝即 429 |
| 前端 | `EnterpriseKnowledgeView.vue`、`api/enterprise.ts`、`api/types.ts` | 上传 PDF、作业列表、节点时间线抽屉、prompt 折叠、重试/取消、2s 轮询 |
| 提示词 | `prompts/ingest-clean-v1.txt` | clean 步骤的系统提示（版本由 `prompt.version` 决定） |

## 2. 关键实现选择

1. **prompt 只存一张表**：`ingest_job_step.prompt_text`，永久保留（用户确认"用个表存就行"）；
   不引入 TTL/清理任务；访问控制为知识库 owner + admin。
2. **OCR 原文与清洗文本分列存**（`ocr_markdown`/`clean_markdown`）：
   重试时可跳过 OCR、只重跑清洗/切片；也支持后续"只重切片"。
3. **索引幂等**：documentId 固定为 `ingest-<jobId>`，重试先 `deleteByDocumentId` 再建，
   避免重复文档/向量。
4. **取消语义**：`cancel` 只置状态；流水线在每步边界检查，当前步跑完即停，后续步不再执行。
5. **队列满**：`CreateIngestJobUseCase` 捕获线程池拒绝，把作业置 FAILED 并抛 429。
6. **页数**：`OcrResult` 新增 `pageCount`（千帆 `layoutParsingResults` 长度），
   OCR 后写入 Job，供节点视图展示。

## 3. 验证

| 范围 | 结果 |
|------|------|
| ai-core | **108** 全绿（`OcrResult` 页数字段兼容旧构造） |
| enterprise | **78** 全绿（新增 8 个：作业/步骤 upsert 4 + 流水线 4） |
| 前端 | typecheck 0 / Vitest 23 / build 成功（`web-ci.ps1`） |
| 根聚合 | **1041** 全绿（ai-core 108 / enterprise 78 / patient 21 / platform 734 / gateway 100） |
| 真机（`cloud-accept-week32.sh`） | **PASS**：20 页 PDF → `SUCCEEDED`，`pageCount=10`（千帆单次上限）、`chunkCount=40`；6 节点全 SUCCEEDED；clean prompt 219 字符、`3948/7120` token；同文件重传 `duplicate=true` 同 jobId；文本入库回归 200 |

流水线测试覆盖：

- 全 6 步 SUCCEEDED、clean 节点 `promptText` 与 12/34 token、文档与向量数量一致；
- 重试且已有 OCR 文本 → OCR 步骤 `SKIPPED`，不调用 OCR；
- OCR 抛错 → Job `FAILED` 且 OCR 步骤 `FAILED`；
- 运行前取消 → 不执行任何步骤。

## 4. 未完成/待真机

1. ~~真机 20 页扫描 PDF~~ **已通过**（`deploy/app/cloud-week32.sh` + `cloud-accept-week32.sh`）；
   注意千帆单次 PDF 只解析 **10 页**，20 页文件需分批（P1）；
2. HTTP 契约用例（multipart/403/409/429）尚未补；
3. 进程重启把遗留 `RUNNING` 置 `FAILED` 的启动恢复逻辑未做（P1）；
4. 企业侧无 actuator，节点耗时只落库，未导出 Micrometer 指标（W33）。

## 5. 真机过程中修的两个问题

1. **clean 截断**：首版一次性把 9580 字符喂给模型，`chat.max-tokens=1024` 把输出截到 432 字，
   导致 `chunk=1`；改为 **1500 字符分段清洗**（7 段），产出 18035 字 / 40 块；
2. **客户端 multipart 头**：验收脚本首次漏设 `Content-Type: multipart/form-data`，
   Spring 报 `HttpMediaTypeNotSupportedException` 500；属验收脚本问题，服务端未改。
## 6. 增补（2026-11-01，用户反馈后）

1. **分块查看**：新增 `GET /knowledge-bases/{kbId}/documents/{docId}/chunks` + 企业页文档行「切片」
   按钮（抽屉展示序号/字数/正文）；真机验证 8 块可读。
2. **只重清洗**：新增 `POST /ingest-jobs/{jobId}/reprocess`，用已存 `ocr_markdown` 重跑
   clean→chunk→embed→index，不重跑 OCR（OCR 配额 429 时仍可修复历史文档）。
3. **外键顺序 bug**：`indexStep` 原先先删 `document` 再删 `document_chunk`，触发
   `fk_document_chunk_document`；改为先删切片（向量）再删文档。
4. **清洗兜底**：`deepseek-v4-pro` 会把"思考过程"写进正文；新增 `sanitizeCleanOutput`
   去掉"我们需要/用户要求/分析"前缀（截到第一个 markdown 标题，找不到则回退原文），
   并在 prompt 中加硬性约束。真机重跑后首块已是正文；**中段仍可能夹带分析**
   （模型特性），彻底解决需给 clean 步骤换非推理模型（`ingest.clean-model`，待补）。