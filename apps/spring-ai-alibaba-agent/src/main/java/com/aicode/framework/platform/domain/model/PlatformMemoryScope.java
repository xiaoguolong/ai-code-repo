package com.aicode.framework.platform.domain.model;

/**
 * 记忆访问范围（Week 21）：租户 + 用户二维强隔离。
 *
 * <p>该值对象**只能由服务端**从登录用户记录推导（见 {@code SecurityContextRunScopeAdapter} /
 * {@code PlatformMemoryUseCase}），禁止从请求参数、请求头构造。</p>
 *
 * @param tenantKey 租户键
 * @param userId    用户主键
 */
public record PlatformMemoryScope(String tenantKey, long userId) {

    public PlatformMemoryScope {
        if (tenantKey == null || tenantKey.isBlank()) {
            throw new IllegalArgumentException("tenantKey must not be blank");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        tenantKey = tenantKey.trim();
    }

    /**
     * 由用户记录推导 scope（租户来自库，不接受客户端指定）。
     *
     * @param user 平台用户
     * @return 该用户的最小可见范围
     */
    public static PlatformMemoryScope of(PlatformUser user) {
        return new PlatformMemoryScope(user.tenantKey(), user.id());
    }
}
