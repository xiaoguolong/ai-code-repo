package com.aicode.enterprise.domain.model;

import java.time.Instant;

/**
 * 平台用户 ↔ enterprise 本地用户的映射（Week 21 建链，Week 23 运维可见）。
 *
 * @param platformUserId   平台用户主键
 * @param enterpriseUserId enterprise 本地用户主键
 * @param tenantKey        租户键（便于运维核对，不参与鉴权）
 * @param linkedAt         建链时间
 */
public record SsoUserLink(
        long platformUserId,
        long enterpriseUserId,
        String tenantKey,
        Instant linkedAt
) {

    public SsoUserLink {
        tenantKey = tenantKey == null ? "" : tenantKey;
    }
}
