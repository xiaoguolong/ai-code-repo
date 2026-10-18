package com.aicode.framework.observability.application;

import com.aicode.framework.infrastructure.logging.TraceIds;
import com.aicode.framework.observability.domain.AgentObservabilityPort;
import com.aicode.framework.observability.domain.MonitoringComponent;
import com.aicode.framework.observability.domain.MonitoringOverviewView;
import com.aicode.framework.observability.domain.MonitoringProbePort;
import com.aicode.framework.observability.domain.MonitoringProbeResult;
import com.aicode.framework.observability.domain.MonitoringStatus;
import com.aicode.framework.observability.domain.SkyWalkingStatusView;
import com.aicode.framework.observability.domain.TraceContextView;
import com.aicode.framework.observability.infrastructure.LangfuseProperties;
import com.aicode.framework.observability.infrastructure.MonitoringProperties;
import com.aicode.framework.observability.infrastructure.SkyWalkingProperties;
import com.aicode.framework.observability.infrastructure.SkyWalkingStatusProvider;
import com.aicode.framework.platform.domain.exception.PlatformAccessDeniedException;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全栈监控概览用例（Week 20）。
 *
 * <p>把此前各自为政的四个观测后端（OTel / Prometheus+Grafana / SkyWalking / Langfuse）
 * 与网关指标目标收进一张体检表，并给出「同一条请求在四个系统里怎么查」的关联信息。</p>
 *
 * <p><b>语义红线</b>：</p>
 * <ul>
 *   <li>没启用 → {@code DISABLED}，不算故障；启用了但探不通 → {@code DOWN}，必须暴露；</li>
 *   <li>探测失败<b>不抛异常</b>（自检接口在后端挂掉时最有价值）；</li>
 *   <li>端点地址做凭据剥离，响应里不出现账号密码；</li>
 *   <li>仅管理员可见（会暴露内部地址与拓扑）。</li>
 * </ul>
 */
@Service
public class PlatformMonitoringUseCase {

    private final AgentObservabilityPort observability;
    private final LangfuseProperties langfuseProperties;
    private final SkyWalkingStatusProvider skyWalkingStatusProvider;
    private final SkyWalkingProperties skyWalkingProperties;
    private final MonitoringProperties properties;
    private final MonitoringProbePort probe;
    private final PlatformPermissionChecker permissionChecker;

    /**
     * @param observability            观测端口（可能已被 SkyWalking 装饰）
     * @param langfuseProperties       Langfuse 配置（开关与 host）
     * @param skyWalkingStatusProvider SkyWalking 自检装配
     * @param skyWalkingProperties     SkyWalking 配置（判断「配置开了但 Agent 没挂」）
     * @param properties               监控配置
     * @param probe                    HTTP 健康探针
     * @param permissionChecker        权限校验（仅管理员）
     */
    public PlatformMonitoringUseCase(
            AgentObservabilityPort observability,
            LangfuseProperties langfuseProperties,
            SkyWalkingStatusProvider skyWalkingStatusProvider,
            SkyWalkingProperties skyWalkingProperties,
            MonitoringProperties properties,
            MonitoringProbePort probe,
            PlatformPermissionChecker permissionChecker
    ) {
        this.observability = observability;
        this.langfuseProperties = langfuseProperties;
        this.skyWalkingStatusProvider = skyWalkingStatusProvider;
        this.skyWalkingProperties = skyWalkingProperties;
        this.properties = properties;
        this.probe = probe;
        this.permissionChecker = permissionChecker;
    }

