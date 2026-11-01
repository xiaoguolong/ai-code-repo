# Spec: Week 32 — PDF/OCR 入库 + 步骤级过程管理（Dify 式节点视图）

> 来源：`docs/specs/week-32-candidates.md`（2026-11-01 候选）+ 用户决策：
> 步骤提示词**原文可见**、过程像 Dify 工作流一样按节点查看。
> 基线：Week 31 收盘（根聚合 **1033**、前端 Vitest **23**、云端 SSE 已上线）。
>
> **状态：执行中（2026-11-01）。** 后端（V8/流水线/接口）与企业页节点视图已完成，
> 单测与前端 CI 全绿；真机 20 页 PDF 验收待做。进度见 `notes/impl-logs/week-32.md`。

## Objective

1. **PDF/扫描件入库闭环**：上传 PDF → OCR → 清洗 → 切片 → 向量化 → 可检索；
   不再需要"传文件、调 OCR、贴文本入库"三次手工调用。
2. **步骤级过程管理**：一次入库 = 一个 Job + N 个 Step；每步有状态、耗时、输入/输出摘要、
   **prompt 原文**、token 用量、错误；失败可重试、可取消、重复上传幂等。
3. **Dify 式节点视图**：企业知识库页展示 Job 列表与节点时间线（节点卡片：prompt/结果/用量），
   不是拖拽式流程编辑器。

## 1. 现状（已核对代码）

| 能力 | 已有 | 缺 |
|------|------|----|
| 上传文件 | `POST /api/v1/files`（multipart，50MB，本地磁盘 + `file_record`） | 与入库无关联 |
| OCR | `POST /api/v1/ocr/upload`（multipart → base64 → 千帆 PaddleOCR-VL，120s） | 结果不落库、无页数/请求 id、无重试 |
| 入库 | `IngestDocumentUseCase`：纯文本 → `TextChunker`(500/50) → 逐块 embed → pgvector | 只收字符串、同步执行、无 Job |
| LLM 能力 | enterprise 已有 `ChatModelPort` / `PromptTemplatePort`（问答在用） | 入库链路未使用 |
| 过程记录 | 只有最终 `document` + `document_chunk` | 无 Job/Step 表、无 prompt/token 记录 |
| 迁移 | common `db/migration` V1–V5、V7；V6 在 `db/postgresql`（pgvector） | 新表放 common，版本 **V8** |

## 2. 已定决策与默认值

| 决策 | 取值 | 说明 |
|------|------|------|
| 工作流形态 | **确定性 DAG**（不引入 LLM 规划） | 节点顺序固定：upload→ocr→clean→chunk→embed→index |
| 哪些节点有 prompt | `clean`（文档清洗/纠错/分节） | OCR/切片/向量化没有 prompt，节点里显式标注"无 prompt" |
| prompt 存储 | **原文落库**（用户已确认） | 只有一个表：`ingest_job_step.prompt_text`，**永久保留**；W32 不做 TTL/清理配置 |
| 谁能看 prompt | **知识库 owner + admin** | enterprise 侧按 KB 归属校验；他人 403 |
| OCR 供应商 | 千帆 PaddleOCR-VL（沿用） | 失败可按次重试，不自动换供应商 |
| OCR 原文 | **独立存一份**（`ingest_job.ocr_markdown`） | 便于"只重切片"而不重跑 OCR |
| 执行方式 | **异步 Job**（企业侧有界线程池，默认 2） | 同步 HTTP 撑不住 50MB/上百页 |
| 幂等 | `(kb_id, sha256(file))` 唯一 | 重复上传返回已有 Job（`duplicate=true`） |

## 3. 数据模型（V8，enterprise `db/migration/V8__ingest_job.sql`）

