package com.aicode.framework.observability.dto;

import com.aicode.framework.observability.domain.MonitoringComponent;
import com.aicode.framework.observability.domain.MonitoringOverviewView;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 监控概览响应体（Week 20）。
 *
 * <p>对外契约：状态用<b>字符串</b>（{@code UP} / {@code DOWN} / {@code DISABLED} / {@code UNKNOWN}），
 * 不暴露 Java 枚举序列化细节；组件顺序固定，脚本可按 key 取值。</p>
 *
 * <p>本记录<b>类型上就没有</b>任何密钥字段；端点地址在用例层已脱敏。</p>
 *
 * @param generatedAt 生成时间（UTC）
 * @param allHealthy  所有已启用组件是否都 UP（没有组件时为 false）
 * @param components  组件列表
 * @param correlation 跨后端关联信息
 */
public record MonitoringOverviewResponse(
        Instant generatedAt,
        boolean allHealthy,
        List<Component> components,
        Correlation correlation
) {

    /**
     * 视图 → 响应体。
     *
     * @param view 用例视图
     * @return 响应体；入参为 null 时抛 {@link IllegalArgumentException}
     */
    public static MonitoringOverviewResponse from(MonitoringOverviewView view) {
        if (view == null) {
            throw new IllegalArgumentException("view must not be null");
        }
        List<Component> components = view.components().stream()
                .map(Component::from)
                .toList();
        return new MonitoringOverviewResponse(
                view.generatedAt(),
                view.allHealthy(),
                components,
                Correlation.from(view.correlation()));
    }

    /**
     * 单个组件。
     *
     * @param key        稳定标识
     * @param name       展示名
     * @param enabled    是否启用
     * @param status     状态字符串
     * @param endpoint   脱敏后的端点
     * @param detail     说明
     * @param consoleUrl 控制台入口
     */
    public record Component(
            String key,
            String name,
            boolean enabled,
            String status,
            String endpoint,
            String detail,
            String consoleUrl
    ) {

        /** 从领域视图转换，状态取枚举名。 */
        static Component from(MonitoringComponent component) {
            return new Component(
                    component.key(),
                    component.name(),
                    component.enabled(),
                    component.status().name(),
                    component.endpoint(),
                    component.detail(),
                    component.consoleUrl());
        }
    }

    /**
     * 跨后端关联信息。
     *
     * @param traceId             业务 traceId
     * @param otelTraceId         OTel / Langfuse traceId
     * @param correlationTag      关联标签名
     * @param langfuseTraceUrl    Langfuse 深链
     * @param grafanaDashboardUrl Grafana 仪表盘入口
     * @param skywalkingUiUrl     SkyWalking UI 入口
     * @param queryHints          各系统检索提示
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

        /** 从领域视图转换。 */
        static Correlation from(MonitoringOverviewView.Correlation correlation) {
            return new Correlation(
                    correlation.traceId(),
                    correlation.otelTraceId(),
                    correlation.correlationTag(),
                    correlation.langfuseTraceUrl(),
                    correlation.grafanaDashboardUrl(),
                    correlation.skywalkingUiUrl(),
                    correlation.queryHints());
        }
    }
}