    /**
     * 生成监控概览。
     *
     * @param userId 调用者用户 ID（必须是管理员）
     * @return 概览视图；每个组件都有状态与说明
     * @throws PlatformAccessDeniedException 非管理员调用
     */
    public MonitoringOverviewView overview(long userId) {
        if (!permissionChecker.isAdmin(userId)) {
            throw new PlatformAccessDeniedException("admin role required for monitoring overview");
        }
        List<MonitoringComponent> components = new ArrayList<>();
        components.add(otelComponent());
        components.add(prometheusComponent());
        components.add(grafanaComponent());
        components.add(skyWalkingComponent());
        components.add(langfuseComponent());
        components.add(gatewayMetricsComponent());
        return new MonitoringOverviewView(Instant.now(), components, correlation());
    }

    /** OTel：只看「埋点是否开 + 是否导出」，端点可达性由 SDK 自己重试，不在此探测。 */
    private MonitoringComponent otelComponent() {
        boolean enabled = observability.isEnabled() && properties.resolvedOtelExportEnabled();
        String endpoint = sanitizeUrl(properties.resolvedOtelTraceEndpoint());
        String detail = enabled
                ? "OTLP/HTTP；采样率 " + properties.resolvedOtelSamplingProbability()
                : "observability.enabled=false 或 OTLP 导出已关闭";
        return new MonitoringComponent("otel", "OpenTelemetry", enabled,
                enabled ? MonitoringStatus.UP : MonitoringStatus.DISABLED, endpoint, detail, "");
    }

    /** Prometheus：探自身 {@code /-/ready}；未配置地址时不探测。 */
    private MonitoringComponent prometheusComponent() {
        String base = sanitizeUrl(properties.resolvedPrometheusBaseUrl());
        boolean enabled = properties.resolvedEnabled() && !base.isEmpty();
        if (!enabled) {
            return disabled("prometheus", "Prometheus", base, "monitoring.enabled=false 或未配置地址");
        }
        MonitoringProbeResult result = probe.probe(base + properties.resolvedPrometheusReadyPath());
        return new MonitoringComponent("prometheus", "Prometheus", true, statusOf(result), base,
                result.reachable()
                        ? "就绪；抓取 " + properties.resolvedPrometheusSelfMetricsPath() + "（15s）"
                        : result.detail(),
                base + "/graph");
    }

    /** Grafana：探 {@code /api/health}；控制台直接给默认仪表盘深链。 */
    private MonitoringComponent grafanaComponent() {
        String base = sanitizeUrl(properties.resolvedGrafanaBaseUrl());
        boolean enabled = properties.resolvedEnabled() && !base.isEmpty();
        if (!enabled) {
            return disabled("grafana", "Grafana", base, "monitoring.enabled=false 或未配置地址");
        }
        MonitoringProbeResult result = probe.probe(base + "/api/health");
        String dashboard = base + "/d/" + properties.resolvedGrafanaDashboardUid();
        return new MonitoringComponent("grafana", "Grafana", true, statusOf(result), base,
                result.reachable()
                        ? "仪表盘 " + properties.resolvedGrafanaDashboardUid()
                        : result.detail(),
                dashboard);
    }

    /** SkyWalking：启用但 Agent 未挂载 → DOWN（配置与事实不一致必须暴露）。 */
    private MonitoringComponent skyWalkingComponent() {
        SkyWalkingStatusView status = skyWalkingStatusProvider.status();
        boolean enabled = skyWalkingProperties.enabled();
        MonitoringStatus state = !enabled
                ? MonitoringStatus.DISABLED
                : (status.bridgeAvailable() ? MonitoringStatus.UP : MonitoringStatus.DOWN);
        String detail = enabled
                ? "service=" + status.serviceName() + "；agentAttached=" + status.bridgeAvailable()
                : "observability.skywalking.enabled=false（HTTP/JDBC/Redis/JVM 由 Java Agent 提供）";
        return new MonitoringComponent("skywalking", "SkyWalking", enabled, state,
                sanitizeUrl(status.backendService()), detail,
                sanitizeUrl(properties.resolvedSkywalkingUiUrl()));
    }

