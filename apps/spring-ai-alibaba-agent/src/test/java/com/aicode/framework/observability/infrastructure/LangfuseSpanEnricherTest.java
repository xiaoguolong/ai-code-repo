package com.aicode.framework.observability.infrastructure;

import com.aicode.framework.observability.LangfuseTestFixtures;
import com.aicode.framework.observability.domain.LangfuseAttributes;
import com.aicode.framework.observability.domain.TraceDimensions;
import com.aicode.framework.observability.domain.TraceScope;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Langfuse trace 维度富化测试（Week 18）。
 *
 * <p>官方要求把 {@code userId} / {@code sessionId} / {@code traceName} / {@code tags} 传播到 trace 内
 * <b>每一个 span</b>（过滤与聚合作用在 observation 上，只写 root span 不够）。本测试用真实 OTel SDK
 * 断言「后开的 span 也带上这些维度」，以及作用域关闭后不再污染后续 span。</p>
 */
class LangfuseSpanEnricherTest {

    private final InMemorySpanExporter exporter = InMemorySpanExporter.create();
    private final LangfuseContext context = new LangfuseContext();
    private final LangfuseSpanEnricher enricher = new LangfuseSpanEnricher(context, LangfuseTestFixtures.enabled());

    private SdkTracerProvider tracerProvider;
    private Tracer tracer;

    @BeforeEach
    void setUp() {
        tracerProvider = SdkTracerProvider.builder()
                .setSampler(Sampler.alwaysOn())
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .addSpanProcessor(enricher)
                .build();
        tracer = tracerProvider.get("langfuse-enricher-test");
    }

    @AfterEach
    void tearDown() {
        tracerProvider.close();
        exporter.reset();
    }

    @Test
    void enrichesEverySpanStartedInsideTraceScope() {
        try (TraceScope ignored = context.begin(dimensions())) {
            endSpan("agent.run");
            endSpan("llm.chat");
        }

        for (SpanData span : exporter.getFinishedSpanItems()) {
            assertThat(span.getAttributes().get(AttributeKey.stringKey(LangfuseAttributes.TRACE_NAME)))
                    .isEqualTo("agent:medical-assistant");
            assertThat(span.getAttributes().get(AttributeKey.stringKey(LangfuseAttributes.USER_ID))).isEqualTo("1");
            assertThat(span.getAttributes().get(AttributeKey.stringKey(LangfuseAttributes.SESSION_ID))).isEqualTo("P001");
            assertThat(span.getAttributes().get(AttributeKey.stringArrayKey(LangfuseAttributes.TRACE_TAGS)))
                    .containsExactly("MEDICAL_ASSISTANT", "medical-assistant");
            assertThat(span.getAttributes().get(AttributeKey.stringKey(LangfuseAttributes.ENVIRONMENT)))
                    .isEqualTo("test");
            assertThat(span.getAttributes().get(AttributeKey.stringKey(LangfuseAttributes.RELEASE)))
                    .isEqualTo("week18");
        }
    }

    @Test
    void writesOnlyDeploymentDimensionsOutsideTraceScope() {
        endSpan("http.server");

        SpanData span = exporter.getFinishedSpanItems().get(0);
        assertThat(span.getAttributes().get(AttributeKey.stringKey(LangfuseAttributes.ENVIRONMENT))).isEqualTo("test");
        assertThat(span.getAttributes().get(AttributeKey.stringKey(LangfuseAttributes.TRACE_NAME))).isNull();
        assertThat(span.getAttributes().get(AttributeKey.stringKey(LangfuseAttributes.USER_ID))).isNull();
    }

    @Test
    void restoresPreviousDimensionsWhenNestedScopeCloses() {
        try (TraceScope outer = context.begin(dimensions())) {
            try (TraceScope inner = context.begin(new TraceDimensions("2", null, "agent:other", List.of()))) {
                endSpan("inner.span");
            }
            endSpan("outer.span");
            assertThat(outer).isNotNull();
        }

        SpanData innerSpan = exporter.getFinishedSpanItems().get(0);
        SpanData outerSpan = exporter.getFinishedSpanItems().get(1);
        assertThat(innerSpan.getAttributes().get(AttributeKey.stringKey(LangfuseAttributes.TRACE_NAME)))
                .isEqualTo("agent:other");
        assertThat(innerSpan.getAttributes().get(AttributeKey.stringKey(LangfuseAttributes.SESSION_ID))).isNull();
        assertThat(outerSpan.getAttributes().get(AttributeKey.stringKey(LangfuseAttributes.TRACE_NAME)))
                .isEqualTo("agent:medical-assistant");
        assertThat(context.current()).isEmpty();
    }

    @Test
    void onlyRequiresStartCallback() {
        assertThat(enricher.isStartRequired()).isTrue();
        assertThat(enricher.isEndRequired()).isFalse();
    }

    private TraceDimensions dimensions() {
        return TraceDimensions.agentRun(1L, "medical-assistant", "MEDICAL_ASSISTANT", "P001");
    }

    private void endSpan(String name) {
        Span span = tracer.spanBuilder(name).startSpan();
        span.end();
    }
}