```sql
CREATE TABLE ingest_job (
    job_id          VARCHAR(64) PRIMARY KEY,
    kb_id           BIGINT NOT NULL,
    user_id         BIGINT NOT NULL,
    file_name       VARCHAR(255) NOT NULL,
    file_id         VARCHAR(64),
    content_hash    VARCHAR(64) NOT NULL,          -- sha256(file)
    status          VARCHAR(24) NOT NULL,          -- PENDING/RUNNING/SUCCEEDED/FAILED/CANCELLED
    page_count      INT NOT NULL DEFAULT 0,
    chunk_count     INT NOT NULL DEFAULT 0,
    ocr_markdown    TEXT,                          -- 独立留存，支持只重切片
    document_id     VARCHAR(64),                   -- 成功后关联 document
    error_message   TEXT NOT NULL DEFAULT '',
    created_at      TIMESTAMPTZ NOT NULL,
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ
);
CREATE UNIQUE INDEX uk_ingest_job_hash ON ingest_job(kb_id, content_hash);
CREATE INDEX idx_ingest_job_kb ON ingest_job(kb_id, created_at DESC);

CREATE TABLE ingest_job_step (
    job_id          VARCHAR(64) NOT NULL REFERENCES ingest_job(job_id) ON DELETE CASCADE,
    step_no         INT NOT NULL,
    step_key        VARCHAR(32) NOT NULL,          -- upload/ocr/clean/chunk/embed/index
    status          VARCHAR(16) NOT NULL,          -- PENDING/RUNNING/SUCCEEDED/FAILED/SKIPPED
    input_summary   VARCHAR(1000) NOT NULL DEFAULT '',
    output_summary  VARCHAR(1000) NOT NULL DEFAULT '',
    prompt_version  VARCHAR(32)  NOT NULL DEFAULT '',
    prompt_text     TEXT,                          -- clean 步骤的渲染后 prompt 原文
    prompt_tokens   INT NOT NULL DEFAULT 0,
    completion_tokens INT NOT NULL DEFAULT 0,
    duration_ms     BIGINT NOT NULL DEFAULT 0,
    error_message   TEXT NOT NULL DEFAULT '',
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (job_id, step_no)
);
```

> prompt 原文就存在 `ingest_job_step.prompt_text` 这一张表里，**永久保留**；W32 不引入 TTL/清理任务。
> 访问控制：只对 owner/admin 可见、不写日志、不进出站；后续需要"删除某次 Job 的 prompt"再加管理入口（P1）。

## 4. 流水线（Job 状态机）

```text
PENDING ──submit──> RUNNING ──每步 SUCCEEDED──> SUCCEEDED（document_id 可检索）
   │                  ├── 任一步 FAILED ──> FAILED（error 落 step + job；可 retry）
   │                  └── cancel ────────> CANCELLED（当前步之后停止）
   └── 重复上传 ──> 直接返回已有 Job（duplicate=true）
```

| 步 | 做什么 | 输出/记录 |
|----|--------|-----------|
| upload | 保存文件、算 sha256、建 Job | file_id、字节数 |
| ocr | 千帆 OCR（multipart base64），逐页 markdown 拼接 | 页数、正文字符数、`ocr_markdown` |
| clean | **LLM** 清洗/纠错/分节（prompt 原文落库） | 清洗后文本、prompt/token、耗时 |
| chunk | `TextChunker` 500/50 | 块数、字符数 |
| embed | 逐块 embedding（沿用现有端口） | 向量数、失败块数（>0 即失败） |
| index | 写 `document` + `document_chunk` | document_id、chunk_count |

**重试语义**：`retry(jobId)` 只从失败步重跑；OCR 已有 `ocr_markdown` 时跳过 OCR
（`step.status=SKIPPED`），保证"不重复烧 OCR/embedding"。取消 = 置 `cancelRequested`，
当前步完成后停止，后续步 `SKIPPED`。

## 5. 接口（enterprise `8081`，经 `/ent/api` 反代）

| 方法 + 路径 | 说明 |
|-------------|------|
| `POST /api/v1/knowledge-bases/{kbId}/ingest-jobs` | multipart `file`（PDF/图片，≤50MB）；202 `{jobId, duplicate}` |
| `GET /api/v1/knowledge-bases/{kbId}/ingest-jobs` | 列表（分页/状态筛选） |
| `GET /api/v1/ingest-jobs/{jobId}` | Job + steps（含 prompt 原文、token、耗时） |
| `POST /api/v1/ingest-jobs/{jobId}/retry` | 从失败步重跑；非 FAILED → 409 |
| `POST /api/v1/ingest-jobs/{jobId}/cancel` | 非 RUNNING/PENDING → 409 |
| 权限 | KB 不属于当前用户 → 403；Job 不存在 → 404；prompt 仅 owner/admin 可见 |

响应形状（Job 详情）：

```json
{
  "jobId": "…", "status": "SUCCEEDED", "fileName": "…", "pageCount": 20,
  "chunkCount": 143, "documentId": "…", "duplicate": false,
  "steps": [
    {"stepNo": 1, "stepKey": "upload", "status": "SUCCEEDED", "durationMs": 12,
     "inputSummary": "file=… (2.1MB)", "outputSummary": "fileId=…"},
    {"stepNo": 3, "stepKey": "clean", "status": "SUCCEEDED", "durationMs": 8100,
     "promptVersion": "v1", "promptText": "你是医疗文档清洗助手…",
     "promptTokens": 1200, "completionTokens": 800, "outputSummary": "清洗后 18234 字"}
  ]
}
```

## 6. 前端（企业知识库页扩展，`/ent/knowledge`）