    /** Langfuse：启用才探 {@code /api/public/health}；关闭时零网络请求。 */
    private MonitoringComponent langfuseComponent() {
        boolean enabled = langfuseProperties.resolvedEnabled();
        String base = sanitizeUrl(langfuseProperties.resolvedHost());
        if (!enabled) {
            return disabled("langfuse", "Langfuse", base, "langfuse.enabled=false");
        }
        MonitoringProbeResult result = probe.probe(base + "/api/public/health");
        return new MonitoringComponent("langfuse", "Langfuse", true, statusOf(result), base,
                result.reachable() ? "LLM trace / token / 成本 / Prompt 管理" : result.detail(),
                base);
    }

    /** 网关指标端点：验证 Prometheus 的第二个抓取目标确实开了指标出口。 */
    private MonitoringComponent gatewayMetricsComponent() {
        String url = sanitizeUrl(properties.resolvedGatewayMetricsUrl());
        boolean enabled = properties.resolvedEnabled() && !url.isEmpty();
        if (!enabled) {
            return disabled("gateway-metrics", "Gateway Metrics", url,
                    "monitoring.enabled=false 或未配置网关指标端点");
        }
        MonitoringProbeResult result = probe.probe(url);
        return new MonitoringComponent("gateway-metrics", "Gateway Metrics", true, statusOf(result), url,
                result.reachable() ? "网关 /actuator/prometheus 可抓取" : result.detail(), "");
    }

    /** 跨后端关联：业务 traceId / OTel traceId / 关联标签 / 三个控制台深链 / 检索提示。 */
    private MonitoringOverviewView.Correlation correlation() {
        String traceId = TraceIds.current();
        TraceContextView context = observability.currentTraceContext(traceId);
        String otelTraceId = context.otelTraceId();
        String langfuseUrl = langfuseProperties.resolvedEnabled()
                ? langfuseProperties.traceUrl(otelTraceId).orElse("")
                : "";
        String grafanaBase = sanitizeUrl(properties.resolvedGrafanaBaseUrl());
        String grafanaUrl = grafanaBase.isEmpty()
                ? ""
                : grafanaBase + "/d/" + properties.resolvedGrafanaDashboardUid();

        Map<String, String> hints = new LinkedHashMap<>();
        String correlationTag = skyWalkingStatusProvider.status().correlationTag();
        hints.put("logs", "traceId=" + traceId);
        hints.put("audit", "audit_log.trace_id=" + traceId);
        hints.put("skywalking", correlationTag + " = " + traceId);
        hints.put("langfuse", "traceId = " + otelTraceId);
        hints.put("prometheus", "指标无 trace 维度，按 application / agent_key / gen_ai_request_model 聚合");
        return new MonitoringOverviewView.Correlation(
                traceId,
                otelTraceId,
                correlationTag,
                langfuseUrl,
                grafanaUrl,
                sanitizeUrl(properties.resolvedSkywalkingUiUrl()),
                hints);
    }

    private static MonitoringStatus statusOf(MonitoringProbeResult result) {
        return result != null && result.reachable() ? MonitoringStatus.UP : MonitoringStatus.DOWN;
    }

    private static MonitoringComponent disabled(String key, String name, String endpoint, String detail) {
        return new MonitoringComponent(key, name, false, MonitoringStatus.DISABLED, endpoint, detail, "");
    }

    /**
     * 剥离 URL 中的账号密码（配置可能写成 {@code http://user:pass@host}）。
     *
     * <p>解析失败时用正则兜底，保证「任何情况下响应里都不出现凭据」。</p>
     *
     * @param raw 原始地址
     * @return 脱敏地址；空白返回空串
     */
    static String sanitizeUrl(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return "";
        }
        try {
            URI uri = URI.create(value);
            if (uri.getRawUserInfo() == null) {
                return value;
            }
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(),
                    uri.getPath(), uri.getQuery(), uri.getFragment()).toString();
        } catch (Exception ex) {
            return value.replaceAll("//[^/@]*@", "//");
        }
    }
}
