package com.aicode.enterprise.domain.port;

import com.aicode.enterprise.domain.model.SsoUserLink;

import java.util.List;
import java.util.Optional;

/**
 * 出站端口：平台用户 ↔ 本地用户映射（Week 21 建链，Week 23 增加运维查询/解绑）。
 *
 * <p>不合并用户表的原因见 Spec 三：enterprise 的 {@code app_user} 被文档归属、会话、
 * Token 统计依赖，一次性迁移风险远大于一张映射表。</p>
 */
public interface SsoUserLinkPort {

    /**
     * 按平台用户查本地用户主键（兼容 Week 21 调用方）。
     *
     * @param platformUserId 平台用户主键
     * @return 本地用户主键，未建链为空
     */
    default Optional<Long> findEnterpriseUserId(long platformUserId) {
        return findByPlatformUserId(platformUserId).map(SsoUserLink::enterpriseUserId);
    }

    /**
     * 按平台用户查完整映射。
     *
     * @param platformUserId 平台用户主键
     * @return 映射，未建链为空
     */
    Optional<SsoUserLink> findByPlatformUserId(long platformUserId);

    /**
     * 按本地用户查完整映射（防止一个本地用户被两个平台用户占用）。
     *
     * @param enterpriseUserId 本地用户主键
     * @return 映射，未建链为空
     */
    Optional<SsoUserLink> findByEnterpriseUserId(long enterpriseUserId);

    /**
     * 列出映射（运维视图，按建链时间倒序）。
     *
     * @param limit 条数上限
     * @return 映射列表，无数据返回空列表
     */
    List<SsoUserLink> listAll(int limit);

    /**
     * 建立（或覆盖）映射。
     *
     * @param platformUserId   平台用户主键
     * @param enterpriseUserId 本地用户主键
     * @param tenantKey        租户键
     */
    void link(long platformUserId, long enterpriseUserId, String tenantKey);

    /**
     * 删除某平台用户的映射。
     *
     * @param platformUserId 平台用户主键
     * @return true = 删除了一条
     */
    boolean unlink(long platformUserId);
}
