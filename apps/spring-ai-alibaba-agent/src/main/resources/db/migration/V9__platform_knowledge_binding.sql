-- Week 25（V9）：平台侧知识库绑定
-- 语义：平台把一个 enterprise 知识库登记给某个租户；检索时按绑定的 enterpriseUserId 执行，
-- 避免依赖「每个平台用户都必须有 SSO 映射」。
CREATE TABLE IF NOT EXISTS platform_knowledge_binding (
    binding_id          VARCHAR(64)  PRIMARY KEY,
    tenant_key          VARCHAR(64)  NOT NULL,
    enterprise_user_id  BIGINT       NOT NULL,
    knowledge_base_id   BIGINT       NOT NULL,
    name                VARCHAR(255) NOT NULL,
    description         VARCHAR(1000) NOT NULL DEFAULT '',
    enabled             BOOLEAN      NOT NULL,
    created_by          BIGINT       NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_platform_knowledge_binding_user FOREIGN KEY (created_by) REFERENCES platform_user(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_platform_knowledge_binding_tenant_kb
    ON platform_knowledge_binding(tenant_key, knowledge_base_id);

CREATE INDEX IF NOT EXISTS idx_platform_knowledge_binding_tenant
    ON platform_knowledge_binding(tenant_key, enabled);
