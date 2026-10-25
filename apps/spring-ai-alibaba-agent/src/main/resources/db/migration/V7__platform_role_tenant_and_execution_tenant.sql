-- Week 23 / V7：角色按租户复制（B2）+ 执行记录租户维度
-- 约束：脚本必须同时可跑在 PostgreSQL 与 H2(MODE=PostgreSQL)，不使用方言专有语法。
--
-- 背景：Week 21 的 platform_role 是全局表（role_key 主键）。B2 决策要求「每个租户一份角色副本，
-- 但暂不开放租户自建角色」：
--   1) 角色主体与三类授权表都增加 tenant_key，主键改为 (tenant_key, role_key[, value])；
--   2) 迁移时把旧全局角色复制给每个已存在租户；若租户表为空（全新库），至少复制给 default，
--      其余租户由 Java seed 补齐；
--   3) execution_record 增加 tenant_key，并按 user_id 回填既有数据。

-- ── 1) 新角色主体/授权表（先建副本，最后整体替换，避免中途丢表）──────────────
CREATE TABLE IF NOT EXISTS platform_role_v7 (
    tenant_key   VARCHAR(64)  NOT NULL,
    role_key     VARCHAR(64)  NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    is_admin     BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (tenant_key, role_key)
);

CREATE TABLE IF NOT EXISTS platform_role_agent_v7 (
    tenant_key VARCHAR(64) NOT NULL,
    role_key   VARCHAR(64) NOT NULL,
    agent_key  VARCHAR(64) NOT NULL,
    PRIMARY KEY (tenant_key, role_key, agent_key)
);

CREATE TABLE IF NOT EXISTS platform_role_tool_v7 (
    tenant_key VARCHAR(64) NOT NULL,
    role_key   VARCHAR(64) NOT NULL,
    tool_key   VARCHAR(64) NOT NULL,
    PRIMARY KEY (tenant_key, role_key, tool_key)
);

CREATE TABLE IF NOT EXISTS platform_role_patient_v7 (
    tenant_key VARCHAR(64) NOT NULL,
    role_key   VARCHAR(64) NOT NULL,
    patient_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (tenant_key, role_key, patient_id)
);

-- ── 2) 复制：每个已存在租户一份；租户表为空时至少 default ─────────────────────
INSERT INTO platform_role_v7 (tenant_key, role_key, display_name, is_admin)
SELECT t.tenant_key, r.role_key, r.display_name, r.is_admin
FROM platform_role r CROSS JOIN platform_tenant t;

INSERT INTO platform_role_v7 (tenant_key, role_key, display_name, is_admin)
SELECT 'default', r.role_key, r.display_name, r.is_admin
FROM platform_role r
WHERE NOT EXISTS (SELECT 1 FROM platform_tenant WHERE tenant_key = 'default');

INSERT INTO platform_role_agent_v7 (tenant_key, role_key, agent_key)
SELECT t.tenant_key, g.role_key, g.agent_key
FROM platform_role_agent g CROSS JOIN platform_tenant t;

INSERT INTO platform_role_agent_v7 (tenant_key, role_key, agent_key)
SELECT 'default', g.role_key, g.agent_key
FROM platform_role_agent g
WHERE NOT EXISTS (SELECT 1 FROM platform_tenant WHERE tenant_key = 'default');

INSERT INTO platform_role_tool_v7 (tenant_key, role_key, tool_key)
SELECT t.tenant_key, g.role_key, g.tool_key
FROM platform_role_tool g CROSS JOIN platform_tenant t;

INSERT INTO platform_role_tool_v7 (tenant_key, role_key, tool_key)
SELECT 'default', g.role_key, g.tool_key
FROM platform_role_tool g
WHERE NOT EXISTS (SELECT 1 FROM platform_tenant WHERE tenant_key = 'default');

INSERT INTO platform_role_patient_v7 (tenant_key, role_key, patient_id)
SELECT t.tenant_key, g.role_key, g.patient_id
FROM platform_role_patient g CROSS JOIN platform_tenant t;

INSERT INTO platform_role_patient_v7 (tenant_key, role_key, patient_id)
SELECT 'default', g.role_key, g.patient_id
FROM platform_role_patient g
WHERE NOT EXISTS (SELECT 1 FROM platform_tenant WHERE tenant_key = 'default');

-- ── 3) 整体替换 ──────────────────────────────────────────────────────────────
DROP TABLE IF EXISTS platform_role_agent;
DROP TABLE IF EXISTS platform_role_tool;
DROP TABLE IF EXISTS platform_role_patient;
DROP TABLE IF EXISTS platform_role;

ALTER TABLE platform_role_v7 RENAME TO platform_role;
ALTER TABLE platform_role_agent_v7 RENAME TO platform_role_agent;
ALTER TABLE platform_role_tool_v7 RENAME TO platform_role_tool;
ALTER TABLE platform_role_patient_v7 RENAME TO platform_role_patient;

-- ── 4) 执行记录租户维度 ─────────────────────────────────────────────────────
ALTER TABLE execution_record ADD COLUMN IF NOT EXISTS tenant_key VARCHAR(64) NOT NULL DEFAULT 'default';

UPDATE execution_record
SET tenant_key = (SELECT u.tenant_key FROM platform_user u WHERE u.id = execution_record.user_id)
WHERE EXISTS (SELECT 1 FROM platform_user u WHERE u.id = execution_record.user_id);

CREATE INDEX IF NOT EXISTS idx_execution_record_tenant_started
    ON execution_record (tenant_key, started_at);
