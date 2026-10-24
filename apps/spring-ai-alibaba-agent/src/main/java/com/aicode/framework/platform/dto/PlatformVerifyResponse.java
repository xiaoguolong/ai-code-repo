package com.aicode.framework.platform.dto;

import com.aicode.framework.platform.domain.model.PlatformUser;

/**
 * 平台身份校验响应（Week 21，SSO 身份源）。
 *
 * <p>刻意**不返回**平台 token：校验不等于登录，调用方（enterprise）只需要知道「你是谁、属于哪个租户」。</p>
 *
 * @param userId    平台用户主键
 * @param username  用户名
 * @param roleKey   平台角色键
 * @param tenantKey 租户键
 */
public record PlatformVerifyResponse(long userId, String username, String roleKey, String tenantKey) {

    /**
     * 由用户记录转换。
     *
     * @param user 平台用户
     * @return 响应体
     */
    public static PlatformVerifyResponse from(PlatformUser user) {
        return new PlatformVerifyResponse(user.id(), user.username(), user.roleKey(), user.tenantKey());
    }
}
