package com.aicode.framework.observability.application;

import com.aicode.framework.infrastructure.logging.TraceIds;
import com.aicode.framework.observability.domain.AgentObservabilityPort;
import com.aicode.framework.observability.domain.SkyWalkingStatusView;
import com.aicode.framework.observability.domain.SpanKind;
import com.aicode.framework.observability.domain.SpanScope;
import com.aicode.framework.observability.support.RecordingObservabilityPort;
import com.aicode.framework.observability.support.RecordingSpanBridge;
import com.aicode.framework.observability.infrastructure.LangfuseContentPolicy;
import com.aicode.framework.observability.infrastructure.LangfuseContext;
import com.aicode.framework.observability.infrastructure.SkyWalkingObservabilityAdapter;
import com.aicode.framework.observability.infrastructure.SkyWalkingProperties;
import com.aicode.framework.observability.infrastructure.SkyWalkingStatusProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SkyWalking 自检用例测试（Week 19）。
 *
 * <p>验证「Week 17/18 既有自检不受影响」与「新增 SkyWalking 自检可用」两件事同时成立：
 * 既有 4 参构造器（默认 SkyWalking 关闭）与新的 5 参构造器都要能工作。</p>
 */
class PlatformObservabilitySkyWalkingTest {

    private final RecordingObservabilityPort basePort = new RecordingObservabilityPort();
    private final RecordingSpanBridge bridge = new RecordingSpanBridge();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("既有 4 参构造器：SkyWalking 视为未启用，日常链路字段仍然可用")
    void legacyConstructorKeepsSkyWalkingOff() {
        MDC.put(TraceIds.MDC_KEY, "week19-e2e");
        PlatformObservabilityUseCase useCase = new PlatformObservabilityUseCase(
                basePort, com.aicode.framework.observability.LangfuseTestFixtures.disabled(),
                new LangfuseContext(), disabledContentPolicy());

        SkyWalkingStatusView view = useCase.skywalkingStatus();

        assertThat(view.enabled()).isFalse();
        assertThat(view.bridgeAvailable()).isFalse();
        assertThat(view.skywalkingTraceId()).isEmpty();
        // 业务 traceId 与 OTel traceId 仍从既有端口读取（Week 17 口径零变化）
        assertThat(view.traceId()).isEqualTo("week19-e2e");
        assertThat(view.otelTraceId()).isEqualTo("otel-trace");
        assertThat(view.correlated()).isFalse();
    }

    @Test
    @DisplayName("装饰器装配后：自检如实报告 SkyWalking traceId，并给出关联标签名")
    void decoratedPortIsReported() {
        MDC.put(TraceIds.MDC_KEY, "week19-e2e");
        AgentObservabilityPort decorated = new SkyWalkingObservabilityAdapter(basePort, bridge, "aicode");
        SkyWalkingProperties properties =
                new SkyWalkingProperties(true, "ai-code-platform", "192.168.132.128:11800", "aicode");
        PlatformObservabilityUseCase useCase = new PlatformObservabilityUseCase(
                decorated, com.aicode.framework.observability.LangfuseTestFixtures.disabled(),
                new LangfuseContext(), disabledContentPolicy(),
                new SkyWalkingStatusProvider(decorated, properties, bridge));

        SkyWalkingStatusView view = useCase.skywalkingStatus();

        assertThat(view.enabled()).isTrue();
        assertThat(view.bridgeAvailable()).isTrue();
        assertThat(view.serviceName()).isEqualTo("ai-code-platform");
        assertThat(view.backendService()).isEqualTo("192.168.132.128:11800");
        assertThat(view.skywalkingTraceId()).isEqualTo("sw-trace");
        assertThat(view.correlationTag()).isEqualTo("aicode.trace_id");
        assertThat(view.correlated()).isTrue();
    }

    @Test
    @DisplayName("自检不影响链路：只读调用不产生额外 span（不制造噪声数据）")
    void statusIsReadOnly() {
        AgentObservabilityPort decorated = new SkyWalkingObservabilityAdapter(basePort, bridge, "aicode");
        SkyWalkingProperties properties = new SkyWalkingProperties(true, "s", "b", "aicode");
        PlatformObservabilityUseCase useCase = new PlatformObservabilityUseCase(
                decorated, com.aicode.framework.observability.LangfuseTestFixtures.disabled(),
                new LangfuseContext(), disabledContentPolicy(),
                new SkyWalkingStatusProvider(decorated, properties, bridge));

        bridge.calls.clear();
        useCase.skywalkingStatus();

        assertThat(bridge.calls).noneMatch(call -> call.startsWith("start:"));
    }

    @Test
    @DisplayName("仍然透传既有端口的 span 能力（自检类没有偷换端口）")
    void existingPortStillWorks() {
        AgentObservabilityPort decorated = new SkyWalkingObservabilityAdapter(basePort, bridge, "aicode");
        try (SpanScope span = decorated.openSpan(SpanKind.AGENT_RUN, "agent.run", Map.of())) {
            assertThat(span.traceId()).isEqualTo("otel-trace");
        }
        assertThat(basePort.sawCall("openSpan:AGENT_RUN:agent.run:0")).isTrue();
    }

    private LangfuseContentPolicy disabledContentPolicy() {
        return new LangfuseContentPolicy(
                com.aicode.framework.observability.LangfuseTestFixtures.disabled(),
                com.aicode.framework.observability.GuardrailStub.active(),
                new ObjectMapper());
    }
}
