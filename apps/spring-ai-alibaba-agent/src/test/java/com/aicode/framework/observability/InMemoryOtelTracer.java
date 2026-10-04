package com.aicode.framework.observability;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;

import java.util.List;

/**
 * 用真实 OTel SDK + 内存 exporter 构造 {@link Tracer} 的测试工具（Week 17）。
 *
 * <p>与 {@link OtelSdkTestSupport} 的区别：本类面向 Spring 上下文测试——
 * 由 {@code @TestConfiguration} 调用 {@link #tracer()} 注册为 bean，
 * 从而替换生产环境经 {@code management.tracing.*} 装配的真实 OTLP 导出链路，
 * 让「HTTP 请求 → span → 断言」不依赖外部 collector。</p>
 */
public final class InMemoryOtelTracer {

    private InMemoryOtelTracer() {
    }

    /**
     * 创建一个桥接到内存 exporter 的 Micrometer {@link Tracer}。
     *
     * @return 每次调用返回独立实例（测试之间不共享 span 缓冲）
     */
    public static Tracer tracer() {
        return build().tracer();
    }

    /**
     * 创建 tracer 与其 exporter 的组合（需要断言 span 时使用）。
     *
     * @return 组合对象
     */
    public static Bundle build() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        SdkTracerProvider provider = SdkTracerProvider.builder()
                .setSampler(Sampler.alwaysOn())
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        Tracer tracer = new OtelTracer(
                provider.get("observability-test"),
                new OtelCurrentTraceContext(),
                event -> {
                });
        return new Bundle(tracer, exporter, provider);
    }

    /**
     * tracer 与内存 exporter 的组合。
     *
     * @param tracer   Micrometer 门面
     * @param exporter 内存导出器（span 结束后同步可读）
     * @param provider SDK provider，用于释放资源
     */
    public record Bundle(Tracer tracer, InMemorySpanExporter exporter, SdkTracerProvider provider)
            implements AutoCloseable {

        /** 已结束的 span，按结束顺序。 */
        public List<SpanData> finishedSpans() {
            return exporter.getFinishedSpanItems();
        }

        @Override
        public void close() {
            provider.close();
        }
    }
}
