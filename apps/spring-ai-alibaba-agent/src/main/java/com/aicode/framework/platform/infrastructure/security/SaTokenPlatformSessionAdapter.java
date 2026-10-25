package com.aicode.framework.platform.infrastructure.security;

import cn.dev33.satoken.stp.StpUtil;
import com.aicode.framework.platform.domain.port.PlatformSessionPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

/**
 * 基于 Sa-Token 的平台会话适配器（Week 28）。
 *
 * <p>{@code StpUtil.logout(userId)} 会注销该 loginId 的全部会话（多端一起踢出），
 * 单实例部署下立即生效；多实例需共享 Sa-Token 存储。</p>
 */
@Component
@ConditionalOnClass(name = "cn.dev33.satoken.stp.StpUtil")
public class SaTokenPlatformSessionAdapter implements PlatformSessionPort {

    @Override
    public void revokeAll(long userId) {
        StpUtil.logout(userId);
    }
}
