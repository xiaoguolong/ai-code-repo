package com.aicode.enterprise.dto;

import com.aicode.enterprise.application.SsoAdminUseCase;

/**
 * SSO 解绑响应（Week 23）。
 *
 * @param platformUserId   平台用户主键
 * @param enterpriseUserId 本地用户主键
 * @param unlinked         是否已解除映射
 * @param localUserDeleted 是否删除了本地用户
 */
public record SsoUnlinkResponse(
        long platformUserId,
        long enterpriseUserId,
        boolean unlinked,
        boolean localUserDeleted
) {

    /**
     * 由用例结果转换。
     *
     * @param result 解绑结果
     * @return 响应体
     */
    public static SsoUnlinkResponse from(SsoAdminUseCase.SsoUnlinkResult result) {
        return new SsoUnlinkResponse(
                result.platformUserId(), result.enterpriseUserId(),
                result.unlinked(), result.localUserDeleted());
    }
}
