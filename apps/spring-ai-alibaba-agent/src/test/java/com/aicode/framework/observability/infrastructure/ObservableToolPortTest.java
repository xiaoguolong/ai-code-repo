package com.aicode.framework.observability.infrastructure;

import com.aicode.core.domain.exception.ToolExecutionException;
import com.aicode.core.domain.model.ToolCall;
import com.aicode.core.domain.model.ToolDefinition;
import com.aicode.core.domain.model.ToolResult;
import com.aicode.core.domain.port.ToolPort;
import com.aicode.framework.observability.OtelSdkTestSupport;
import com.aicode.framework.observability.domain.AgentObservabilityPort;
import com.aicode.framework.observability.domain.NoopObservabilityAdapter;
import com.aicode.framework.observability.domain.ObservabilityAttributes;
import com.aicode.framework.observability.domain.SpanKind;
import com.aicode.framework.observability.domain.SpanScope;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tool 调用链埋点测试（Week 17）：{@code tool.call} span、工具名属性、结果长度与失败计数。
 */
class ObservableToolPortTest extends OtelSdkTestSupport {

    private SimpleMeterRegistry registry;
    private AgentObservabilityPort port;
    private RecordingTool delegate;
    private ObservableToolPort observable;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        port = new MicrometerObservabilityAdapter(tracer, registry);
        delegate = new RecordingTool();
        observable = new ObservableToolPort(delegate, port);
    }

    @Test
    void createsToolCallSpanWithToolNameAndResultLengthOnly() {
        observable.execute(new ToolCall("call-1", "PatientLookupTool", "{\"patientId\":\"P001\"}"));

        SpanData span = finishedSpan(ObservableToolPort.SPAN_NAME);
        assertThat(span.getKind()).isEqualTo(io.opentelemetry.api.trace.SpanKind.INTERNAL);
        assertThat(span.getAttributes().get(AttributeKey.stringKey(ObservabilityAttributes.TOOL_NAME)))
                .isEqualTo("PatientLookupTool");
        assertThat(span.getAttributes().get(AttributeKey.stringKey(ObservabilityAttributes.TOOL_CALL_ID)))
                .isEqualTo("call-1");
        assertThat(span.getAttributes().get(AttributeKey.stringKey(ObservabilityAttributes.TOOL_RESULT_CHARS)))
                .isEqualTo(String.valueOf(delegate.output.length()));
        assertThat(span.getAttributes().asMap().values())
                .as("工具参数与结果正文不得进入 span 属性")
                .noneMatch(value -> String.valueOf(value).contains("P001")
                        || String.valueOf(value).contains(delegate.output));
    }

    @Test
    void createsChildToolSpanUnderActiveParentSpan() {
        try (SpanScope parent = port.openSpan(SpanKind.AGENT_RUN, "agent.run", Map.of())) {
            assertThat(parent.spanId()).isNotBlank();
            observable.execute(new ToolCall("call-9", "PatientLookupTool", "{}"));
        }

        SpanData parentSpan = finishedSpan("agent.run");
        SpanData child = finishedSpan(ObservableToolPort.SPAN_NAME);
        assertThat(child.getTraceId()).isEqualTo(parentSpan.getTraceId());
        assertThat(child.getParentSpanId()).isEqualTo(parentSpan.getSpanId());
    }

    @Test
    void recordsSuccessCounterWithToolName() {
        observable.execute(new ToolCall("call-1", "PatientLookupTool", "{}"));

        assertThat(registry.find("tool.call.count")
                .tag(ObservabilityAttributes.TOOL_NAME, "PatientLookupTool")
                .tag(ObservabilityAttributes.TOOL_OUTCOME, ObservabilityAttributes.OUTCOME_SUCCESS)
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void marksErrorAndCountsFailureThenRethrows() {
        delegate.failure = new ToolExecutionException("unknown tool: Nope");

        assertThatThrownBy(() -> observable.execute(new ToolCall("call-2", "Nope", "{}")))
                .isInstanceOf(ToolExecutionException.class);

        assertThat(finishedSpan(ObservableToolPort.SPAN_NAME).getStatus().getStatusCode())
                .isEqualTo(io.opentelemetry.api.trace.StatusCode.ERROR);
        assertThat(registry.find("tool.call.count")
                .tag(ObservabilityAttributes.TOOL_OUTCOME, ObservabilityAttributes.OUTCOME_FAILURE)
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void definitionsArePassedThroughWithoutSpan() {
        assertThat(observable.definitions()).isEqualTo(delegate.definitions());
        assertThat(finishedSpans()).isEmpty();
    }

    @Test
    void disabledObservabilityStillExecutesTool() {
        ObservableToolPort bare = new ObservableToolPort(delegate, new NoopObservabilityAdapter());

        assertThat(bare.execute(new ToolCall("call-3", "PatientLookupTool", "{}")))
                .isEqualTo(new ToolResult(delegate.output));
        assertThat(finishedSpans()).isEmpty();
        assertThat(registry.getMeters()).isEmpty();
    }

    private static final class RecordingTool implements ToolPort {

        private final String output = "patient P001 risk=HIGH";
        private RuntimeException failure;

        @Override
        public List<ToolDefinition> definitions() {
            return List.of(new ToolDefinition("PatientLookupTool", "查询患者", Map.of("type", "object")));
        }

        @Override
        public ToolResult execute(ToolCall call) {
            if (failure != null) {
                throw failure;
            }
            return new ToolResult(output);
        }
    }
}
