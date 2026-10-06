package com.aicode.gateway.infrastructure.logging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 网关链路 ID 过滤器测试（Week 19）。
 *
 * <p>Week 16 的契约「{@code X-Trace-Id} 原样回显」在网关这一跳同样必须成立：
 * 客户端传什么，响应头与响铃体 {@code traceId} 就是什么。网关还额外负责把它<b>派生</b>成
 * W3C {@code traceparent} 注入下游，平台才可能续接同一条链路。</p>
 */
class TraceIdWebFilterTest {

    private TraceIdWebFilter filter;
    private RecordingChain chain;

    @BeforeEach
    void setUp() {
        filter = new TraceIdWebFilter();
        chain = new RecordingChain();
    }

    @Test
    @DisplayName("客户端带 X-Trace-Id：原样回显并写进请求属性")
    void inboundTraceIdIsEchoed() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/gateway/status")
                        .header(GatewayTraceIds.HEADER_NAME, " week19-e2e ")
                        .build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getHeaders().getFirst(GatewayTraceIds.HEADER_NAME))
                .isEqualTo("week19-e2e");
        assertThat(exchange.getAttributes()).containsEntry(GatewayAttributes.ATTR_TRACE_ID, "week19-e2e");
        assertThat(exchange.getAttributes()).containsEntry(GatewayAttributes.ATTR_INBOUND_TRACE_ID, "week19-e2e");
    }

    @Test
    @DisplayName("客户端不带：生成 32 位 hex，并回写响应头")
    void missingTraceIdIsGenerated() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/gateway/status").build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        String traceId = (String) exchange.getAttributes().get(GatewayAttributes.ATTR_TRACE_ID);
        assertThat(traceId).hasSize(32).matches("[0-9a-f]{32}");
        assertThat(exchange.getResponse().getHeaders().getFirst(GatewayTraceIds.HEADER_NAME)).isEqualTo(traceId);
        assertThat(exchange.getAttributes()).containsEntry(GatewayAttributes.ATTR_INBOUND_TRACE_ID, "");
    }

    @Test
    @DisplayName("派生 traceparent：trace-id 与业务 ID 的 MD5 换算一致，并注入下游请求")
    void traceparentIsDerivedAndInjected() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/gateway/status")
                        .header(GatewayTraceIds.HEADER_NAME, "week19-e2e")
                        .build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        String traceparent = (String) exchange.getAttributes().get(GatewayAttributes.ATTR_TRACEPARENT);
        assertThat(traceparent).startsWith("00-" + GatewayTraceIds.otelTraceId("week19-e2e") + "-").endsWith("-01");
        assertThat(exchange.getResponse().getHeaders().getFirst(GatewayTraceIds.TRACEPARENT_HEADER))
                .isEqualTo(traceparent);
        // 注入下游请求头：链上传递的交换对象必须带上它
        assertThat(chain.exchange.getRequest().getHeaders().getFirst(GatewayTraceIds.TRACEPARENT_HEADER))
                .isEqualTo(traceparent);
    }

    @Test
    @DisplayName("上游已带合法 traceparent：沿用其 trace-id 与采样位，不重新派生")
    void validTraceparentIsReused() {
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        String spanId = "00f067aa0ba902b7";
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/gateway/status")
                        .header(GatewayTraceIds.TRACEPARENT_HEADER, "00-" + traceId + "-" + spanId + "-00")
                        .build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getAttributes()).containsEntry(GatewayAttributes.ATTR_TRACE_ID, traceId);
        assertThat(exchange.getAttributes().get(GatewayAttributes.ATTR_TRACEPARENT))
                .isEqualTo("00-" + traceId + "-" + spanId + "-00");
    }

    @Test
    @DisplayName("X-Trace-Id 优先于 traceparent（Week 16 契约不被 W3C 头覆盖）")
    void businessHeaderWinsOverTraceparent() {
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/gateway/status")
                        .header(GatewayTraceIds.TRACEPARENT_HEADER, "00-" + traceId + "-00f067aa0ba902b7-01")
                        .header(GatewayTraceIds.HEADER_NAME, "explicit-business-id")
                        .build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getHeaders().getFirst(GatewayTraceIds.HEADER_NAME))
                .isEqualTo("explicit-business-id");
    }

    /** 记录链上传递的交换对象。 */
    private static final class RecordingChain implements WebFilterChain {

        private ServerWebExchange exchange;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.exchange = exchange;
            return Mono.empty();
        }
    }
}
