package com.aicode.framework.observability.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全栈监控概览视图（Week 20）。
 *
 * <p>一次回答「四个观测后端现在活没活、同一条请求在四个系统里怎么对上」。
 * 组件列表按固定顺序输出（OTel → Prometheus → Grafana → SkyWalking → Langfuse → 网关指标），
 * 便于人读与脚本断言。</p>
 *
 * @param generatedAt 生成时间
 * @param components  各组件体检结果
 * @param correlation 跨后端关联信息（业务 traceId / OTel traceId / 关联标签 / 深链）
 */
public record MonitoringOverviewView(
        Instant generatedAt,
        List<MonitoringComponent> components,
        Correlation correlation
) {

    /** 列表与关联信息做空值归一，调用方不用判 null。 */
    public MonitoringOverviewView {
        components = components == null ? List.of() : List.copyOf(components);
        correlation = correlation == null
                ? new Correlation("", "", "", "", "", "", Map.of())
                : correlation;
    }

    /**
     * 整体是否健康：所有<b>已启用</b>组件均为 {@link MonitoringStatus#UP} 才算健康。
     *
     * <p>两条刻意语义：</p>
     * <ul>
     *   <li>{@code DISABLED} 不参与判定（没开就不该报错，否则每次没启用的后后端都会制造假故障）；</li>
     *   <li>没有任何组件时返回 {@code false}（「什么都没配」不能算健康）。</li>
     * </ul>
     *
     * @return 健康返回 true
     */
    public boolean allHealthy() {
        if (components.isEmpty()) {
            return false;
        }
        return components.stream()
                .filter(MonitoringComponent::enabled)
                .allMatch(MonitoringComponent::healthy);
    }

    /**
     * 按 key 取组件（脚本与测试用）。
     *
     * @param key 组件标识
     * @return 组件；不存在时返回空 Optional
     */
    public java.util.Optional<MonitoringComponent> component(String key) {
        return components.stream().filter(component -> component.key().equals(key)).findFirst();
    }

    /**
     * 跨后端关联信息。
     *
     * <p>四个后端的 traceId 口径不同（见 Week 19 的三条规则）：日志 / 审计 / 响应体是业务 traceId，
     * OTel 与 Langfuse 用 W3C traceId，SkyWalking 用 Base64 segmentId。故这里既给两套 ID，
     * 也给「四个系统各自怎么查」的提示，避免运维凭猜。</p>
     *
     * @param traceId            业务 traceId（与响应头 {@code X-Trace-Id} 一致）
     * @param otelTraceId        OTel / Langfuse 使用的 W3C traceId
     * @param correlationTag     跨后端关联标签名（{@code aicode.trace_id}）
     * @param langfuseTraceUrl   Langfuse trace 深链；未启用或未配 project id 时为空串
     * @param grafanaDashboardUrl Grafana 仪表盘入口；未配置时为空串
     * @param skywalkingUiUrl    SkyWalking UI 入口；未配置时为空串
     * @param queryHints         各系统的检索提示（键：logs / audit / skywalking / langfuse / prometheus）
     */
    public record Correlation(
            String traceId,
            String otelTraceId,
            String correlationTag,
            String langfuseTraceUrl,
            String grafanaDashboardUrl,
            String skywalkingUiUrl,
            Map<String, String> queryHints
    ) {

        /** 空值归一：字符串空转空串，提示表复制为不可变 Map。 */
        public Correlation {
            traceId = traceId == null ? "" : traceId;
            otelTraceId = otelTraceId == null ? "" : otelTraceId;
            correlationTag = correlationTag == null ? "" : correlationTag;
            langfuseTraceUrl = langfuseTraceUrl == null ? "" : langfuseTraceUrl;
            grafanaDashboardUrl = grafanaDashboardUrl == null ? "" : grafanaDashboardUrl;
            skywalkingUiUrl = skywalkingUiUrl == null ? "" : skywalkingUiUrl;
            queryHints = queryHints == null
                    ? Map.of()
                    : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(queryHints));
        }
    }
}
