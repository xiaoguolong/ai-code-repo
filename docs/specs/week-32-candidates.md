# Week 32 候选计划：PDF/OCR 入库 + 步骤级过程管理

> **状态：候选（2026-11-01，待批准）**。第 31 周已交付 SSE；本周（W32）不做代码之外的承诺，
> 先定范围与验收，再拆执行版 `week-32.md`。
>
> 来源：用户 2026-11-01 决策——方案 A；步骤级"prompt 原文可见"；"像 Dify 工作流那样"查看每一步的
> 提示词/结果/token；PDF/扫描件 → OCR → 切片/向量。

## 1. 现状（已核对代码）

| 能力 | 已有 | 缺 |
|------|------|----|
| 上传 PDF/扫描件 | enterprise `POST /api/v1/files`（50MB、本地磁盘）；`POST /api/v1/ocr/upload` 直接 multipart 识别 | 页面入口、类型/大小校验、去重、清理 |
| OCR | `POST /api/v1/ocr`（千帆 PaddleOCR-VL，120s，逐页 markdown、图片占位符清理） | 结果不落库、不返回页数/请求 id、无重试 |
| 切片/向量 | `IngestDocumentUseCase`：`TextChunker`(500/50) → 逐 chunk embed → pgvector | 只收字符串；OCR 文本要人工二次调用；逐块串行 |
| 串联 | **无**（三次手工调用） | 作业/步骤/进度/重试/幂等 |
| 过程管理 | 平台有执行记录/steps 摘要/聚合 token/Langfuse/Prometheus | enterprise 侧没有步骤表、没有 Micrometer；prompt 正文不落库 |

## 2. 已定方向

1. **工作流形态**：确定性 DAG（不是 LLM 规划），节点 = 上传 → OCR → 清洗 → 切片 → 向量化；
   每个节点有 `status/耗时/输入摘要/输出摘要/错误`，可重跑失败节点；
2. **Dify 式视图**：先做"运行详情的时间线/节点卡片"（每节点 prompt/结果/用量），
   **不做**拖拽式流程编辑器（那是另一个量级）；
3. **prompt 原文可见**：节点级记录渲染后的 prompt 正文；必须同时具备
   访问控制（仅 owner/admin）、保留期（默认 30 天，可配 0=不存）、列表页默认脱敏、
   不进日志/不进 Langfuse 出站正文；
4. **用量口径**：OCR 记页数/图片数（厂商无 token）；embedding 默认 `hashing` 无 token，
   切 `openai` 时解析 `usage.prompt_tokens`；LLM 节点记 prompt/completion。

## 3. 建议范围（P0，3–5 天）

| # | 事项 | 预估 |
|---|------|------|
| 1 | enterprise：`ingest_job` / `ingest_job_step`（V12）+ 状态机 + 重试/取消 + 幂等（内容 hash） | 1.5d |
| 2 | enterprise：上传 → OCR → 清洗 → 切片 → 向量 串成作业；页数/字符数/块数落步骤 | 1d |
| 3 | 平台/enterprise：步骤查询 API + 页面时间线（节点卡片、prompt 正文、结果、用量） | 1d |
| 4 | prompt 存储与保留策略（加密/脱敏/保留期/权限）+ 审计 | 0.5d |
| 5 | 测试 + 真机（20 页扫描件）+ 文档 | 1d |

## 4. 明确不做（W32）

- 拖拽式工作流编辑器、任意节点编排/条件分支；
- PDF 页面预览/切片预览/编辑；
- 多模态 LLM 节点（图表理解）——OCR 已返回 markdown，先不做二次理解；
- 平台侧再建一套 RAG（仍复用 enterprise 的切片/向量）。

## 5. 验收草案

- 上传 20 页扫描件 → 作业 `SUCCEEDED`，步骤齐全（ocr/chunk/embed），页数=20、块数>0；
- 失败可重试且不重复入库；重复上传同文件幂等；
- 页面能看到每步 prompt 原文（有权限时）、输出摘要、耗时、用量；
- 关闭 prompt 存储（`INGEST_PROMPT_STORE=false`）时页面显示"未保存"而不是空白；
- 根聚合 ≥1023 不回归；真机端到端 < 3 分钟（20 页）。

## 6. 开工前需要确认

1. **保留期**：prompt 原文默认存 30 天，还是"仅本次运行可见、不落库"？
2. **权限**：prompt 原文只给 admin，还是知识库 owner 也能看？
3. **OCR 供应商**：继续千帆 PaddleOCR-VL，还是本地 PaddleOCR（成本/合规）？
4. **存储**：OCR 原文是否独立存一份（便于重切片），还是只留 chunks？
