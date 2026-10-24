package com.aicode.framework.platform.dto;

import com.aicode.framework.platform.application.PlatformMeView;
import com.aicode.framework.platform.domain.model.PlatformUser;

/**
 * 当前平台用户响应（Week 21 增加租户字段）。
 *
 * @param userId     用户主键
 * @param username   用户名
 * @param roleKey    角色键
 * @param tenantKey  租户键
 * @param tenantName 租户展示名
 */
public record PlatformMeResponse(
        long userId,
        String username,
        String roleKey,
        String tenantKey,
        String tenantName
) {

    /**
     * 兼容 Week 13–20 的三参构造器（租户回退为默认值）。
     *
     * @param userId   用户主键
     * @param username 用户名
     * @param roleKey  角色键
     */
    public PlatformMeResponse(long userId, String username, String roleKey) {
        this(userId, username, roleKey, "default", "default");
    }

    /**
     * 由用例视图转换。
     *
     * @param view 当前用户视图
     * @return 响应体
     */
    public static PlatformMeResponse from(PlatformMeView view) {
        return new PlatformMeResponse(view.userId(), view.username(), view.roleKey(),
                view.tenantKey(), view.tenantName());
    }

    /**
     * 兼容旧入口：只给用户（无租户展示名）。
     *
     * @param user 平台用户
     * @return 响应体
     */
    public static PlatformMeResponse from(PlatformUser user) {
        return new PlatformMeResponse(user.id(), user.username(), user.roleKey(),
                user.tenantKey(), user.tenantKey());
    }
}
