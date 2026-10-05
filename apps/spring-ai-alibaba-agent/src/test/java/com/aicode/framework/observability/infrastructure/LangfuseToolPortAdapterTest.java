package com.aicode.framework.observability.infrastructure;

import com.aicode.core.domain.model.ToolCall;
import com.aicode.core.domain.model.ToolDefinition;
import com.aicode.core.domain.model.ToolResult;
import com.aicode.core.domain.port.ToolPort;
import com.aicode.framework.observability.GuardrailStub;
import com.aicode.framework.observability.LangfuseTestFixtures;
import com.aicode.framework.observability.OtelSdkTestSupport;
import com.aicode.framework.observability.domain.AgentObservabilityPort;
import com.aicode.framework.observability.domain.LangfuseAttributes;
import com.aicode.framework.observability.domain.ModelPrice;
import com.aicode.framework.observability.domain.ObservabilityAttributes;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tool 调用链的 Langfuse 观测测试（Week 18）：观测类型落 {@code tool}，
 * 正文（参数与结果）只在开启采集时上报且已脱敏；关闭时 Week 17 行为不变。
 */
class LangfuseToolPortAdapterTest extends OtelSdkTestSupport {

    private SimpleMeterRegistry registry;
    private AgentObservabilityPort port;
    private RecordingToolPort delegate;
    private LangfusePromptTracker tracker;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        port = new MicrometerObservabilityAdapter(tracer, registry);
        delegate = new RecordingToolPort();
        tracker = new LangfusePromptTracker();
    }

    @Test
    void marksToolObservationWithoutContentByDefault() {
        ObservableToolPort observable = new ObservableToolPort(delegate, port, support(false));

        observable.execute(call("{\"patientId\":\"P001\"}"));

        SpanData span = finishedSpan(ObservableToolPort.SPAN_NAME);
        assertThat(string(span, LangfuseAttributes.OBSERVATION_TYPE)).isEqualTo(LangfuseAttributes.TYPE_TOOL);
        assertThat(string(span, LangfuseAttributes.OBSERVATION_METADATA_PREFIX + "toolCallId"))
                .isEqualTo("query_patient");
        assertThat(string(span, LangfuseAttributes.OBSERVATION_INPUT)).isNull();
        assertThat(string(span, LangfuseAttributes.OBSERVATION_OUTPUT)).isNull();
        assertThat(string(span, ObservabilityAttributes.TOOL_RESULT_CHARS)).isNotBlank();
    }

    @Test
    void capturesMaskedArgumentsAndResultWhenEnabled() {
        ObservableToolPort observable = new ObservableToolPort(delegate, port, support(true));

        observable.execute(call("{\"patientId\":\"P001\",\"phone\":\"13812345678\"}"));

        SpanData span = finishedSpan(ObservableToolPort.SPAN_NAME);
        assertThat(string(span, LangfuseAttributes.OBSERVATION_INPUT))
                .contains("PatientLookupTool")
                .contains("138****5678")
                .doesNotContain("13812345678");
        assertThat(string(span, LangfuseAttributes.OBSERVATION_OUTPUT)).contains("P001");
    }

    @Test
    void keepsWeek17BehaviourWhenLangfuseSupportIsDisabled() {
        ObservableToolPort observable = new ObservableToolPort(delegate, port);

        observable.execute(call("{\"patientId\":\"P001\"}"));

        SpanData span = finishedSpan(ObservableToolPort.SPAN_NAME);
        assertThat(span.getAttributes().asMap().keySet())
                .noneMatch(key -> key.getKey().startsWith("langfuse."));
        assertThat(string(span, ObservabilityAttributes.TOOL_NAME)).isEqualTo("PatientLookupTool");
        assertThat(registry.find("tool.call.count")
                .tag(ObservabilityAttributes.TOOL_OUTCOME, ObservabilityAttributes.OUTCOME_SUCCESS)
                .counter().count()).isEqualTo(1.0);
    }

    private LangfuseGenerationSupport support(boolean captureContent) {
        return LangfuseTestFixtures.generationSupport(
                LangfuseTestFixtures.tracing(captureContent, Map.<String, ModelPrice>of()),
                tracker, GuardrailStub.active());
    }

    private ToolCall call(String arguments) {
        return new ToolCall("query_patient", "PatientLookupTool", arguments);
    }

    private String string(SpanData span, String key) {
        return span.getAttributes().get(AttributeKey.stringKey(key));
    }

    /** 返回固定结果的假工具端口。 */
    private static final class RecordingToolPort implements ToolPort {

        @Override
        public List<ToolDefinition> definitions() {
            return List.of();
        }

        @Override
        public ToolResult execute(ToolCall call) {
            return new ToolResult("{\"patientId\":\"P001\",\"phone\":\"13800000000\"}");
        }
    }
}
