package com.aicode.framework.platform.dto;

import com.aicode.framework.platform.application.PlatformUserView;

import java.time.Instant;

/**
 * 平台用户响应（Week 28）。
 *
 * @param userId     用户 ID
 * @param username   登录名
 * @param roleKey    角色键
 * @param tenantKey  租户键
 * @param disabled   是否停用
 * @param disabledAt 停用时间；正常为 null
 */
public record PlatformUserResponse(
        long userId,
        String username,
        String roleKey,
        String tenantKey,
        boolean disabled,
        Instant disabledAt
) {

    /**
     * 由视图转换。
     *
     * @param view 用户视图
     * @return 响应体
     */
    public static PlatformUserResponse from(PlatformUserView view) {
        return new PlatformUserResponse(
                view.userId(), view.username(), view.roleKey(), view.tenantKey(),
                view.disabled(), view.disabledAt());
    }
}
