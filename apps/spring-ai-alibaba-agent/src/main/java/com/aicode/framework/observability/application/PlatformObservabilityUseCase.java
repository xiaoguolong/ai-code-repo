package com.aicode.framework.observability.application;

import com.aicode.framework.infrastructure.logging.TraceIds;
import com.aicode.framework.observability.domain.AgentObservabilityPort;
import com.aicode.framework.observability.domain.LangfuseStatusView;
import com.aicode.framework.observability.domain.TraceContextView;
import com.aicode.framework.observability.domain.TraceDimensions;
import com.aicode.framework.observability.infrastructure.LangfuseContentPolicy;
import com.aicode.framework.observability.infrastructure.LangfuseContext;
import com.aicode.framework.observability.infrastructure.LangfuseProperties;
import org.springframework.stereotype.Service;

/**
 * 链路上下文与 Langfuse 接入自检用例（Week 17 新增，Week 18 扩展）。
 *
 * <p>把「日志 / 审计 / 响应体里的 traceId」与「链路后端的 OTel traceId + spanId」一次返回，
 * 便于运维与开发核对同一条链路；Week 18 起再返回 Langfuse 的开关、端点、trace 维度与 UI 深链，
 * 用于回答「配置生效了吗、数据写到哪个 trace 上了」。</p>
 */
@Service
public class PlatformObservabilityUseCase {

    private final AgentObservabilityPort observability;
    private final LangfuseProperties langfuseProperties;
    private final LangfuseContext langfuseContext;
    private final LangfuseContentPolicy contentPolicy;

    public PlatformObservabilityUseCase(
            AgentObservabilityPort observability,
            LangfuseProperties langfuseProperties,
            LangfuseContext langfuseContext,
            LangfuseContentPolicy contentPolicy
    ) {
        this.observability = observability;
        this.langfuseProperties = langfuseProperties;
        this.langfuseContext = langfuseContext;
        this.contentPolicy = contentPolicy;
    }

    /**
     * 读取当前请求的链路上下文。
     *
     * @return 链路上下文视图；观测关闭时除 {@code traceId} 外均为空
     */
    public TraceContextView currentTraceContext() {
        return observability.currentTraceContext(TraceIds.current());
    }

    /**
     * 当前请求在 Langfuse UI 上的深链。
     *
     * @return 深链；未开启 Langfuse、未配 project ID 或无 traceId 时为 null
     */
    public String langfuseTraceUrl() {
        return langfuseProperties.traceUrl(currentTraceContext().otelTraceId()).orElse(null);
    }

    /**
     * Langfuse 接入自检：开关、端点、正文采集有效值、Prompt 管理状态、本次请求的 trace 维度。
     *
     * <p>只回掩码后的 public key，<b>不含</b> secret key。</p>
     *
     * @return 自检视图，永不为 null
     */
    public LangfuseStatusView langfuseStatus() {
        TraceContextView view = currentTraceContext();
        TraceDimensions dimensions = langfuseContext.current().orElse(null);
        boolean enabled = langfuseProperties.resolvedEnabled();
        LangfuseProperties.Prompt prompt = langfuseProperties.resolvedPrompt();

        LangfuseStatusView.Trace trace = new LangfuseStatusView.Trace(
                view.traceId(),
                view.otelTraceId(),
                enabled && dimensions != null ? dimensions.traceName() : null,
                enabled && dimensions != null ? dimensions.userId() : null,
                enabled && dimensions != null ? dimensions.sessionId() : null,
                langfuseTraceUrl());

        return new LangfuseStatusView(
                enabled,
                langfuseProperties.resolvedHost(),
                langfuseProperties.otlpEndpoint(),
                langfuseProperties.projectId() == null ? "" : langfuseProperties.projectId().trim(),
                langfuseProperties.resolvedEnvironment(),
                langfuseProperties.resolvedRelease(),
                contentPolicy.enabled(),
                langfuseProperties.resolvedMaxContentChars(),
                langfuseProperties.publicKeyMasked(),
                prompt.resolvedEnabled(),
                prompt.resolvedLabel(),
                prompt.resolvedCacheTtlSeconds(),
                langfuseProperties.modelPriceCatalog().models(),
                trace);
    }
}
