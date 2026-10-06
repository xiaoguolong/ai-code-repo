package com.aicode.framework.observability.infrastructure;

import com.aicode.core.domain.model.TokenUsage;
import com.aicode.framework.infrastructure.logging.TraceIds;
import com.aicode.framework.observability.domain.SpanKind;
import com.aicode.framework.observability.domain.SpanScope;
import com.aicode.framework.observability.domain.TraceDimensions;
import com.aicode.framework.observability.support.RecordingObservabilityPort;
import com.aicode.framework.observability.support.RecordingSpanBridge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * SkyWalking 手动埋点装饰器测试（Week 19）。
 *
 * <p>三条必须锁死的行为：</p>
 * <ol>
 *   <li><b>叠加而非替换</b>：既有端口（OTel/Langfuse/Prometheus）的调用一个都不少；</li>
 *   <li><b>跨后端关联</b>：每个 SkyWalking span 都写业务 traceId 标签（与响应头同源，取自 MDC）；</li>
 *   <li><b>不抛异常</b>：Toolkit 故障只影响链路数据，不影响业务。</li>
 * </ol>
 */
class SkyWalkingObservabilityAdapterTest {

    private RecordingObservabilityPort delegate;
    private RecordingSpanBridge bridge;
    private SkyWalkingObservabilityAdapter adapter;

