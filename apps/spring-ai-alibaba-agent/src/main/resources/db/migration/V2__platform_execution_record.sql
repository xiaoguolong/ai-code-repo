-- Week 16 / V2：Agent 执行记录（Week 13 遗留落库项）
-- 约束：脚本必须同时可跑在 PostgreSQL 与 H2(MODE=PostgreSQL)。

CREATE TABLE IF NOT EXISTS execution_record (
    id                BIGINT       PRIMARY KEY,
    execution_id      VARCHAR(64)  NOT NULL UNIQUE,
    user_id           BIGINT       NOT NULL,
    agent_key         VARCHAR(64)  NOT NULL,
    agent_type        VARCHAR(48)  NOT NULL,
    status            VARCHAR(24)  NOT NULL,
    -- VARCHAR 无长度：PostgreSQL 为不限长，H2 为最大长度，避免 PostgreSQL 不存在的 CLOB 类型
    input_json        VARCHAR      NOT NULL,
    output_json       VARCHAR      NOT NULL,
    model             VARCHAR(128) NOT NULL DEFAULT '',
    prompt_tokens     INT          NOT NULL DEFAULT 0,
    completion_tokens INT          NOT NULL DEFAULT 0,
    total_tokens      INT          NOT NULL DEFAULT 0,
    error_message     VARCHAR(1024) NOT NULL DEFAULT '',
    started_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at       TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_execution_record_user FOREIGN KEY (user_id) REFERENCES platform_user(id)
);

CREATE INDEX IF NOT EXISTS idx_execution_record_user_started
    ON execution_record(user_id, started_at);
