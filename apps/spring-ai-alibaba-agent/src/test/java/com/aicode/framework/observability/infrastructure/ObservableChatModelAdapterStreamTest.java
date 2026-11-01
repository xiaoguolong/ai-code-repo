package com.aicode.framework.observability.infrastructure;

import com.aicode.core.domain.model.ChatMessage;
import com.aicode.core.domain.model.ChatOptions;
import com.aicode.core.domain.model.ChatResult;
import com.aicode.core.domain.model.ChatStreamListener;
import com.aicode.core.domain.model.FinishReason;
import com.aicode.core.domain.model.MessageRole;
import com.aicode.core.domain.model.TokenUsage;
import com.aicode.core.domain.model.ToolDefinition;
import com.aicode.core.domain.port.ChatModelPort;
import com.aicode.framework.observability.domain.AgentObservabilityPort;
import com.aicode.framework.observability.domain.MonitoringMetricNames;
import com.aicode.framework.observability.domain.SpanScope;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 观测装饰器的增量透传（Week 31 真机回归）。
 *
 * <p>曾因未覆盖 {@code chatStream(...)}，落到接口默认实现 → 同步调用 + 单 chunk，
 * 云端表现为"首字=总耗时"。这里锁死：增量必须透传、结果必须返回、token 必须记。</p>
 */
class ObservableChatModelAdapterStreamTest {

    @Test
    void passesDeltasThroughWhenObservabilityDisabled() {
        AgentObservabilityPort observability = mock(AgentObservabilityPort.class);
        when(observability.isEnabled()).thenReturn(false);
        StubPort delegate = new StubPort();
        ObservableChatModelAdapter adapter = new ObservableChatModelAdapter(delegate, observability);
        List<String> deltas = new ArrayList<>();

        ChatResult result = adapter.chatStream(messages(), options(), deltas::add);

        assertThat(deltas).containsExactly("报", "告");
        assertThat(result.content()).isEqualTo("报告");
        assertThat(delegate.streamCalls).isEqualTo(1);
    }

    @Test
    void passesDeltasThroughAndRecordsTokensWhenEnabled() {
        AgentObservabilityPort observability = mock(AgentObservabilityPort.class);
        when(observability.isEnabled()).thenReturn(true);
        when(observability.openSpan(any(), anyString(), anyMap())).thenReturn(mock(SpanScope.class));
        StubPort delegate = new StubPort();
        ObservableChatModelAdapter adapter = new ObservableChatModelAdapter(delegate, observability);
        List<String> deltas = new ArrayList<>();

        ChatResult result = adapter.chatStream(messages(), options(), deltas::add);

        assertThat(deltas).containsExactly("报", "告");
        assertThat(result.content()).isEqualTo("报告");
        verify(observability).recordTokenUsage(eq("test-model"), any(TokenUsage.class));
        verify(observability).recordCounter(eq(MonitoringMetricNames.LLM_CALL_COUNT), eq(1.0),
                anyString(), anyString(), anyString(), anyString());
    }

    private static List<ChatMessage> messages() {
        return List.of(new ChatMessage(MessageRole.USER, "生成报告"));
    }

    private static ChatOptions options() {
        return new ChatOptions("test-model", 0.2, 64);
    }

    private static final class StubPort implements ChatModelPort {
        private int streamCalls;

        @Override
        public ChatResult chat(List<ChatMessage> messages, ChatOptions options, List<ToolDefinition> tools) {
            throw new AssertionError("streaming path must not call chat()");
        }

        @Override
        public ChatResult chatStream(
                List<ChatMessage> messages,
                ChatOptions options,
                ChatStreamListener listener
        ) {
            streamCalls++;
            listener.onDelta("报");
            listener.onDelta("告");
            return new ChatResult("报告", List.of(), FinishReason.STOP,
                    new TokenUsage(3, 4, 7), options.model());
        }
    }
}
