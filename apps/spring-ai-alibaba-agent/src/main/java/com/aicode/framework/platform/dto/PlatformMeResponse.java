package com.aicode.framework.platform.dto;

import com.aicode.framework.platform.domain.model.PlatformUser;

/**
 * 当前平台用户响应。
 */
public record PlatformMeResponse(long userId, String username, String roleKey) {

    public static PlatformMeResponse from(PlatformUser user) {
        return new PlatformMeResponse(user.id(), user.username(), user.roleKey());
    }
}
