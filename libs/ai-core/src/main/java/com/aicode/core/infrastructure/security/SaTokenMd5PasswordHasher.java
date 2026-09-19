package com.aicode.core.infrastructure.security;

import cn.dev33.satoken.secure.SaSecureUtil;
import com.aicode.core.domain.port.PasswordHasher;
import com.aicode.core.infrastructure.config.AuthProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

/**
 * 基于 Sa-Token {@link SaSecureUtil} 的加盐 md5 密码哈希。盐 = 用户名 + 全局盐。
 * 注意：md5 为快速哈希，可被暴力破解，仅适用于演示/学习环境；生产应替换为 BCrypt/Argon2 实现。
 * sa-token 为可选依赖，仅当引入 sa-token 时才装配本适配器。
 */
@Component
@ConditionalOnClass(name = "cn.dev33.satoken.secure.SaSecureUtil")
public class SaTokenMd5PasswordHasher implements PasswordHasher {

    private final String salt;

    public SaTokenMd5PasswordHasher(AuthProperties authProperties) {
        this.salt = authProperties.resolvedPasswordSalt();
    }

    @Override
    public String hash(String rawPassword, String username) {
        return SaSecureUtil.md5BySalt(rawPassword, username + salt);
    }

    @Override
    public boolean matches(String rawPassword, String username, String hashed) {
        return hash(rawPassword, username).equals(hashed);
    }
}
