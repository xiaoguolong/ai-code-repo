package com.aicode.framework.observability.infrastructure;

import com.aicode.framework.infrastructure.logging.TraceIds;
import com.aicode.framework.observability.domain.SkyWalkingStatusView;
import com.aicode.framework.observability.support.RecordingObservabilityPort;
import com.aicode.framework.observability.support.RecordingSpanBridge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SkyWalking 自检装配测试（Week 19）。
 *
 * <p>自检的价值在于「一眼判断配置有没有真正生效」：装饰器是否装上、Agent 是否挂上、
 * 两套 traceId 是否都记录了本次请求。</p>
 */
class SkyWalkingStatusProviderTest {

    private final RecordingObservabilityPort port = new RecordingObservabilityPort();
    private final RecordingSpanBridge bridge = new RecordingSpanBridge();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("装饰器已装配且开关打开：enabled=true、bridgeAvailable=true、两个 traceId 都非空")
    void statusWhenDecorated() {
        MDC.put(TraceIds.MDC_KEY, "week19-e2e");
        SkyWalkingObservabilityAdapter decorated =
                new SkyWalkingObservabilityAdapter(port, bridge, "aicode");
        SkyWalkingStatusProvider provider = new SkyWalkingStatusProvider(
                decorated, new SkyWalkingProperties(true, "ai-code-platform", "192.168.132.128:11800", "aicode"), bridge);

        SkyWalkingStatusView view = provider.status();

        assertThat(view.enabled()).isTrue();
        assertThat(view.bridgeAvailable()).isTrue();
        assertThat(view.serviceName()).isEqualTo("ai-code-platform");
        assertThat(view.backendService()).isEqualTo("192.168.132.128:11800");
        assertThat(view.skywalkingTraceId()).isEqualTo("sw-trace");
        assertThat(view.skywalkingSpanId()).isEqualTo(1);
        assertThat(view.traceId()).isEqualTo("week19-e2e");
        assertThat(view.otelTraceId()).isEqualTo("otel-trace");
        assertThat(view.correlationTag()).isEqualTo("aicode.trace_id");
        assertThat(view.correlated()).isTrue();
    }

    @Test
    @DisplayName("未装配装饰器（默认关闭）：enabled=false，SkyWalking 侧字段为空/-1")
    void statusWhenNotDecorated() {
        MDC.put(TraceIds.MDC_KEY, "week19-e2e");
        SkyWalkingStatusProvider provider = new SkyWalkingStatusProvider(
                port, new SkyWalkingProperties(true, "ai-code-platform", "oap:11800", "aicode"),
                NoopSpanBridge.INSTANCE);

        SkyWalkingStatusView view = provider.status();

        assertThat(view.enabled()).isFalse();
        assertThat(view.bridgeAvailable()).isFalse();
        assertThat(view.skywalkingTraceId()).isEmpty();
        assertThat(view.skywalkingSpanId()).isEqualTo(-1);
        assertThat(view.correlated()).isFalse();
        // 业务 traceId 与 OTel traceId 仍应给出（自检对 Week 17/18 的链路也要可读）
        assertThat(view.traceId()).isEqualTo("week19-e2e");
        assertThat(view.otelTraceId()).isEqualTo("otel-trace");
    }

    @Test
    @DisplayName("MDC 无 traceId（非请求线程）时不自造 ID，correlated=false")
    void statusWithoutRequestContext() {
        SkyWalkingStatusProvider provider = new SkyWalkingStatusProvider(
                port, new SkyWalkingProperties(false, "", "", ""), NoopSpanBridge.INSTANCE);

        SkyWalkingStatusView view = provider.status();

        assertThat(view.traceId()).isEmpty();
        assertThat(view.serviceName()).isEqualTo("unknown-service");
        assertThat(view.backendService()).isEmpty();
        assertThat(view.correlationTag()).isEqualTo("aicode.trace_id");
        assertThat(view.correlated()).isFalse();
    }
}
