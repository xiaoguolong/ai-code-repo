-- Week 27（V10）：知识库绑定对账 + 审批待办超时提醒
-- 约束：脚本必须同时可跑在 PostgreSQL 与 H2(MODE=PostgreSQL)，不使用方言专有语法 / 方言 upsert。
--
-- 1) platform_knowledge_binding 增加同步状态：
--    sync_status   UNKNOWN（导入后尚未对账，V9 旧行默认）/ ACTIVE / MISSING
--    last_synced_at 最近一次成功对账时间；对账失败不改写（避免上游抖动把绑定判死）
-- 2) platform_approval_reminder：提醒记账（不改 platform_approval 的领域字段与状态机）
--    只记录「何时提醒过、提醒了几次」，审批是否决策与此表无关。

ALTER TABLE platform_knowledge_binding
    ADD COLUMN IF NOT EXISTS sync_status VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN';

ALTER TABLE platform_knowledge_binding
    ADD COLUMN IF NOT EXISTS last_synced_at TIMESTAMP WITH TIME ZONE;

-- 运营视图/对账统计按「租户 + 状态」查询
CREATE INDEX IF NOT EXISTS idx_platform_knowledge_binding_sync
    ON platform_knowledge_binding (tenant_key, sync_status);

CREATE TABLE IF NOT EXISTS platform_approval_reminder (
    approval_id      VARCHAR(64) NOT NULL PRIMARY KEY,
    reminder_count   INTEGER     NOT NULL DEFAULT 0,
    last_reminded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_platform_approval_reminder_approval
        FOREIGN KEY (approval_id) REFERENCES platform_approval(approval_id)
);

-- 冷却窗口查询：最近提醒时间
CREATE INDEX IF NOT EXISTS idx_platform_approval_reminder_last
    ON platform_approval_reminder (last_reminded_at);

-- 超时待办扫描：status + created_at（V6 的两个索引都以 user_id/tenant_key 打头，帮不上全局扫描）
CREATE INDEX IF NOT EXISTS idx_platform_approval_status_created
    ON platform_approval (status, created_at);
