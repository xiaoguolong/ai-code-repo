package com.aicode.framework.observability.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 监控体系配置（Week 20）。
 *
 * <p>分三段：</p>
 * <ol>
 *   <li><b>探测</b>（{@code probe}）：超时与缓存 TTL，决定自检接口的最大耗时；</li>
 *   <li><b>后端地址</b>（{@code prometheus} / {@code grafana} / {@code links}）：只用于自检与深链，
 *       不参与数据上报；地址里的账号密码会在响应里被剥离；</li>
 *   <li><b>展示口径</b>（{@code otel}）：导出端点、采样率与导出开关，与
 *       {@code management.otlp.tracing.*} 使用同一组环境变量，避免两处配置漂移。</li>
 * </ol>
 *
 * <p>空白地址一律归一为空串，用例据此把组件标为 {@code DISABLED} 且不发起网络请求
 * （离线环境与单测不会因为「探测本机 19090 超时」而变慢）。</p>
 *
 * @param enabled    是否启用整组探测（默认 true）
 * @param probe      探测超时与缓存
 * @param prometheus Prometheus 地址
 * @param grafana    Grafana 地址与默认仪表盘
 * @param otel       OTel 导出展示口径
 * @param links      第三方控制台入口
 * @param targets    被监控的抓取目标（网关指标端点）
 */
