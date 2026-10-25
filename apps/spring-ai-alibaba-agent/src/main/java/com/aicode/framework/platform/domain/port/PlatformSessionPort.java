package com.aicode.framework.platform.domain.port;

/**
 * 平台会话运维端口（Week 28）。
 *
 * <p>停用用户时要让已签发的平台 token 立即失效；实现由 Sa-Token 适配器提供。
 * 领域/应用层不依赖 Sa-Token 类型。</p>
 */
public interface PlatformSessionPort {

    /**
     * 踢出某用户的全部平台会话。
     *
     * @param userId 用户 ID
     */
    void revokeAll(long userId);
}
