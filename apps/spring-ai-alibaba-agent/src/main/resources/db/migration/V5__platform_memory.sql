-- Week 21 / V5：按「租户 + 用户」隔离的长期记忆
-- 约束：脚本必须同时可跑在 PostgreSQL 与 H2(MODE=PostgreSQL)。
-- 向量以 JSON 文本存储（如 [0.1,-0.2]）：平台库与其它应用共用，不在此处 CREATE EXTENSION；
-- 换 pgvector 只需替换适配器（见 docs/specs/week-21.md 七）。

CREATE TABLE IF NOT EXISTS platform_memory (
    memory_id  VARCHAR(64)  NOT NULL PRIMARY KEY,
    tenant_key VARCHAR(64)  NOT NULL,
    user_id    BIGINT       NOT NULL,
    session_id VARCHAR(64)  NOT NULL,
    task       VARCHAR      NOT NULL,
    answer     VARCHAR      NOT NULL,
    embedding  VARCHAR,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- 隔离谓词与排序走同一条索引：tenant_key + user_id + created_at
CREATE INDEX IF NOT EXISTS idx_platform_memory_scope
    ON platform_memory (tenant_key, user_id, created_at);

CREATE INDEX IF NOT EXISTS idx_platform_memory_session
    ON platform_memory (tenant_key, user_id, session_id);
