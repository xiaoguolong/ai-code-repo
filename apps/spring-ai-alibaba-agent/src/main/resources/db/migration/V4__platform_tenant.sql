-- Week 21 / V4：多租户（医疗 SaaS 的第一道边界）
-- 约束：脚本必须同时可跑在 PostgreSQL 与 H2(MODE=PostgreSQL)，不使用方言专有特性。

CREATE TABLE IF NOT EXISTS platform_tenant (
    tenant_key   VARCHAR(64)  NOT NULL PRIMARY KEY,
    display_name VARCHAR(128) NOT NULL,
    status       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 既有数据全部落到 default 租户：加列带默认值，不要求先回填即可启动。
-- 不建外键：租户 seed 与用户 seed 是彼此独立的 ApplicationRunner，顺序由 Spring 决定；
-- 用默认值兜底比引入「必须先 seed 租户」的隐式顺序依赖更稳。
ALTER TABLE platform_user ADD COLUMN IF NOT EXISTS tenant_key VARCHAR(64) NOT NULL DEFAULT 'default';
