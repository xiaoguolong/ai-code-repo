package com.aicode.framework.platform.application;

import com.aicode.framework.platform.domain.model.PlatformUser;

import java.time.Instant;

/**
 * 平台用户视图（Week 28，仅用户管理接口使用）。
 *
 * <p>不包含密码哈希；停用状态用 {@code disabled + disabledAt} 两个字段表达，
 * 前端可直接渲染「正常 / 已停用于某时」。</p>
 *
 * @param userId     用户 ID
 * @param username   登录名
 * @param roleKey    角色键
 * @param tenantKey  租户键
 * @param disabled   是否停用
 * @param disabledAt 停用时间；正常为 null
 */
public record PlatformUserView(
        long userId,
        String username,
        String roleKey,
        String tenantKey,
        boolean disabled,
        Instant disabledAt
) {

    /**
     * 由领域模型转换。
     *
     * @param user 平台用户
     * @return 视图
     */
    public static PlatformUserView from(PlatformUser user) {
        return new PlatformUserView(
                user.id(), user.username(), user.roleKey(), user.tenantKey(),
                user.disabled(), user.disabledAt());
    }
}
