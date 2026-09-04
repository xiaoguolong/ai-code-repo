package com.aicode.enterprise.infrastructure.security;

import cn.dev33.satoken.stp.StpUtil;
import com.aicode.enterprise.domain.port.AuthTokenPort;
import org.springframework.stereotype.Component;

/**
 * 基于 Sa-Token 的登录令牌适配器。StpUtil 为 Sa-Token 全局工具，登录后令牌挂在当前请求线程。
 */
@Component
public class SaTokenAuthTokenAdapter implements AuthTokenPort {

    @Override
    public String login(long userId) {
        StpUtil.login(userId);
        return StpUtil.getTokenValue();
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }
}
