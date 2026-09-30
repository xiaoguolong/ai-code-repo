package com.aicode.framework.platform.infrastructure.security;

/**
 * 请求线程内标记「Platform Run」上下文，供 {@link AuthorizingToolPort} 区分平台调度与直连 Run。
 */
public final class PlatformSecurityContext {

    private static final ThreadLocal<Long> PLATFORM_RUN_USER = new ThreadLocal<>();

    private PlatformSecurityContext() {
    }

    /** 进入 Platform Run 调度前调用。 */
    public static void beginPlatformRun(long userId) {
        PLATFORM_RUN_USER.set(userId);
    }

    /** Platform Run 结束后清除（必须在 finally 中调用）。 */
    public static void clear() {
        PLATFORM_RUN_USER.remove();
    }

    /** 当前线程是否处于 Platform Run 上下文。 */
    public static boolean isPlatformRun() {
        return PLATFORM_RUN_USER.get() != null;
    }

    /** 当前 Platform Run 用户 ID。 */
    public static Long currentPlatformUserId() {
        return PLATFORM_RUN_USER.get();
    }
}
