package com.aicode.framework.observability.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SkyWalking 接入配置（Week 19）。
 *
 * <p>注意：<b>Java Agent 的开关不在这里</b>。Agent 由启动参数
 * {@code -javaagent:<path> -Dskywalking.*} 决定，应用配置无法在类加载前改变它。
 * 本配置只控制「我们自己的手动埋点是否参与」，两者关系：</p>
 * <ul>
 *   <li>{@code enabled=false}（默认）：不装配手动埋点装饰器，零开销；</li>
 *   <li>{@code enabled=true}：装配装饰器；若此时 Agent 未挂载，装饰器自动降级为空实现并打 WARN。</li>
 * </ul>
 *
 * @param enabled       是否启用手动埋点（默认 false）
 * @param serviceName   Agent 配置的服务名，仅用于自检展示（真实值来自 {@code SW_AGENT_NAME}）
 * @param backendService OAP 后端地址，仅用于自检展示（真实值来自 {@code SW_AGENT_COLLECTOR_BACKEND_SERVICES}）
 * @param tagPrefix     业务标签前缀（默认 {@code aicode}，用于与 Agent 自动埋点的标签区分）
 */
@ConfigurationProperties(prefix = "observability.skywalking")
public record SkyWalkingProperties(boolean enabled, String serviceName, String backendService, String tagPrefix) {

    /**
     * 归一化后的服务名。
     *
     * @return 非空服务名，默认 {@code unknown-service}
     */
    public String resolvedServiceName() {
        return serviceName == null || serviceName.isBlank() ? "unknown-service" : serviceName.trim();
    }

    /**
     * 归一化后的 OAP 地址。
     *
     * @return 地址；未配置时为空串
     */
    public String resolvedBackendService() {
        return backendService == null ? "" : backendService.trim();
    }

    /**
     * 归一化后的标签前缀。
     *
     * @return 默认 {@code aicode}
     */
    public String resolvedTagPrefix() {
        return tagPrefix == null || tagPrefix.isBlank() ? "aicode" : tagPrefix.trim();
    }
}
