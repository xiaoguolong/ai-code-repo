-- Week 16 / V1：平台 RBAC（用户、角色、Agent/Tool/数据域三类授权）
-- 约束：脚本必须同时可跑在 PostgreSQL 与 H2(MODE=PostgreSQL)，不使用方言专有特性。

CREATE TABLE IF NOT EXISTS platform_user (
    id            BIGINT       PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(128) NOT NULL,
    role_key      VARCHAR(64)  NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS platform_role (
    role_key     VARCHAR(64)  PRIMARY KEY,
    display_name VARCHAR(128) NOT NULL,
    is_admin     BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS platform_role_agent (
    role_key  VARCHAR(64) NOT NULL,
    agent_key VARCHAR(64) NOT NULL,
    PRIMARY KEY (role_key, agent_key)
);

CREATE TABLE IF NOT EXISTS platform_role_tool (
    role_key VARCHAR(64) NOT NULL,
    tool_key VARCHAR(64) NOT NULL,
    PRIMARY KEY (role_key, tool_key)
);

CREATE TABLE IF NOT EXISTS platform_role_patient (
    role_key   VARCHAR(64) NOT NULL,
    patient_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (role_key, patient_id)
);
