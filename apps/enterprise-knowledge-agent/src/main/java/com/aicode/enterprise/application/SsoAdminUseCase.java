package com.aicode.enterprise.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.core.domain.exception.NotFoundException;
import com.aicode.enterprise.domain.exception.ConflictException;
import com.aicode.enterprise.domain.model.SsoUserLink;
import com.aicode.enterprise.domain.port.SsoUserLinkPort;
import com.aicode.enterprise.domain.port.UserPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * SSO 映射运维用例（Week 23，A1 运维令牌）：查询、重建、解绑（可选删本地用户）。
 *
 * <p>为什么解绑要区分两种语义：只解绑时本地同名用户仍在，下次 SSO 登录会
 * 触发自动开户并撞 409（第 22 周登记的“死结”）；`deleteLocalUser=true` 会先解绑
 * 再删本地用户，下次登录可重新开户建链。两个动作都要求运维显式选择。</p>
 */
@Service
public class SsoAdminUseCase {

    private static final Logger log = LoggerFactory.getLogger(SsoAdminUseCase.class);

    /** 运维列表默认条数。 */
    public static final int DEFAULT_LIST_LIMIT = 50;
    /** 运维列表硬上限。 */
    public static final int MAX_LIST_LIMIT = 200;

    private final SsoUserLinkPort linkPort;
    private final UserPort userPort;

    public SsoAdminUseCase(SsoUserLinkPort linkPort, UserPort userPort) {
        this.linkPort = linkPort;
        this.userPort = userPort;
    }

    /**
     * 列出映射。
     *
     * @param limit 条数上限，默认 50、上限 200
     * @return 映射列表
     */
    public List<SsoUserLink> list(int limit) {
        return linkPort.listAll(normalizeLimit(limit));
    }

    /**
     * 建立或重建映射。
     *
     * <p>幂等：同一对 platform/enterprise 已建链时直接返回；平台用户已链到别的本地用户时
     * 先解旧链再建新链（重建）；本地用户已被别的平台用户占用时 409，不静默抢占。</p>
     *
     * @param platformUserId   平台用户主键
     * @param enterpriseUserId 本地用户主键
     * @param tenantKey        租户键，空白回落 default
     * @return 建链后的映射
     * @throws NotFoundException  本地用户不存在
     * @throws ConflictException  本地用户已被其他平台用户占用
     */
    public SsoUserLink link(long platformUserId, long enterpriseUserId, String tenantKey) {
        if (platformUserId <= 0 || enterpriseUserId <= 0) {
            throw new InvalidChatRequestException("platformUserId and enterpriseUserId must be positive");
        }
        String normalizedTenant = (tenantKey == null || tenantKey.isBlank()) ? "default" : tenantKey.trim();
        userPort.findById(enterpriseUserId)
                .orElseThrow(() -> new NotFoundException("enterprise user not found: " + enterpriseUserId));

        linkPort.findByEnterpriseUserId(enterpriseUserId).ifPresent(existing -> {
            if (existing.platformUserId() != platformUserId) {
                throw new ConflictException("enterprise user already linked to platform user "
                        + existing.platformUserId());
            }
        });
        linkPort.findByPlatformUserId(platformUserId).ifPresent(existing -> {
            if (existing.enterpriseUserId() != enterpriseUserId) {
                linkPort.unlink(platformUserId);
                log.warn("[sso-admin] rebind platformUserId={} oldEnterpriseUserId={} newEnterpriseUserId={}",
                        platformUserId, existing.enterpriseUserId(), enterpriseUserId);
            }
        });
        linkPort.link(platformUserId, enterpriseUserId, normalizedTenant);
        log.info("[sso-admin] link platformUserId={} enterpriseUserId={} tenantKey={}",
                platformUserId, enterpriseUserId, normalizedTenant);
        return linkPort.findByPlatformUserId(platformUserId)
                .orElseThrow(() -> new IllegalStateException("link write did not persist"));
    }

    /**
     * 解绑（可选删除本地用户）。
     *
     * @param platformUserId  平台用户主键
     * @param deleteLocalUser true = 解绑后删除本地用户，下次 SSO 登录可重新开户
     * @return 解绑结果
     * @throws NotFoundException 映射不存在
     * @throws ConflictException 本地用户仍被业务数据引用，无法删除
     */
    public SsoUnlinkResult unlink(long platformUserId, boolean deleteLocalUser) {
        SsoUserLink link = linkPort.findByPlatformUserId(platformUserId)
                .orElseThrow(() -> new NotFoundException("sso link not found for platform user: " + platformUserId));
        boolean unlinked = linkPort.unlink(platformUserId);
        if (!unlinked) {
            throw new ConflictException("sso link disappeared concurrently: " + platformUserId);
        }
        boolean localUserDeleted = false;
        if (deleteLocalUser) {
            try {
                localUserDeleted = userPort.deleteById(link.enterpriseUserId());
            } catch (RuntimeException ex) {
                throw new ConflictException(
                        "local user still referenced by business data; unlink only: " + link.enterpriseUserId());
            }
        }
        log.info("[sso-admin] unlink platformUserId={} enterpriseUserId={} deleteLocalUser={} deleted={}",
                platformUserId, link.enterpriseUserId(), deleteLocalUser, localUserDeleted);
        return new SsoUnlinkResult(platformUserId, link.enterpriseUserId(), true, localUserDeleted);
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIST_LIMIT;
        }
        return Math.min(limit, MAX_LIST_LIMIT);
    }

    /**
     * 解绑结果。
     *
     * @param platformUserId   平台用户主键
     * @param enterpriseUserId 本地用户主键
     * @param unlinked         是否已解除映射
     * @param localUserDeleted 是否删除了本地用户
     */
    public record SsoUnlinkResult(
            long platformUserId,
            long enterpriseUserId,
            boolean unlinked,
            boolean localUserDeleted
    ) {
    }
}
