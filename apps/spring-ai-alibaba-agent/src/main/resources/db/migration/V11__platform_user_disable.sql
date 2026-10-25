-- Week 28（V11）：平台用户停用
-- 语义：disabled_at 非空 = 停用（保留停用时间，恢复时置空）；不删行，
-- 执行记录 / 审批 / 随访 / 记忆 / 审计全部保留。
-- 约束：脚本必须同时可跑在 PostgreSQL 与 H2(MODE=PostgreSQL)。

ALTER TABLE platform_user ADD COLUMN IF NOT EXISTS disabled_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_platform_user_disabled
    ON platform_user (disabled_at);
