package com.aicode.framework.observability.infrastructure;

import com.aicode.core.domain.model.TokenUsage;
import com.aicode.framework.infrastructure.logging.TraceIds;
import com.aicode.framework.observability.OtelSdkTestSupport;
import com.aicode.framework.observability.domain.AgentObservabilityPort;
import com.aicode.framework.observability.domain.NoopObservabilityAdapter;
import com.aicode.framework.observability.domain.ObservabilityAttributes;
import com.aicode.framework.observability.domain.SpanKind;
import com.aicode.framework.observability.domain.SpanScope;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Span;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 观测端口 Micrometer 适配器测试（Week 17）。
 *
 * <p>用<b>真实 OTel SDK + 内存 exporter</b> 断言 traceId / spanId / 父子关系与属性，
 * 保证覆盖生产路径（Micrometer 门面 → OTel bridge → SDK），而不是假实现的偶然行为。</p>
 */
class MicrometerObservabilityAdapterTest extends OtelSdkTestSupport {

    private SimpleMeterRegistry registry;
    private AgentObservabilityPort port;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        port = new MicrometerObservabilityAdapter(tracer, registry);
    }

    @AfterEach
    void clearMdc() {
        org.slf4j.MDC.clear();
    }

    @Test
    void opensSpanWithGivenKindNameAndAttributes() {
        String scopeTraceId;
        try (SpanScope scope = port.openSpan(SpanKind.AGENT_RUN, "agent.run",
                Map.of(ObservabilityAttributes.AGENT_KEY, "medical-assistant"))) {
            assertThat(scope.spanId()).hasSize(16).matches("[0-9a-f]{16}");
            assertThat(scope.traceId()).hasSize(32).matches("[0-9a-f]{32}");
            scopeTraceId = scope.traceId();
        }

        SpanData span = finishedSpan("agent.run");
        assertThat(span.getKind()).isEqualTo(io.opentelemetry.api.trace.SpanKind.INTERNAL);
        assertThat(span.getAttributes().get(
                io.opentelemetry.api.common.AttributeKey.stringKey(ObservabilityAttributes.AGENT_KEY)))
                .isEqualTo("medical-assistant");
        assertThat(span.getAttributes().get(
                io.opentelemetry.api.common.AttributeKey.stringKey(ObservabilityAttributes.SPAN_KIND)))
                .isEqualTo("AGENT_RUN");
        assertThat(span.getTraceId()).isEqualTo(scopeTraceId);
    }

    @Test
    void childSpanKeepsSameTraceIdAndParentRelationship() {
        try (SpanScope outer = port.openSpan(SpanKind.AGENT_RUN, "agent.run", Map.of())) {
            try (SpanScope inner = port.openSpan(SpanKind.LLM_CALL, "llm.chat", Map.of())) {
                assertThat(inner.traceId()).isEqualTo(outer.traceId());
                assertThat(inner.spanId()).isNotEqualTo(outer.spanId());
            }
        }

        SpanData parent = finishedSpan("agent.run");
        SpanData child = finishedSpan("llm.chat");
        assertThat(child.getTraceId()).isEqualTo(parent.getTraceId());
        assertThat(child.getParentSpanId()).isEqualTo(parent.getSpanId());
        assertThat(child.getKind()).isEqualTo(io.opentelemetry.api.trace.SpanKind.CLIENT);
    }

    @Test
    void alignsOtelTraceIdWithMdcTraceId() {
        org.slf4j.MDC.put(TraceIds.MDC_KEY, "client-trace-123");
        // 本次请求带了合法上游 traceparent：TraceIdFilter 会把其 spanId 记进 MDC
        org.slf4j.MDC.put(TraceIds.MDC_PARENT_SPAN_ID, "00f067aa0ba902b7");

        String otelTraceId;
        try (SpanScope scope = port.openSpan(SpanKind.AGENT_RUN, "agent.run", Map.of())) {
            otelTraceId = scope.traceId();
            assertThat(otelTraceId).isEqualTo(TraceIds.otelTraceId("client-trace-123"));
        }

        SpanData span = finishedSpan("agent.run");
        assertThat(span.getTraceId()).isEqualTo(otelTraceId);
        assertThat(span.getParentSpanId()).isEqualTo("00f067aa0ba902b7");
    }

    @Test
    void withoutUpstreamParentSpanIdNoFakeParentIsCreated() {
        // 无上游 traceparent 时不设父，避免两种坏情况：
        //   「全 0 父」会让 OTel SDK 忽略整个父上下文（实测），
        //   「随机父」会在链路后端留下指向不存在 span 的假边（实测）。
        // 生产路径上子 span 由活动 span 正常建链，不落此分支（见 ObservabilityProductionTraceLinkTest）。
        org.slf4j.MDC.put(TraceIds.MDC_KEY, "client-trace-123");

        String otelTraceId;
        try (SpanScope scope = port.openSpan(SpanKind.AGENT_RUN, "agent.run", Map.of())) {
            otelTraceId = scope.traceId();
        }

        SpanData span = finishedSpan("agent.run");
        assertThat(span.getTraceId()).isEqualTo(otelTraceId).hasSize(32).matches("[0-9a-f]{32}");
        assertThat(span.getParentSpanId())
                .as("不得引用不存在的父 span")
                .isEqualTo("0000000000000000");
    }

    @Test
    void incomingTraceparentSpanIdBecomesRemoteParent() {
        org.slf4j.MDC.put(TraceIds.MDC_KEY, "4bf92f3577b34da6a3ce929d0e0e4736");
        org.slf4j.MDC.put(TraceIds.MDC_PARENT_SPAN_ID, "00f067aa0ba902b7");

        try (SpanScope scope = port.openSpan(SpanKind.SERVER, "http.server", Map.of())) {
            assertThat(scope.traceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        }

        SpanData span = finishedSpan("http.server");
        assertThat(span.getTraceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(span.getParentSpanId()).isEqualTo("00f067aa0ba902b7");
    }

    @Test
    void writesTraceIdBackToMdcSoExistingLogsStayCorrelated() {
        org.slf4j.MDC.put(TraceIds.MDC_KEY, "client-trace-123");

        try (SpanScope scope = port.openSpan(SpanKind.LLM_CALL, "llm.chat", Map.of())) {
            assertThat(TraceIds.current())
                    .as("span 内既有日志的 MDC traceId 必须仍是原值，不得被 OTel 覆盖成 32hex")
                    .isEqualTo("client-trace-123");
        }
        assertThat(TraceIds.current()).isEqualTo("client-trace-123");
    }

    @Test
    void mdcIsRestoredAfterSpanCloses() {
        try (SpanScope scope = port.openSpan(SpanKind.AGENT_RUN, "agent.run", Map.of())) {
            assertThat(scope.traceId()).isNotBlank();
        }

        assertThat(org.slf4j.MDC.get(TraceIds.MDC_KEY)).isNull();
    }

    @Test
    void differentMdcTraceIdsProduceDifferentOtelTraceIds() {
        org.slf4j.MDC.put(TraceIds.MDC_PARENT_SPAN_ID, "00f067aa0ba902b7");

        org.slf4j.MDC.put(TraceIds.MDC_KEY, "trace-a");
        String firstTraceId;
        try (SpanScope first = port.openSpan(SpanKind.AGENT_RUN, "agent.run", Map.of())) {
            firstTraceId = first.traceId();
        }
        assertThat(firstTraceId).isEqualTo(TraceIds.otelTraceId("trace-a"));

        org.slf4j.MDC.put(TraceIds.MDC_KEY, "trace-b");
        try (SpanScope second = port.openSpan(SpanKind.AGENT_RUN, "agent.run", Map.of())) {
            assertThat(second.traceId()).isEqualTo(TraceIds.otelTraceId("trace-b"));
            assertThat(second.traceId()).isNotEqualTo(firstTraceId);
        }
    }

    @Test
    void recordsErrorAndMarksSpanAsError() {
        IllegalStateException failure = new IllegalStateException("model exploded");

        try (SpanScope scope = port.openSpan(SpanKind.LLM_CALL, "llm.chat", Map.of())) {
            scope.recordError(failure);
        }

        SpanData span = finishedSpan("llm.chat");
        assertThat(span.getStatus().getStatusCode())
                .isEqualTo(io.opentelemetry.api.trace.StatusCode.ERROR);
        assertThat(span.getEvents()).anySatisfy(event ->
                assertThat(event.getName()).isEqualTo("exception"));
    }

    @Test
    void closeIsIdempotent() {
        SpanScope scope = port.openSpan(SpanKind.TOOL_CALL, "tool.call", Map.of());
        scope.close();
        scope.close();

        assertThat(finishedSpans()).hasSize(1);
    }

    @Test
    void spanScopeForbidsPromptAndPatientContentAttributes() {
        try (SpanScope scope = port.openSpan(SpanKind.LLM_CALL, "llm.chat",
                Map.of(ObservabilityAttributes.GEN_AI_REQUEST_MODEL, "deepseek-chat"))) {
            assertThat(scope.traceId()).isNotBlank();
        }

        SpanData span = finishedSpan("llm.chat");
        assertThat(span.getAttributes().asMap().keySet())
                .allSatisfy(key -> assertThat(key.getKey()).doesNotContain("prompt.text"));
        assertThat(span.getAttributes().asMap().values())
                .as("span 属性只允许标识 / 模型名 / 长度 / token / 状态")
                .noneMatch(value -> String.valueOf(value).contains("patient"));
    }

    @Test
    void mapsDomainSpanKindToMicrometerKind() {
        assertThat(MicrometerObservabilityAdapter.toMicrometerKind(SpanKind.SERVER))
                .isEqualTo(Span.Kind.SERVER);
        assertThat(MicrometerObservabilityAdapter.toMicrometerKind(SpanKind.LLM_CALL))
                .isEqualTo(Span.Kind.CLIENT);
        assertThat(MicrometerObservabilityAdapter.toMicrometerKind(SpanKind.AGENT_RUN)).isNull();
        assertThat(MicrometerObservabilityAdapter.toMicrometerKind(SpanKind.TOOL_CALL)).isNull();
    }

    @Test
    void recordsTokenUsageAsCountersOnlyWhenUsageIsKnown() {
        port.recordTokenUsage("deepseek-chat", new TokenUsage(10, 20, 30));

        assertThat(counter("llm.tokens.total",
                ObservabilityAttributes.GEN_AI_REQUEST_MODEL, "deepseek-chat",
                ObservabilityAttributes.TOKEN_TYPE, "prompt").count()).isEqualTo(10.0);
        assertThat(counter("llm.tokens.total",
                ObservabilityAttributes.GEN_AI_REQUEST_MODEL, "deepseek-chat",
                ObservabilityAttributes.TOKEN_TYPE, "completion").count()).isEqualTo(20.0);
    }

    @Test
    void skipsTokenUsageWhenUpstreamDidNotReturnUsage() {
        port.recordTokenUsage("deepseek-chat", TokenUsage.unknown());
        port.recordTokenUsage("deepseek-chat", null);

        assertThat(registry.find("llm.tokens.total").counter()).isNull();
    }

    @Test
    void recordsCountersAndTimersWithExpectedNamesAndTags() {
        port.recordCounter("agent.run.count", 1.0,
                ObservabilityAttributes.EXECUTION_STATUS, "COMPLETED",
                ObservabilityAttributes.AGENT_KEY, "medical-assistant");
        port.recordDuration("agent.run.duration", 1500L, ObservabilityAttributes.AGENT_KEY, "medical-assistant");

        assertThat(counter("agent.run.count",
                ObservabilityAttributes.EXECUTION_STATUS, "COMPLETED").count()).isEqualTo(1.0);
        assertThat(registry.find("agent.run.duration")
                .tag(ObservabilityAttributes.AGENT_KEY, "medical-assistant")
                .timer()).isNotNull();
    }

    @Test
    void ignoresNullOrBlankMetricNamesInsteadOfThrowing() {
        port.recordCounter(null, 1.0, "k", "v");
        port.recordCounter(" ", 1.0, "k", "v");
        port.recordDuration(null, 1L, "k", "v");

        assertThat(registry.getMeters()).isEmpty();
    }

    @Test
    void disabledPortProducesNoSpansAndNoMetricsAndNeverThrows() {
        AgentObservabilityPort disabled = new NoopObservabilityAdapter();

        assertThat(disabled.isEnabled()).isFalse();
        try (SpanScope scope = disabled.openSpan(SpanKind.AGENT_RUN, "agent.run", Map.of())) {
            scope.attribute("k", "v");
            scope.recordError(new IllegalStateException("ignored"));
            assertThat(scope.traceId()).isEmpty();
            assertThat(scope.spanId()).isEmpty();
            assertThat(scope.sampled()).isFalse();
        }
        disabled.recordTokenUsage("m", new TokenUsage(1, 1, 2));
        disabled.recordCounter("agent.run.count", 1.0, "k", "v");
        disabled.recordDuration("agent.run.duration", 1L, "k", "v");

        assertThat(finishedSpans())
                .as("Noop 端口不得产生任何 span").isEmpty();
        assertThat(registry.getMeters())
                .as("Noop 端口不得产生任何指标").isEmpty();
        assertThat(port.isEnabled()).isTrue();
    }

    private Counter counter(String name, String... tags) {
        Counter found = registry.find(name).tags(tags).counter();
        assertThat(found).as("counter %s with tags %s", name, Arrays.toString(tags)).isNotNull();
        return found;
    }
}
