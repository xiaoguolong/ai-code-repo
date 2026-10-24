package com.aicode.framework.platform.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 解析（Week 22）。
 *
 * <p><b>默认不信 {@code X-Forwarded-For}</b>：只有显式配置「前面有可信反代」时才取第一跳。
 * 否则攻击者随便加一个头就能绕过登录限流——这类「以为在保护、其实给了旁路」的设计比不做还糟。</p>
 */
public final class ClientIpResolver {

    private static final String FORWARDED_FOR = "X-Forwarded-For";
    private static final String UNKNOWN = "unknown";

    private ClientIpResolver() {
    }

    /**
     * 解析客户端 IP。
     *
     * @param request           当前请求
     * @param trustForwardedFor 是否信任 X-Forwarded-For（仅当反向代理会覆盖该头时置 true）
     * @return 非空 IP 字符串；取不到时返回 {@code unknown}
     */
    public static String resolve(HttpServletRequest request, boolean trustForwardedFor) {
        if (request == null) {
            return UNKNOWN;
        }
        if (trustForwardedFor) {
            String forwarded = request.getHeader(FORWARDED_FOR);
            if (forwarded != null && !forwarded.isBlank()) {
                String first = forwarded.split(",")[0].trim();
                if (!first.isEmpty()) {
                    return first;
                }
            }
        }
        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr == null || remoteAddr.isBlank()) ? UNKNOWN : remoteAddr.trim();
    }
}
