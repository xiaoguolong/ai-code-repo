package com.aicode.framework.observability.infrastructure;

import com.aicode.core.domain.exception.ChatModelException;
import com.aicode.core.domain.model.ChatMessage;
import com.aicode.core.domain.model.ChatOptions;
import com.aicode.core.domain.model.ChatResult;
import com.aicode.core.domain.model.FinishReason;
import com.aicode.core.domain.model.MessageRole;
import com.aicode.core.domain.model.TokenUsage;
import com.aicode.core.domain.model.ToolDefinition;
import com.aicode.core.domain.port.ChatModelPort;
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
 * LLM 调用链埋点测试（Week 17）：{@code llm.chat} span 的父子关系、真实 token 属性与指标。
 */
class ObservableChatModelAdapterTest extends OtelSdkTestSupport {

    private SimpleMeterRegistry registry;
    private AgentObservabilityPort port;
    private RecordingChatModel delegate;
    private ObservableChatModelAdapter adapter;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        port = new MicrometerObservabilityAdapter(tracer, registry);
        delegate = new RecordingChatModel();
        adapter = new ObservableChatModelAdapter(delegate, port);
    }

    @Test
    void createsChildLlmChatSpanUnderActiveParentSpan() {
        try (SpanScope parent = port.openSpan(SpanKind.AGENT_RUN, "agent.run", Map.of())) {
            assertThat(parent.spanId()).isNotBlank();
            adapter.chat(messages(), options(), List.of());
        }

        SpanData parentSpan = finishedSpan("agent.run");
        SpanData llmSpan = finishedSpan(ObservableChatModelAdapter.SPAN_NAME);
        assertThat(llmSpan.getKind()).isEqualTo(io.opentelemetry.api.trace.SpanKind.CLIENT);
        assertThat(llmSpan.getTraceId()).isEqualTo(parentSpan.getTraceId());
        assertThat(llmSpan.getParentSpanId()).isEqualTo(parentSpan.getSpanId());
    }

    @Test
    void recordsRealTokenUsageOnSpanAndInMetrics() {
        adapter.chat(messages(), options(), List.of());

        SpanData span = finishedSpan(ObservableChatModelAdapter.SPAN_NAME);
        assertThat(span.getAttributes().get(AttributeKey.stringKey(ObservabilityAttributes.GEN_AI_REQUEST_MODEL)))
                .isEqualTo("deepseek-chat");
        assertThat(span.getAttributes().get(AttributeKey.stringKey(ObservabilityAttributes.GEN_AI_USAGE_PROMPT_TOKENS)))
                .isEqualTo("11");
        assertThat(span.getAttributes().get(AttributeKey.stringKey(ObservabilityAttributes.GEN_AI_USAGE_COMPLETION_TOKENS)))
                .isEqualTo("22");
        assertThat(span.getAttributes().get(AttributeKey.stringKey(ObservabilityAttributes.GEN_AI_USAGE_TOTAL_TOKENS)))
                .isEqualTo("33");

        assertThat(registry.find("llm.tokens.total")
                .tag(ObservabilityAttributes.TOKEN_TYPE, "prompt").counter().count()).isEqualTo(11.0);
        assertThat(registry.find("llm.tokens.total")
                .tag(ObservabilityAttributes.TOKEN_TYPE, "completion").counter().count()).isEqualTo(22.0);
        assertThat(registry.find("llm.call.count")
                .tag(ObservabilityAttributes.LLM_OUTCOME, ObservabilityAttributes.OUTCOME_SUCCESS)
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.find("llm.call.duration").timer()).isNotNull();
    }

    @Test
    void recordsToolCallTurnSpanWithoutTokenMetricsWhenUsageUnknown() {
        delegate.result = new ChatResult("", List.of(), FinishReason.TOOL_CALLS, TokenUsage.unknown(), "deepseek-chat");

        adapter.chat(messages(), options(), List.of());

        assertThat(finishedSpan(ObservableChatModelAdapter.SPAN_NAME)).isNotNull();
        assertThat(registry.find("llm.tokens.total").counter()).isNull();
        assertThat(registry.find("llm.call.count").counter()).isNotNull();
    }

    @Test
    void marksSpanErrorAndCountsFailureThenRethrows() {
        delegate.failure = new ChatModelException("upstream 500");

        assertThatThrownBy(() -> adapter.chat(messages(), options(), List.of()))
                .isInstanceOf(ChatModelException.class)
                .hasMessageContaining("upstream 500");

        assertThat(finishedSpan(ObservableChatModelAdapter.SPAN_NAME).getStatus().getStatusCode())
                .isEqualTo(io.opentelemetry.api.trace.StatusCode.ERROR);
        assertThat(registry.find("llm.call.count")
                .tag(ObservabilityAttributes.LLM_OUTCOME, ObservabilityAttributes.OUTCOME_FAILURE)
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void neverPutsMessageContentIntoSpanAttributes() {
        adapter.chat(List.of(new ChatMessage(MessageRole.USER, "患者张三 13812345678 的血压")),
                options(), List.of());

        assertThat(finishedSpan(ObservableChatModelAdapter.SPAN_NAME).getAttributes().asMap().values())
                .noneMatch(value -> String.valueOf(value).contains("张三")
                        || String.valueOf(value).contains("13812345678"));
    }

    @Test
    void delegatesToUnderlyingPortUnchanged() {
        ChatResult expected = delegate.result;

        assertThat(adapter.chat(messages(), options(), List.of())).isEqualTo(expected);
    }

    @Test
    void disabledObservabilityBypassesSpanAndMetrics() {
        ObservableChatModelAdapter bare = new ObservableChatModelAdapter(delegate, new NoopObservabilityAdapter());

        assertThat(bare.chat(messages(), options(), List.of())).isEqualTo(delegate.result);
        assertThat(finishedSpans()).isEmpty();
        assertThat(registry.getMeters()).isEmpty();
    }

    private List<ChatMessage> messages() {
        return List.of(new ChatMessage(MessageRole.USER, "你好"));
    }

    private ChatOptions options() {
        return new ChatOptions("deepseek-chat", 0.7, 2048);
    }

    /** 可编排结果的假模型端口。 */
    private static final class RecordingChatModel implements ChatModelPort {

        private ChatResult result = new ChatResult("答案", List.of(), FinishReason.STOP,
                new TokenUsage(11, 22, 33), "deepseek-chat");
        private RuntimeException failure;

        @Override
        public ChatResult chat(List<ChatMessage> messages, ChatOptions options, List<ToolDefinition> tools) {
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }
}
