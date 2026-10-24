-- Week 21 / V7：平台用户 ↔ enterprise 本地用户映射（统一身份 SSO）
-- 约束：脚本必须同时可跑在 PostgreSQL 与 H2(MODE=PostgreSQL)。
-- 不合并 app_user：enterprise 的文档归属、会话与 Token 统计都依赖它，迁表风险远大于一张映射表。

CREATE TABLE IF NOT EXISTS sso_user_link (
    platform_user_id   BIGINT      NOT NULL PRIMARY KEY,
    enterprise_user_id BIGINT      NOT NULL UNIQUE,
    tenant_key         VARCHAR(64) NOT NULL,
    linked_at          TIMESTAMP WITH TIME ZONE NOT NULL
);
