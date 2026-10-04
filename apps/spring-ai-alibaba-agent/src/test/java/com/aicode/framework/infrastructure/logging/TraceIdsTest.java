package com.aicode.framework.infrastructure.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 链路 ID 工具测试（Week 17）：traceId 生成、OTel traceId 派生、W3C traceparent 解析与格式化。
 *
 * <p>核心契约：OTel traceId 必须由 traceId <b>确定性派生</b>（同输入恒等），
 * 否则日志与链路会各自一套 ID，无法互查。</p>
 */
class TraceIdsTest {

    private static final String VALID_TRACEPARENT =
            "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    @AfterEach
    void clearMdc() {
        org.slf4j.MDC.clear();
    }

    @Test
    void generatesNewW3cCompatibleTraceIdWhenNothingProvided() {
        String traceId = TraceIds.newTraceId();

        assertThat(traceId).hasSize(TraceIds.OTEL_TRACE_ID_LENGTH).matches("[0-9a-f]{32}");
        assertThat(TraceIds.otelTraceId(traceId)).isEqualTo(traceId);
    }

    @Test
    void keepsW3cHexTraceIdAsOtelTraceId() {
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";

        assertThat(TraceIds.otelTraceId(traceId)).isEqualTo(traceId);
    }

    @Test
    void derivesOtelTraceIdFromOpaqueTraceIdDeterministically() {
        String first = TraceIds.otelTraceId("client-trace-123");
        String second = TraceIds.otelTraceId("client-trace-123");

        assertThat(first).hasSize(32).matches("[0-9a-f]{32}").isEqualTo(second);
        assertThat(TraceIds.otelTraceId("client-trace-124")).isNotEqualTo(first);
    }

    @Test
    void derivesOtelTraceIdFromOverLongTraceIdWithoutFailing() {
        String overLong = "x".repeat(TraceIds.MAX_LENGTH + 40);

        assertThat(TraceIds.otelTraceId(overLong)).hasSize(32).matches("[0-9a-f]{32}");
    }

    @Test
    void generatesSpanIdInW3cFormat() {
        assertThat(TraceIds.newSpanId()).hasSize(16).matches("[0-9a-f]{16}");
    }

    @Test
    void parsesValidTraceparent() {
        TraceContext context = TraceIds.parseTraceparent(VALID_TRACEPARENT);

        assertThat(context.valid()).isTrue();
        assertThat(context.traceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(context.parentSpanId()).isEqualTo("00f067aa0ba902b7");
        assertThat(context.sampled()).isTrue();
    }

    @Test
    void rejectsMalformedTraceparent() {
        assertThat(TraceIds.parseTraceparent(null).valid()).isFalse();
        assertThat(TraceIds.parseTraceparent("").valid()).isFalse();
        assertThat(TraceIds.parseTraceparent("abc").valid()).isFalse();
        assertThat(TraceIds.parseTraceparent("01-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01").valid())
                .as("只支持 W3C 版本 00").isFalse();
        assertThat(TraceIds.parseTraceparent("00-zzzz-00f067aa0ba902b7-01").valid()).isFalse();
        assertThat(TraceIds.parseTraceparent("00-" + "0".repeat(32) + "-00f067aa0ba902b7-01").valid())
                .as("全 0 trace-id 非法").isFalse();
    }

    @Test
    void formatsTraceparentRoundTrip() {
        String formatted = TraceIds.formatTraceparent(
                "4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", true);

        assertThat(formatted).isEqualTo(VALID_TRACEPARENT);
        assertThat(TraceIds.parseTraceparent(formatted).traceId())
                .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(TraceIds.formatTraceparent("4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", false))
                .endsWith("-00");
    }

    @Test
    void readsTraceIdFromMdc() {
        org.slf4j.MDC.put(TraceIds.MDC_KEY, "mdc-trace");

        assertThat(TraceIds.current()).isEqualTo("mdc-trace");
    }

    @Test
    void currentReturnsEmptyStringWithoutMdcValue() {
        assertThat(TraceIds.current()).isEmpty();
    }

    @Test
    void normalizeTrimsAndTruncates() {
        assertThat(TraceIds.normalize("  abc  ")).isEqualTo("abc");
        assertThat(TraceIds.normalize(null)).isEmpty();
        assertThat(TraceIds.normalize("y".repeat(200))).hasSize(TraceIds.MAX_LENGTH);
    }
}
