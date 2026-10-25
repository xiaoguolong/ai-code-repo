package com.aicode.enterprise.infrastructure.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * enterprise 内部接口配置（Week 25，前缀 {@code enterprise.internal}）。
 *
 * <p>内部接口只给平台侧调用（知识库检索/清单），**不走 Sa-Token**，由
 * {@link InternalTokenGuard} 用共享令牌守卫。令牌只从环境变量读取，禁止入库；
 * 未配置时内部端点一律 403（fail closed）。</p>
 *
 * @param token 内部共享令牌；空表示未配置
 */
@ConfigurationProperties(prefix = "enterprise.internal")
public record InternalProperties(String token) {

    /**
     * @return 是否配置了内部令牌
     */
    public boolean configured() {
        return token != null && !token.isBlank();
    }
}
