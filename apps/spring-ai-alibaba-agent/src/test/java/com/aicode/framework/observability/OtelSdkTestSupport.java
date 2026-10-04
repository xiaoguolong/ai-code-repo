package com.aicode.framework.observability;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.junit.jupiter.api.AfterEach;

import java.util.List;

/**
 * 真实 OpenTelemetry SDK 测试基座（Week 17）。
 *
 * <p>为什么不用 {@code SimpleTracer} 断言 ID：{@code SimpleTracer} 是 Micrometer 自带的假实现，
 * 其 span context 不保证生成合法的 traceId / spanId。而本周的核心契约是
 * 「OTel traceId 与既有 traceId 同源、子 span 与父 span 同一条链路」，
 * 这些必须用<b>真实的 OTel SDK + 内存 exporter</b> 验证，才能覆盖生产实际走的代码路径
 * （{@code micrometer-tracing-bridge-otel} → OTel SDK → exporter）。</p>
 *
 * <p>子类通过 {@link #tracer} 与 {@link #exporter} 断言 span 名、属性、traceId 与父子关系。</p>
 */
public abstract class OtelSdkTestSupport {

    /** 内存导出器：span 结束后同步落在这里，无需 collector。 */
    protected final InMemorySpanExporter exporter = InMemorySpanExporter.create();

    /** 真实 OTel SDK 提供者（AlwaysOn 采样，保证测试期不丢 span）。 */
    private final SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .setSampler(Sampler.alwaysOn())
            .addSpanProcessor(SimpleSpanProcessor.create(exporter))
            .build();

    /** Micrometer 门面，桥接到上面的 OTel SDK（scope 名固定为测试用）。 */
    protected final Tracer tracer = new OtelTracer(
            tracerProvider.get("otel-sdk-test"),
            new OtelCurrentTraceContext(),
            event -> {
            });

    /** 关闭 SDK provider，避免测试之间串用导出线程。 */
    @AfterEach
    void shutdownOtel() {
        tracerProvider.close();
        exporter.reset();
    }

    /** 取出已结束的 span（按结束顺序）。 */
    protected List<SpanData> finishedSpans() {
        return exporter.getFinishedSpanItems();
    }

    /** 按 span 名查找已结束的 span。 */
    protected SpanData finishedSpan(String name) {
        return finishedSpans().stream()
                .filter(span -> name.equals(span.getName()))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("no finished span named " + name
                        + ", actual=" + finishedSpans().stream().map(SpanData::getName).toList()));
    }
}