```text
选中库 → [上传 PDF] → Job 列表（状态/页数/块数/时间）→ 点 Job → 详情抽屉
详情抽屉：节点时间线（Dify 式卡片）
  ├─ 节点卡：序号 + 名称 + 状态 + 耗时
  ├─ 展开：输入摘要 / 输出摘要 / prompt 原文（只读、可复制；无 prompt 的节点显示"该步无 prompt"）
  └─ 失败节点：[从该步重试]；运行中：[取消]
```

- 轮询 `GET /ingest-jobs/{id}`（2s，最多 10 分钟），终态后停止；
- prompt 原文默认折叠（避免误展示患者信息），展开需二次确认；
- 纯文本入库入口保留（回归用）。

## 7. 五天排期

| 天 | 交付 | 门禁 |
|----|------|------|
| D1 | V8 迁移 + Job/Step 模型/端口/JDBC 适配器 + 状态机 + 幂等 hash | enterprise 单测全绿 |
| D2 | 上传→OCR→clean→chunk→embed→index 编排 + 异步执行器 + 重试/取消 | 本地 PDF 端到端（假 OCR/假模型） |
| D3 | 接口 + 权限（prompt 仅 owner/admin）+ 审计/日志 | HTTP 契约用例 |
| D4 | 企业页上传/Job 列表/节点时间线/重试/取消 | web-ci 全绿 |
| D5 | 真机 20 页扫描件、重试幂等、文本入库回归、文档/Postman | 根聚合 + 云端 PASS |

## 8. 验收

- 上传 20 页 PDF → `SUCCEEDED`：厂商返回页数（千帆单次 PDF 上限 **10 页**，20 页文件实测
  `pageCount=10`，**分批 OCR 列 P1**）、`chunkCount>0`、`documentId` 可检索；
- 节点 6 个齐全，`clean` 节点有 prompt 原文 + prompt/completion tokens > 0；
- 重复上传同一文件 → 返回同一 Job（`duplicate=true`），不新增文档/向量；
- 人为让 embed 失败 → Job `FAILED`；`retry` 只重跑失败步，不重复 OCR；
- `cancel` 运行中的 Job → `CANCELLED`，无残留 RUNNING（重启后也不复活）；
- 非 owner 访问 prompt → 403；prompt 原文在 `ingest_job_step` 长期可查；
- 既有纯文本入库、问答、SSE 不回归；根聚合 ≥1033，web-ci 全绿。

### 8.1 真机结果（2026-11-01，PASS）

| 检查 | 实测 |
|------|------|
| 20 页 PDF 入库 | `SUCCEEDED`，`pageCount=10`（厂商单次上限），`chunkCount=40` |
| 节点 | 6 个全 `SUCCEEDED`；OCR 16.8s、clean 61.1s（7 段）、embed 3.7s、index 3.7s |
| clean prompt | 原文 219 字符，`promptTokens=3948`、`completionTokens=7120` |
| 幂等 | 同文件重传返回同一 `jobId`（`duplicate=true`） |
| 文本入库回归 | 200，`chunks=1` |
| Flyway | `Migrating schema to version 8 - ingest job` → `Successfully applied 1 migration` |

> 长文档清洗按 **1500 字符分段**调用（`chat.max-tokens=1024`，一次性清洗会被截断，
> 真机首版只产出 432 字/1 块，已修复为 7 段/18035 字/40 块）。

## 9. 风险

| 风险 | 缓解 |
|------|------|
| 50MB/上百页 OCR 超 120s | 异步 Job + 超时置 FAILED（可重试）；页数上限 200 |
| 逐块 embedding 慢/限流 | 批量/限速（默认每批 16），失败块记录并重试 |
| prompt 原文含患者信息 | 仅 owner/admin；页面默认折叠；永久保存在 step 表；不写日志/不进出站 |
| 重复入库 | `(kb_id, sha256)` 唯一 + 事务内建 Job |
| 进程重启丢 RUNNING | 启动时把 `RUNNING` 置 `FAILED`（error="restarted"），可重试 |
| 企业侧无 actuator/指标 | 本周只做 DB 记录 + 日志；Micrometer 面板排 W33 |

## 10. 明确不做

- 拖拽式工作流编辑器、任意节点编排/条件分支；
- PDF 页面预览、切片预览/编辑；
- 多模态图表理解（OCR 已出 markdown，先不做二次理解）；
- 平台侧自建 RAG（仍复用 enterprise 切片/向量）；
- 多租户/科室级知识库授权、TLS、容器化（按触发条件）。

## 11. 开工前需确认（默认值已写入第 2 节）

1. prompt 可见范围：**owner + admin**（推荐）还是仅 admin？
2. `clean` 节点是否允许关闭（`INGEST_CLEAN_ENABLED=false` 直接 OCR 原文切片，省 token）？
