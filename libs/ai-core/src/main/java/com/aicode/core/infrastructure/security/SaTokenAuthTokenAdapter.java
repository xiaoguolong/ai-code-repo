package com.aicode.core.infrastructure.security;

import cn.dev33.satoken.stp.StpUtil;
import com.aicode.core.domain.port.AuthTokenPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

/**
 * 基于 Sa-Token 的登录令牌适配器。StpUtil 为 Sa-Token 全局工具，登录后令牌挂在当前请求线程。
 * sa-token 为可选依赖，仅当引入 sa-token 时才装配本适配器。
 */
@Component
@ConditionalOnClass(name = "cn.dev33.satoken.stp.StpUtil")
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
