-- Week 16 / V3：审计日志（谁 / 何时 / 对什么 / 做了什么 / 结果）
-- 约束：脚本必须同时可跑在 PostgreSQL 与 H2(MODE=PostgreSQL)。

CREATE TABLE IF NOT EXISTS audit_log (
    id         BIGSERIAL    PRIMARY KEY,
    user_id    BIGINT       NOT NULL DEFAULT 0,
    action     VARCHAR(48)  NOT NULL,
    resource   VARCHAR(160) NOT NULL,
    result     VARCHAR(24)  NOT NULL,
    trace_id   VARCHAR(64)  NOT NULL DEFAULT '',
    detail     VARCHAR(512) NOT NULL DEFAULT '',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_log_created_at ON audit_log(created_at);
CREATE INDEX IF NOT EXISTS idx_audit_log_user_id ON audit_log(user_id);
