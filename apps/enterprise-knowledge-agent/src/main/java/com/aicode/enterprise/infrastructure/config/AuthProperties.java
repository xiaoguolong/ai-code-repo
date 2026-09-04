package com.aicode.enterprise.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 登录鉴权配置。passwordSalt 用于密码哈希加盐，生产必须通过环境变量覆盖。
 *
 * @param passwordSalt 密码哈希盐，对应 AUTH_PASSWORD_SALT
 */
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(String passwordSalt) {

    /**
     * @return 有效盐；缺省为空串
     */
    public String resolvedPasswordSalt() {
        return passwordSalt == null ? "" : passwordSalt;
    }
}
