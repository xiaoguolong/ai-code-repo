package com.aicode.enterprise.infrastructure.internal;

import com.aicode.enterprise.domain.exception.ForbiddenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 内部接口令牌校验（Week 25）。
 *
 * <p>与 Week 23 的 {@code AdminTokenGuard} 同一套安全要求：</p>
 * <ul>
 *   <li><b>fail closed</b>：未配置令牌时拒绝所有请求，不允许“忘了配 = 裸奔”；</li>
 *   <li>比较用 {@link MessageDigest#isEqual}，不走字符串短路；</li>
 *   <li>日志不打印令牌，只记录拒绝原因。</li>
 * </ul>
 */
@Component
public class InternalTokenGuard {

    private static final Logger log = LoggerFactory.getLogger(InternalTokenGuard.class);

    /** 内部调用方使用的请求头名。 */
    public static final String HEADER_NAME = "X-Internal-Token";

    private final InternalProperties properties;

    public InternalTokenGuard(InternalProperties properties) {
        this.properties = properties;
    }

    /**
     * 校验请求头令牌。
     *
     * @param provided 请求头 {@code X-Internal-Token}，可为 null
     * @throws ForbiddenException 未配置、缺失或不匹配
     */
    public void verify(String provided) {
        if (!properties.configured()) {
            log.warn("[enterprise-internal] ENTERPRISE_INTERNAL_TOKEN is not configured; request rejected");
            throw new ForbiddenException("internal endpoint is not enabled");
        }
        if (provided == null || provided.isBlank()) {
            throw new ForbiddenException("internal token is required");
        }
        boolean matched = MessageDigest.isEqual(
                properties.token().getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
        if (!matched) {
            log.warn("[enterprise-internal] invalid internal token");
            throw new ForbiddenException("internal token is invalid");
        }
    }
}