@ConfigurationProperties(prefix = "monitoring")
public record MonitoringProperties(
        Boolean enabled,
        ProbeConfig probe,
        PrometheusConfig prometheus,
        GrafanaConfig grafana,
        OtelConfig otel,
        LinksConfig links,
        TargetsConfig targets
) {

    /** 探测默认超时（毫秒）：三个探针串行最坏 2.4s，配合 30s 缓存可接受。 */
    public static final long DEFAULT_TIMEOUT_MS = 800L;

    /** 探测结果默认缓存（秒）：与 Prometheus 默认 15s 抓取间隔同量级。 */
    public static final long DEFAULT_CACHE_TTL_SECONDS = 30L;

    /** 默认 Prometheus 地址（本地隧道入口）。 */
    public static final String DEFAULT_PROMETHEUS_BASE_URL = "http://127.0.0.1:19090";

    /** 默认 Grafana 地址（本地隧道入口）。 */
    public static final String DEFAULT_GRAFANA_BASE_URL = "http://127.0.0.1:3001";

    /** 默认仪表盘 UID（与 deploy/grafana 里的 JSON 一致）。 */
    public static final String DEFAULT_DASHBOARD_UID = "aicode-platform-overview";

    /** 默认 SkyWalking UI 地址（本地隧道入口）。 */
    public static final String DEFAULT_SKYWALKING_UI_URL = "http://127.0.0.1:8088";

    /** 默认网关指标端点。 */
    public static final String DEFAULT_GATEWAY_METRICS_URL = "http://127.0.0.1:8080/actuator/prometheus";

    /** 默认 OTLP/HTTP 端点。 */
    public static final String DEFAULT_OTEL_TRACE_ENDPOINT = "http://localhost:4318/v1/traces";

    /**
     * 生产默认值（与 {@code application.yml} 同口径），便于测试与文档引用。
     *
     * @return 默认配置
     */
    public static MonitoringProperties defaults() {
        return new MonitoringProperties(
                true,
                new ProbeConfig(DEFAULT_TIMEOUT_MS, DEFAULT_CACHE_TTL_SECONDS),
                new PrometheusConfig(DEFAULT_PROMETHEUS_BASE_URL, "/actuator/prometheus", "/-/ready"),
                new GrafanaConfig(DEFAULT_GRAFANA_BASE_URL, DEFAULT_DASHBOARD_UID),
                new OtelConfig(DEFAULT_OTEL_TRACE_ENDPOINT, 1.0d, true),
                new LinksConfig(DEFAULT_SKYWALKING_UI_URL),
                new TargetsConfig(DEFAULT_GATEWAY_METRICS_URL));
    }

    /**
     * 是否启用探测。
     *
     * @return 未配置时默认 true
     */
    public boolean resolvedEnabled() {
        return enabled == null || enabled;
    }

    /**
     * 探测超时（毫秒）。
     *
     * @return 合法值；非法值回落 {@value #DEFAULT_TIMEOUT_MS}
     */
    public long resolvedProbeTimeoutMs() {
        long value = probe == null || probe.timeoutMs() == null ? DEFAULT_TIMEOUT_MS : probe.timeoutMs();
        return value <= 0 ? DEFAULT_TIMEOUT_MS : value;
    }

    /**
     * 探测结果缓存 TTL（秒）。
     *
     * @return 非负值；非法值回落 {@value #DEFAULT_CACHE_TTL_SECONDS}
     */
    public long resolvedProbeCacheTtlSeconds() {
        long value = probe == null || probe.cacheTtlSeconds() == null
                ? DEFAULT_CACHE_TTL_SECONDS : probe.cacheTtlSeconds();
        return value < 0 ? DEFAULT_CACHE_TTL_SECONDS : value;
    }

    /**
     * Prometheus 基础地址。
     *
     * @return 去尾斜杠的地址；未配置时回落本机回环，显式空白时返回空串
     */
    public String resolvedPrometheusBaseUrl() {
        if (prometheus == null) {
            return DEFAULT_PROMETHEUS_BASE_URL;
        }
        return trimTrailingSlashes(prometheus.baseUrl());
    }

    /**
     * Prometheus 自身就绪端点路径。
     *
     * @return 以 {@code /} 开头的路径，默认 {@code /-/ready}
     */
    public String resolvedPrometheusReadyPath() {
        return normalizePath(prometheus == null ? null : prometheus.readyPath(), "/-/ready");
    }

    /**
     * 平台自己的指标路径（用于说明与脚本）。
     *
     * @return 以 {@code /} 开头的路径，默认 {@code /actuator/prometheus}
     */
    public String resolvedPrometheusSelfMetricsPath() {
        return normalizePath(prometheus == null ? null : prometheus.selfMetricsPath(), "/actuator/prometheus");
    }

    /**
     * Grafana 基础地址。
     *
     * @return 去尾斜杠的地址；未配置时回落本机回环，显式空白时返回空串
     */
    public String resolvedGrafanaBaseUrl() {
        if (grafana == null) {
            return DEFAULT_GRAFANA_BASE_URL;
        }
        return trimTrailingSlashes(grafana.baseUrl());
    }

    /**
     * 默认仪表盘 UID。
     *
     * @return 非空 UID
     */
    public String resolvedGrafanaDashboardUid() {
        return blankTo(grafana == null ? null : grafana.dashboardUid(), DEFAULT_DASHBOARD_UID);
    }

    /**
     * OTel 导出端点（仅展示）。
     *
     * @return 端点；未配置时为空串
     */
    public String resolvedOtelTraceEndpoint() {
        return otel == null ? "" : blankTo(otel.traceEndpoint(), "").trim();
    }

    /**
     * 采样率（仅展示）。
     *
     * @return [0,1] 区间内的值；非法值回落 1.0
     */
    public double resolvedOtelSamplingProbability() {
        double value = otel == null || otel.samplingProbability() == null ? 1.0d : otel.samplingProbability();
        return value < 0d || value > 1d ? 1.0d : value;
    }

    /**
     * OTLP 导出开关（仅展示，与 {@code management.otlp.tracing.export.enabled} 同源变量）。
     *
     * @return 未配置时默认 true
     */
    public boolean resolvedOtelExportEnabled() {
        return otel == null || otel.exportEnabled() == null || otel.exportEnabled();
    }

    /**
     * SkyWalking UI 入口。
     *
     * @return 去尾斜杠地址；未配置时为空串
     */
    public String resolvedSkywalkingUiUrl() {
        return trimTrailingSlashes(links == null ? null : links.skywalkingUiUrl());
    }

    /**
     * 网关指标端点（Prometheus 抓取目标之一）。
     *
     * @return 去尾斜杠 URL；未配置时为空串
     */
    public String resolvedGatewayMetricsUrl() {
        return trimTrailingSlashes(targets == null ? null : targets.gatewayMetricsUrl());
    }

    private static String trimTrailingSlashes(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String normalizePath(String raw, String fallback) {
        String value = raw == null || raw.isBlank() ? fallback : raw.trim();
        return value.startsWith("/") ? value : "/" + value;
    }

    private static String blankTo(String raw, String fallback) {
        return raw == null || raw.isBlank() ? fallback : raw.trim();
    }

    /**
     * 探测配置。
     *
     * @param timeoutMs       单次探测超时（毫秒）
     * @param cacheTtlSeconds 结果缓存 TTL（秒），0 表示不缓存
     */
    public record ProbeConfig(Long timeoutMs, Long cacheTtlSeconds) {
    }

    /**
     * Prometheus 配置。
     *
     * @param baseUrl         基础地址（如 {@code http://127.0.0.1:19090}）
     * @param selfMetricsPath 平台指标路径（展示用）
     * @param readyPath       就绪探针路径
     */
    public record PrometheusConfig(String baseUrl, String selfMetricsPath, String readyPath) {
    }

    /**
     * Grafana 配置。
     *
     * @param baseUrl      基础地址（如 {@code http://127.0.0.1:3001}）
     * @param dashboardUid 默认仪表盘 UID
     */
    public record GrafanaConfig(String baseUrl, String dashboardUid) {
    }

    /**
     * OTel 展示口径。
     *
     * @param traceEndpoint       OTLP/HTTP 端点
     * @param samplingProbability 采样率
     * @param exportEnabled       是否导出
     */
    public record OtelConfig(String traceEndpoint, Double samplingProbability, Boolean exportEnabled) {
    }

    /**
     * 第三方控制台入口。
     *
     * @param skywalkingUiUrl SkyWalking UI 地址
     */
    public record LinksConfig(String skywalkingUiUrl) {
    }

    /**
     * 抓取目标。
     *
     * @param gatewayMetricsUrl 网关指标端点
     */
    public record TargetsConfig(String gatewayMetricsUrl) {
    }
}