    @BeforeEach
    void setUp() {
        delegate = new RecordingObservabilityPort();
        bridge = new RecordingSpanBridge();
        adapter = new SkyWalkingObservabilityAdapter(delegate, bridge, "aicode");
        MDC.put(TraceIds.MDC_KEY, "week19-e2e");
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("openSpan：既建 SkyWalking 本地 span，也调用既有端口；写关联 traceId 与 span.kind")
    void openSpanDecoratesDelegate() {
        try (SpanScope scope = adapter.openSpan(SpanKind.AGENT_RUN, "agent.run", Map.of("agent.key", "medical-assistant"))) {
            assertThat(scope).isNotNull();
        }

        assertThat(bridge.calls).contains("start:agent.run", "stop");
        assertThat(bridge.sawTag("aicode.trace_id", "week19-e2e")).isTrue();
        assertThat(bridge.sawTag("aicode.span_kind", "AGENT_RUN")).isTrue();
        assertThat(bridge.sawTag("agent.key", "medical-assistant")).isTrue();
        assertThat(bridge.sawTag("aicode.otel_trace_id", "otel-trace")).isTrue();
        assertThat(delegate.sawCall("openSpan:AGENT_RUN:agent.run:1")).isTrue();
        assertThat(delegate.sawCall("close:agent.run")).isTrue();
    }

    @Test
    @DisplayName("空 span 名用兜底名；MDC 无 traceId 时不写关联标签")
    void blankNameAndMissingTraceId() {
        MDC.clear();
        try (SpanScope ignored = adapter.openSpan(SpanKind.LLM_CALL, "  ", null)) {
            assertThat(bridge.sawCall("start:" + SkyWalkingObservabilityAdapter.FALLBACK_OPERATION)).isTrue();
        }
        assertThat(bridge.sawTag("aicode.trace_id", "week19-e2e")).isFalse();
    }

    @Test
    @DisplayName("span 内 attribute 同时写既有端口与 SkyWalking 标签")
    void attributeGoesToBothBackends() {
        try (SpanScope scope = adapter.openSpan(SpanKind.TOOL_CALL, "tool.call", Map.of())) {
            scope.attribute("tool.name", "PatientLookupTool");
            scope.attribute("", "ignored");
            scope.attribute("blank.value", "");
        }

        assertThat(bridge.sawTag("tool.name", "PatientLookupTool")).isTrue();
        assertThat(delegate.sawCall("attribute:tool.name=PatientLookupTool")).isTrue();
        assertThat(bridge.calls).noneMatch(call -> call.equals("tag:blank.value="));
    }

    @Test
    @DisplayName("recordError：先给 SkyWalking 标错误，再标既有端口，且异常继续抛出由调用方决定")
    void recordErrorGoesToBothBackends() {
        try (SpanScope scope = adapter.openSpan(SpanKind.AGENT_RUN, "agent.run", Map.of())) {
            scope.recordError(new IllegalStateException("boom"));
        }

        assertThat(bridge.calls).contains("error:IllegalStateException");
        assertThat(delegate.sawCall("recordError:IllegalStateException")).isTrue();
    }

    @Test
    @DisplayName("close 幂等：重复关闭不会产生第二个 stop")
    void closeIsIdempotent() {
        SpanScope scope = adapter.openSpan(SpanKind.AGENT_RUN, "agent.run", Map.of());
        scope.close();
        scope.close();

        assertThat(bridge.calls.stream().filter(call -> call.equals("stop")).count()).isEqualTo(1);
        assertThat(delegate.calls.stream().filter(call -> call.startsWith("close:")).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("token 用量与指标全部透传既有端口；模型名同时写 SkyWalking 标签")
    void tokenUsageAndMetricsDelegate() {
        adapter.recordTokenUsage("deepseek-v4-pro", new TokenUsage(100, 200, 300));
        adapter.recordCounter("llm.calls", 1, "model", "deepseek-v4-pro");
        adapter.recordDuration("llm.latency", 42, "model", "deepseek-v4-pro");

        assertThat(delegate.sawCall("recordTokenUsage:deepseek-v4-pro")).isTrue();
        assertThat(delegate.sawCall("recordCounter:llm.calls")).isTrue();
        assertThat(delegate.sawCall("recordDuration:llm.latency")).isTrue();
        assertThat(bridge.sawTag("gen_ai.request.model", "deepseek-v4-pro")).isTrue();
    }

    @Test
    @DisplayName("isEnabled / currentTraceContext / activeSpanId 全部走既有端口（对外行为零变化）")
    void readMethodsDelegate() {
        assertThat(adapter.isEnabled()).isTrue();
        assertThat(adapter.currentTraceContext("t").otelTraceId()).isEqualTo("otel-trace");
        assertThat(adapter.activeSpanId()).isEqualTo("otel-span");
        assertThat(delegate.sawCall("currentTraceContext:t")).isTrue();
        assertThat(delegate.sawCall("activeSpanId")).isTrue();
    }

    @Test
    @DisplayName("beginTrace 透传给既有实现（Langfuse 维度仍生效）")
    void beginTraceDelegates() {
        try (var scope = adapter.beginTrace(new TraceDimensions("1", "P001", "agent:medical-assistant",
                java.util.List.of("MEDICAL_ASSISTANT")))) {
            assertThat(scope).isNotNull();
        }
        assertThat(delegate.sawCall("beginTrace:agent:medical-assistant")).isTrue();
    }

    @Test
    @DisplayName("桥不可用时自检字段如实返回（skyWalkingTraceId 为空、spanId 为 -1）")
    void statusAccessorsReflectBridge() {
        assertThat(adapter.bridgeAvailable()).isTrue();
        assertThat(adapter.skyWalkingTraceId()).isEqualTo("sw-trace");
        assertThat(adapter.skyWalkingSpanId()).isEqualTo(1);

        RecordingSpanBridge down = new RecordingSpanBridge();
        down.available = false;
        down.traceId = "";
        down.spanId = -1;
        SkyWalkingObservabilityAdapter offline = new SkyWalkingObservabilityAdapter(delegate, down, "aicode");
        assertThat(offline.bridgeAvailable()).isFalse();
        assertThat(offline.skyWalkingTraceId()).isEmpty();
        assertThat(offline.skyWalkingSpanId()).isEqualTo(-1);
    }

    @Test
    @DisplayName("桥实现抛异常时装饰器不冒泡（可观测故障不影响业务）")
    void bridgeFailureIsSwallowed() {
        SkyWalkingSpanBridge failing = new SkyWalkingSpanBridge() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public String currentTraceId() {
                throw new IllegalStateException("down");
            }

            @Override
            public int currentSpanId() {
                throw new IllegalStateException("down");
            }

            @Override
            public SkyWalkingSpanHandle startLocalSpan(String operationName) {
                throw new IllegalStateException("down");
            }

            @Override
            public void tagActiveSpan(String key, String value) {
                throw new IllegalStateException("down");
            }

            @Override
            public void errorActiveSpan(String message) {
                throw new IllegalStateException("down");
            }

            @Override
            public void stopSpan() {
                throw new IllegalStateException("down");
            }
        };
        SkyWalkingObservabilityAdapter resilient = new SkyWalkingObservabilityAdapter(delegate, failing, "aicode");

        assertThatCode(() -> {
            try (SpanScope scope = resilient.openSpan(SpanKind.AGENT_RUN, "agent.run", Map.of())) {
                scope.attribute("k", "v");
                scope.recordError(new RuntimeException("boom"));
            }
            resilient.recordTokenUsage("m", new TokenUsage(1, 1, 2));
            assertThat(resilient.skyWalkingTraceId()).isEmpty();
            assertThat(resilient.skyWalkingSpanId()).isEqualTo(-1);
        }).doesNotThrowAnyException();

        // 既有端口仍然被调用：故障的只是 SkyWalking 那一路
        assertThat(delegate.sawCall("openSpan:AGENT_RUN:agent.run:0")).isTrue();
    }

    @Test
    @DisplayName("标签前缀可配；null 前缀回落 aicode")
    void tagPrefixIsConfigurable() {
        SkyWalkingObservabilityAdapter custom = new SkyWalkingObservabilityAdapter(delegate, bridge, "  ");
        try (SpanScope ignored = custom.openSpan(SpanKind.AGENT_RUN, "agent.run", Map.of())) {
            assertThat(bridge.sawTag("aicode.trace_id", "week19-e2e")).isTrue();
        }

        bridge.calls.clear();
        SkyWalkingObservabilityAdapter prefixed = new SkyWalkingObservabilityAdapter(delegate, bridge, "myorg");
        try (SpanScope ignored = prefixed.openSpan(SpanKind.AGENT_RUN, "agent.run", Map.of())) {
            assertThat(bridge.sawTag("myorg.trace_id", "week19-e2e")).isTrue();
        }
    }
}
