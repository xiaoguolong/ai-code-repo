package com.aicode.gateway.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关路由配置（Week 19）。
 *
 * <p>路由目标用<b>配置化 URL</b> 而不是注册中心：本机只有两个进程，
 * 引入 Nacos/Eureka 会多一个常驻组件与一套新故障模式（规范 3.4，见 Spec ADR）。</p>
 *
 * @param platformUri       平台服务基础地址（Feign 出口 + 透传代理目标）
 * @param platformProxyPath 透传代理路径前缀
 * @param gatewayApiPath    网关自有用例路径前缀
 */
@ConfigurationProperties(prefix = "gateway.routes")
public record GatewayProperties(String platformUri, String platformProxyPath, String gatewayApiPath) {

    /**
     * 归一化后的平台地址（去掉所有尾部斜杠）。
     *
     * @return 形如 {@code http://host:port} 的地址
     */
    public String resolvedPlatformUri() {
        String value = platformUri == null || platformUri.isBlank()
                ? "http://localhost:8084" : platformUri.trim();
        // 循环去尾斜杠：只去一个的话 "http://host:8084///" 仍会残留，拼出 //api/v1/... 的坏 URL
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    /**
     * 归一化后的透传前缀（保证以 {@code /} 开头、不以 {@code /} 结尾）。
     *
     * @return 形如 {@code /api/v1/platform} 的前缀
     */
    public String resolvedPlatformProxyPath() {
        return normalizePrefix(platformProxyPath, "/api/v1/platform");
    }

    /**
     * 归一化后的网关 API 前缀。
     *
     * @return 形如 {@code /api/v1/gateway} 的前缀
     */
    public String resolvedGatewayApiPath() {
        return normalizePrefix(gatewayApiPath, "/api/v1/gateway");
    }

    /** 前缀归一：空值取默认，去尾部斜杠，确保前导斜杠。 */
    private static String normalizePrefix(String raw, String fallback) {
        String value = raw == null || raw.isBlank() ? fallback : raw.trim();
        if (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.startsWith("/") ? value : "/" + value;
    }
}
