package com.aicode.enterprise.infrastructure.sso;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * enterprise 运维管理配置（Week 23，前缀 {@code enterprise.admin}）。
 *
 * <p>只用于 SSO 映射的解绑/重建。令牌只从环境变量读取，禁止写进仓库；
 * 未配置时管理端点一律 403（fail closed），不会退化成“无鉴权可用”。</p>
 *
 * @param token 运维令牌；空表示未配置
 */
@ConfigurationProperties(prefix = "enterprise.admin")
public record AdminProperties(String token) {

    /**
     * @return 是否配置了运维令牌
     */
    public boolean configured() {
        return token != null && !token.isBlank();
    }
}
