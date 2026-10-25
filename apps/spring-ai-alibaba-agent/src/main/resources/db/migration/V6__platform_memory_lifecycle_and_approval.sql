-- Week 23 / V6：长期记忆生命周期 + 患者分析人工审批
-- 约束：脚本必须同时可跑在 PostgreSQL 与 H2(MODE=PostgreSQL)，不使用方言专有语法。
--
-- 1) platform_memory 增加生命周期字段：
--    expires_at      为空 = 永不过期（既有行默认不过期，避免迁移误删历史记忆）
--    embedding_model 为空 = 向量来源未知/待重建（正好覆盖 Week 21 真机留下的空向量）
-- 2) platform_approval：HIGH 风险患者分析的人工审批任务（跨进程、可重启）。

ALTER TABLE platform_memory ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE platform_memory ADD COLUMN IF NOT EXISTS embedding_model VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_platform_memory_expires
    ON platform_memory (expires_at);

CREATE INDEX IF NOT EXISTS idx_platform_memory_embedding_model
    ON platform_memory (embedding_model);

CREATE TABLE IF NOT EXISTS platform_approval (
    approval_id  VARCHAR(64)  NOT NULL PRIMARY KEY,
    execution_id VARCHAR(64)  NOT NULL,
    tenant_key   VARCHAR(64)  NOT NULL,
    user_id      BIGINT       NOT NULL,
    agent_key    VARCHAR(64)  NOT NULL,
    patient_id   VARCHAR(64)  NOT NULL,
    risk_level   VARCHAR(16)  NOT NULL,
    task         VARCHAR      NOT NULL,
    status       VARCHAR(16)  NOT NULL,
    comment      VARCHAR(1024) NOT NULL DEFAULT '',
    decided_by   BIGINT,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    decided_at   TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_platform_approval_user FOREIGN KEY (user_id) REFERENCES platform_user(id)
);

-- 一次执行最多一条审批任务：重复提交/并发重试不会造出两条待办。
CREATE UNIQUE INDEX IF NOT EXISTS uk_platform_approval_execution
    ON platform_approval (execution_id);

CREATE INDEX IF NOT EXISTS idx_platform_approval_owner
    ON platform_approval (user_id, status, created_at);

CREATE INDEX IF NOT EXISTS idx_platform_approval_tenant
    ON platform_approval (tenant_key, status, created_at);
