package com.aicode.enterprise.domain.port;

import java.util.Optional;

/**
 * 出站端口：平台用户 ↔ 本地用户映射（Week 21）。
 *
 * <p>不合并用户表的原因见 Spec 三：enterprise 的 {@code app_user} 被文档归属、会话、
 * Token 统计依赖，一次性迁移风险远大于一张映射表。</p>
 */
public interface SsoUserLinkPort {

    /**
     * 按平台用户查本地用户。
     *
     * @param platformUserId 平台用户主键
     * @return 本地用户主键，未建链为空
     */
    Optional<Long> findEnterpriseUserId(long platformUserId);

    /**
     * 建立（或覆盖）映射。
     *
     * @param platformUserId   平台用户主键
     * @param enterpriseUserId 本地用户主键
     * @param tenantKey        租户键（便于运维核对）
     */
    void link(long platformUserId, long enterpriseUserId, String tenantKey);
}
