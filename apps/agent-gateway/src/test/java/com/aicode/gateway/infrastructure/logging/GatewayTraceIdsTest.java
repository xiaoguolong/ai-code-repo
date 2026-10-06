package com.aicode.gateway.infrastructure.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 网关链路 ID 工具测试（Week 19）。
 *
 * <p>最关键的一条断言：<b>与平台 {@code TraceIds} 的派生结果逐位一致</b>。
 * 两侧各有一份实现（网关不能依赖平台模块），若派生规则漂移，
 * 「网关注入 traceparent → 平台采纳」这条单源链路就会静默断掉。</p>
 */
class GatewayTraceIdsTest {

    @Test
    @DisplayName("normalize：空与 null 归一为空串，超长截断到 64")
    void normalizeHandlesNullBlankAndOverlong() {
        assertThat(GatewayTraceIds.normalize(null)).isEmpty();
        assertThat(GatewayTraceIds.normalize("   ")).isEmpty();
        assertThat(GatewayTraceIds.normalize("  week19-e2e  ")).isEqualTo("week19-e2e");
        String longValue = "x".repeat(100);
        assertThat(GatewayTraceIds.normalize(longValue)).hasSize(GatewayTraceIds.MAX_LENGTH);
    }

    @Test
    @DisplayName("newTraceId/newSpanId：长度正确、非全 0、两次不同")
    void newIdsHaveW3cShape() {
        String traceId = GatewayTraceIds.newTraceId();
        String spanId = GatewayTraceIds.newSpanId();
        assertThat(traceId).hasSize(32).matches("[0-9a-f]{32}");
        assertThat(spanId).hasSize(16).matches("[0-9a-f]{16}");
        assertThat(GatewayTraceIds.newTraceId()).isNotEqualTo(traceId);
    }

    @Test
    @DisplayName("otelTraceId：合法 hex 左补 0 原样使用（与平台一致）")
    void otelTraceIdKeepsHex() {
        assertThat(GatewayTraceIds.otelTraceId("abc123"))
                .isEqualTo("0".repeat(26) + "abc123");
        assertThat(GatewayTraceIds.otelTraceId("ABC123"))
                .isEqualTo("0".repeat(26) + "abc123");
        assertThat(GatewayTraceIds.otelTraceId("0".repeat(32)))
                .isEqualTo("0".repeat(32));
    }

    @Test
    @DisplayName("otelTraceId：非 hex 走 MD5，且与平台算法结果一致（固定向量）")
    void otelTraceIdMatchesPlatformMd5() {
        // 平台 TraceIds.otelTraceId("week19-e2e") = md5("week19-e2e") 的小写 hex
        assertThat(GatewayTraceIds.otelTraceId("week19-e2e"))
                .isEqualTo(md5("week19-e2e"))
                .hasSize(32);
        assertThat(GatewayTraceIds.otelTraceId("")).isEqualTo("0".repeat(32));
    }

    @Test
    @DisplayName("parseTraceparent：合法解析、非法返回 null")
    void parseTraceparentValidates() {
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        String spanId = "00f067aa0ba902b7";
        String[] parsed = GatewayTraceIds.parseTraceparent("00-" + traceId + "-" + spanId + "-01");
        assertThat(parsed).isNotNull();
        assertThat(parsed[0]).isEqualTo(traceId);
        assertThat(parsed[1]).isEqualTo(spanId);
        assertThat(parsed[2]).isEqualTo("01");

        assertThat(GatewayTraceIds.parseTraceparent(null)).isNull();
        assertThat(GatewayTraceIds.parseTraceparent("garbage")).isNull();
        assertThat(GatewayTraceIds.parseTraceparent("01-" + traceId + "-" + spanId + "-01")).isNull();
        assertThat(GatewayTraceIds.parseTraceparent("00-" + "0".repeat(32) + "-" + spanId + "-01")).isNull();
        assertThat(GatewayTraceIds.parseTraceparent("00-" + traceId + "-" + "0".repeat(16) + "-01")).isNull();
    }

    @Test
    @DisplayName("formatTraceparent：合法拼装，非法参数返回空串")
    void formatTraceparentBuildsHeader() {
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        String spanId = "00f067aa0ba902b7";
        assertThat(GatewayTraceIds.formatTraceparent(traceId, spanId, true))
                .isEqualTo("00-" + traceId + "-" + spanId + "-01");
        assertThat(GatewayTraceIds.formatTraceparent(traceId, spanId, false))
                .isEqualTo("00-" + traceId + "-" + spanId + "-00");
        assertThat(GatewayTraceIds.formatTraceparent("", spanId, true)).isEmpty();
        assertThat(GatewayTraceIds.formatTraceparent(traceId, "", true)).isEmpty();
    }

    @Test
    @DisplayName("常量与平台契约一致：X-Trace-Id / traceparent 头名")
    void constantsMatchContract() {
        assertThat(GatewayTraceIds.HEADER_NAME).isEqualTo("X-Trace-Id");
        assertThat(GatewayTraceIds.TRACEPARENT_HEADER).isEqualTo("traceparent");
    }

    /** 独立实现的 MD5（避免调用被测代码自证）。 */
    private static String md5(String value) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(Character.forDigit((b >> 4) & 0xF, 16));
                builder.append(Character.forDigit(b & 0xF, 16));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
