package com.aicode.enterprise.dto;

import com.aicode.enterprise.domain.model.SsoUserLink;

import java.time.Instant;

/**
 * SSO 映射响应（Week 23）。
 *
 * @param platformUserId   平台用户主键
 * @param enterpriseUserId 本地用户主键
 * @param tenantKey        租户键
 * @param linkedAt         建链时间
 */
public record SsoLinkResponse(
        long platformUserId,
        long enterpriseUserId,
        String tenantKey,
        Instant linkedAt
) {

    /**
     * 由领域模型转换。
     *
     * @param link 映射
     * @return 响应体
     */
    public static SsoLinkResponse from(SsoUserLink link) {
        return new SsoLinkResponse(
                link.platformUserId(), link.enterpriseUserId(), link.tenantKey(), link.linkedAt());
    }
}
