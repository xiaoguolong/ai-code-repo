package com.aicode.framework.observability.infrastructure;

import com.aicode.framework.observability.domain.AgentObservabilityPort;
import com.aicode.framework.observability.domain.NoopObservabilityAdapter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 观测能力装配（Week 17）。
 *
 * <p>开关 {@code observability.enabled}（默认 true）：</p>
 * <ul>
 *   <li>{@code true} → 优先使用容器中的 {@link Tracer}（由 micrometer-tracing-bridge-otel 提供，
 *       经 {@code management.tracing.*} 控制采样与导出）；</li>
 *   <li>{@code false} → 装配 {@link NoopObservabilityAdapter}，零 span、零指标、零开销
 *       （无观测需求环境与单测用）。</li>
 * </ul>
 *
 * <p>两个分支互斥，禁止同时注册同一 Port。</p>
 */
@Configuration
public class ObservabilityConfiguration {

    /**
     * 启用观测：需要容器中存在 {@link Tracer} 与 {@link MeterRegistry}。
     *
     * <p>不额外加 {@code @ConditionalOnMissingBean}：Tracer 由 {@code management.tracing.*} 提供，
     * {@code observability.enabled} 与 {@code management.tracing.enabled} 是两个独立开关，
     * 由使用方保持一致（与审计「显式开关、行为确定」同口径）。</p>
     */
    @Bean
    @ConditionalOnProperty(prefix = "observability", name = "enabled", havingValue = "true", matchIfMissing = true)
    AgentObservabilityPort micrometerObservabilityPort(
            Tracer tracer,
            MeterRegistry meterRegistry,
            LangfuseContext langfuseContext,
            LangfuseProperties langfuseProperties
    ) {
        // Langfuse 开启时额外写入 langfuse.* 观测类型与 trace 维度；关闭时行为与 Week 17 完全一致
        return new MicrometerObservabilityAdapter(
                tracer, meterRegistry, langfuseContext, langfuseProperties.resolvedEnabled());
    }

    /**
     * 关闭观测：空实现端口（无需 Tracer / MeterRegistry，可在无观测依赖环境启动）。
     */
    @Bean
    @ConditionalOnProperty(prefix = "observability", name = "enabled", havingValue = "false")
    AgentObservabilityPort noopObservabilityPort() {
        return new NoopObservabilityAdapter();
    }
}
