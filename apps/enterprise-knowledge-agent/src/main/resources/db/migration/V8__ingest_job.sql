-- Week 32：PDF/OCR 入库作业与步骤级过程记录（enterprise）
-- 语义：一次入库 = ingest_job + N 条 ingest_job_step；step 里保存该步的状态/耗时/结果摘要，
-- clean 步骤额外保存渲染后的 prompt 原文与 token 用量（永久保留，W32 不做 TTL）。
CREATE TABLE IF NOT EXISTS ingest_job (
    job_id         VARCHAR(64)  PRIMARY KEY,
    kb_id          BIGINT       NOT NULL,
    user_id        BIGINT       NOT NULL,
    file_name      VARCHAR(255) NOT NULL,
    file_id        VARCHAR(64),
    content_hash   VARCHAR(64)  NOT NULL,
    status         VARCHAR(24)  NOT NULL,
    page_count     INT          NOT NULL DEFAULT 0,
    chunk_count    INT          NOT NULL DEFAULT 0,
    ocr_markdown   TEXT,
    clean_markdown TEXT,
    document_id    VARCHAR(64),
    error_message  TEXT         NOT NULL DEFAULT '',
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at     TIMESTAMP WITH TIME ZONE,
    finished_at    TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_ingest_job_knowledge_base FOREIGN KEY (kb_id) REFERENCES knowledge_base(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ingest_job_kb_hash ON ingest_job(kb_id, content_hash);
CREATE INDEX IF NOT EXISTS idx_ingest_job_kb_created ON ingest_job(kb_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ingest_job_status ON ingest_job(status);

CREATE TABLE IF NOT EXISTS ingest_job_step (
    job_id            VARCHAR(64) NOT NULL,
    step_no           INT         NOT NULL,
    step_key          VARCHAR(32) NOT NULL,
    status            VARCHAR(16) NOT NULL,
    input_summary     VARCHAR(1000) NOT NULL DEFAULT '',
    output_summary    VARCHAR(1000) NOT NULL DEFAULT '',
    prompt_version    VARCHAR(32)  NOT NULL DEFAULT '',
    prompt_text       TEXT,
    prompt_tokens     INT          NOT NULL DEFAULT 0,
    completion_tokens INT          NOT NULL DEFAULT 0,
    duration_ms       BIGINT       NOT NULL DEFAULT 0,
    error_message     TEXT         NOT NULL DEFAULT '',
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (job_id, step_no),
    CONSTRAINT fk_ingest_job_step_job FOREIGN KEY (job_id) REFERENCES ingest_job(job_id) ON DELETE CASCADE
);
