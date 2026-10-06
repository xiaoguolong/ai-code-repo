package com.aicode.gateway.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 配置归一化测试（Week 19）。
 *
 * <p>这些「空值兜底 + 路径归一」看起来琐碎，但它们是网关启动后能不能路由对的关键：
 * 平台地址多个尾斜杠会拼出 {@code //api/...}（Spring Cloud Gateway 与 Feign 都会 404），
 * 前缀少个前导斜杠会导致鉴权白名单失效。</p>
 */
class GatewayPropertiesTest {

    @Test
    @DisplayName("空配置取默认值：本地平台地址与标准前缀")
    void blankValuesFallBackToDefaults() {
        GatewayProperties properties = new GatewayProperties(null, null, null);
        assertThat(properties.resolvedPlatformUri()).isEqualTo("http://localhost:8084");
        assertThat(properties.resolvedPlatformProxyPath()).isEqualTo("/api/v1/platform");
        assertThat(properties.resolvedGatewayApiPath()).isEqualTo("/api/v1/gateway");
    }

    @Test
    @DisplayName("路径前缀归一：缺失的前导斜杠补齐、多余的尾斜杠去掉")
    void pathPrefixesAreNormalized() {
        GatewayProperties properties = new GatewayProperties(
                "http://platform:8084///", "api/v1/platform/", "gateway/");
        assertThat(properties.resolvedPlatformUri()).isEqualTo("http://platform:8084");
        assertThat(properties.resolvedPlatformProxyPath()).isEqualTo("/api/v1/platform");
        assertThat(properties.resolvedGatewayApiPath()).isEqualTo("/gateway");
    }

    @Test
    @DisplayName("会话配置：头名/ TTL / 存储三处兜底")
    void sessionDefaults() {
        GatewaySessionProperties blank = new GatewaySessionProperties("  ", 0, "");
        assertThat(blank.resolvedTokenName()).isEqualTo("satoken");
        assertThat(blank.resolvedTtlSeconds()).isEqualTo(1800L);
        assertThat(blank.resolvedStorage()).isEqualTo("redis");

        GatewaySessionProperties custom = new GatewaySessionProperties("gw-token", -5, "redis");
        assertThat(custom.resolvedTokenName()).isEqualTo("gw-token");
        assertThat(custom.resolvedTtlSeconds()).isEqualTo(1800L);
    }

    @Test
    @DisplayName("缓存配置：TTL / 前缀 / 幂等 TTL 三处兜底")
    void catalogDefaults() {
        GatewayCatalogProperties blank = new GatewayCatalogProperties(0, "  ", 0);
        assertThat(blank.resolvedCacheTtlSeconds()).isEqualTo(30L);
        assertThat(blank.resolvedKeyPrefix()).isEqualTo("gw:catalog:agents");
        assertThat(blank.resolvedIdempotencyTtlSeconds()).isEqualTo(300L);

        GatewayCatalogProperties custom = new GatewayCatalogProperties(120, "gw:custom", 60);
        assertThat(custom.resolvedCacheTtlSeconds()).isEqualTo(120L);
        assertThat(custom.resolvedKeyPrefix()).isEqualTo("gw:custom");
        assertThat(custom.resolvedIdempotencyTtlSeconds()).isEqualTo(60L);
    }
}
