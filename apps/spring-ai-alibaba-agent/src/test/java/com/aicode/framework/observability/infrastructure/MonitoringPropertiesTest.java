package com.aicode.framework.observability.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 监控体系配置测试（Week 20）。
 *
 * <p>锁死三件事：默认值必须能「开箱可用」（本地 Prometheus/Grafana 走回环）、
 * 空白地址必须降级为「不探测」、非法数值必须回落到安全默认值而不是把超时设成 0。</p>
 */
class MonitoringPropertiesTest {

    @Test
    @DisplayName("默认值：探针 800ms / 缓存 30s，Prometheus 与 Grafana 指向本机回环")
    void defaultsAreUsable() {
        MonitoringProperties properties = MonitoringProperties.defaults();

        assertThat(properties.resolvedEnabled()).isTrue();
        assertThat(properties.resolvedProbeTimeoutMs()).isEqualTo(800L);
        assertThat(properties.resolvedProbeCacheTtlSeconds()).isEqualTo(30L);
        assertThat(properties.resolvedPrometheusBaseUrl()).isEqualTo("http://127.0.0.1:19090");
        assertThat(properties.resolvedPrometheusReadyPath()).isEqualTo("/-/ready");
        assertThat(properties.resolvedGrafanaBaseUrl()).isEqualTo("http://127.0.0.1:3001");
        assertThat(properties.resolvedGrafanaDashboardUid()).isEqualTo("aicode-platform-overview");
        assertThat(properties.resolvedSkywalkingUiUrl()).isEqualTo("http://127.0.0.1:8088");
        assertThat(properties.resolvedGatewayMetricsUrl())
                .isEqualTo("http://127.0.0.1:8080/actuator/prometheus");
        assertThat(properties.resolvedOtelSamplingProbability()).isEqualTo(1.0d);
    }

    @Test
    @DisplayName("尾部斜杠被去掉：拼 /-/ready 与 /api/health 时不会出现双斜杠")
    void baseUrlsAreNormalized() {
        MonitoringProperties properties = new MonitoringProperties(
                true,
                new MonitoringProperties.ProbeConfig(500L, 10L),
                new MonitoringProperties.PrometheusConfig("http://prom:19090///", null, null),
                new MonitoringProperties.GrafanaConfig("http://grafana:3001/", null),
                new MonitoringProperties.OtelConfig("http://collector:4318/v1/traces", 0.1d, true),
                new MonitoringProperties.LinksConfig("http://sw:8088/"),
                new MonitoringProperties.TargetsConfig("http://gateway:8080/actuator/prometheus/"));

        assertThat(properties.resolvedPrometheusBaseUrl()).isEqualTo("http://prom:19090");
        assertThat(properties.resolvedGrafanaBaseUrl()).isEqualTo("http://grafana:3001");
        assertThat(properties.resolvedSkywalkingUiUrl()).isEqualTo("http://sw:8088");
        assertThat(properties.resolvedGatewayMetricsUrl())
                .isEqualTo("http://gateway:8080/actuator/prometheus");
        assertThat(properties.resolvedPrometheusSelfMetricsPath()).isEqualTo("/actuator/prometheus");
        assertThat(properties.resolvedGrafanaDashboardUid()).isEqualTo("aicode-platform-overview");
        assertThat(properties.resolvedOtelSamplingProbability()).isEqualTo(0.1d);
    }

    @Test
    @DisplayName("空白地址 → 空串（调用方据此判定 DISABLED，不去打无意义的网络请求）")
    void blankUrlsResolveToEmpty() {
        MonitoringProperties properties = new MonitoringProperties(
                true,
                null,
                new MonitoringProperties.PrometheusConfig("  ", null, null),
                new MonitoringProperties.GrafanaConfig("", null),
                new MonitoringProperties.OtelConfig("", null, null),
                new MonitoringProperties.LinksConfig(null),
                new MonitoringProperties.TargetsConfig(null));

        assertThat(properties.resolvedPrometheusBaseUrl()).isEmpty();
        assertThat(properties.resolvedGrafanaBaseUrl()).isEmpty();
        assertThat(properties.resolvedSkywalkingUiUrl()).isEmpty();
        assertThat(properties.resolvedGatewayMetricsUrl()).isEmpty();
        assertThat(properties.resolvedOtelTraceEndpoint()).isEmpty();
        assertThat(properties.resolvedOtelExportEnabled()).isTrue();
    }

    @Test
    @DisplayName("非法数值：超时 <=0 回落 800ms，缓存 <0 回落 30s，采样率超界回落 1.0")
    void invalidNumbersFallBackToDefaults() {
        MonitoringProperties properties = new MonitoringProperties(
                null,
                new MonitoringProperties.ProbeConfig(0L, -5L),
                null,
                null,
                new MonitoringProperties.OtelConfig(null, 7.5d, null),
                null,
                null);

        assertThat(properties.resolvedEnabled()).isTrue();
        assertThat(properties.resolvedProbeTimeoutMs()).isEqualTo(800L);
        assertThat(properties.resolvedProbeCacheTtlSeconds()).isEqualTo(30L);
        assertThat(properties.resolvedPrometheusBaseUrl()).isEqualTo("http://127.0.0.1:19090");
        assertThat(properties.resolvedOtelSamplingProbability()).isEqualTo(1.0d);
        assertThat(properties.resolvedOtelExportEnabled()).isTrue();
        assertThat(properties.resolvedGatewayMetricsUrl()).isEmpty();
    }

    @Test
    @DisplayName("enabled=false 可显式关闭整组探测（单测 / 离线环境）")
    void canBeDisabled() {
        MonitoringProperties properties = new MonitoringProperties(
                false, null, null, null, null, null, null);

        assertThat(properties.resolvedEnabled()).isFalse();
    }
}
