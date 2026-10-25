package com.aicode.enterprise.infrastructure.sso;

import com.aicode.enterprise.domain.exception.ForbiddenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 运维端点令牌校验（Week 23，A1）。
 *
 * <p>fail closed：未配置令牌时拒绝所有请求，避免“忘了配 = 无鉴权可用”。
 * 比较用 {@link MessageDigest#isEqual}，不做字符串短路比较；日志不打印令牌。</p>
 */
@Component
public class AdminTokenGuard {

    private static final Logger log = LoggerFactory.getLogger(AdminTokenGuard.class);

    private final AdminProperties properties;

    public AdminTokenGuard(AdminProperties properties) {
        this.properties = properties;
    }

    /**
     * 校验请求头令牌。
     *
     * @param provided 请求头 {@code X-Admin-Token}，可为 null
     * @throws ForbiddenException 未配置、缺失或不匹配
     */
    public void verify(String provided) {
        if (!properties.configured()) {
            log.warn("[sso-admin] ENTERPRISE_ADMIN_TOKEN is not configured; request rejected");
            throw new ForbiddenException("admin endpoint is not enabled");
        }
        if (provided == null || provided.isBlank()) {
            throw new ForbiddenException("admin token is required");
        }
        boolean matched = MessageDigest.isEqual(
                properties.token().getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
        if (!matched) {
            log.warn("[sso-admin] invalid admin token");
            throw new ForbiddenException("admin token is invalid");
        }
    }
}
