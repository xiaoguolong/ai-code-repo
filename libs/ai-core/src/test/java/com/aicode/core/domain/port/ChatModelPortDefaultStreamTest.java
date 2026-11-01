package com.aicode.core.domain.port;

import com.aicode.core.domain.exception.StreamCancelledException;
import com.aicode.core.domain.model.ChatMessage;
import com.aicode.core.domain.model.ChatOptions;
import com.aicode.core.domain.model.ChatResult;
import com.aicode.core.domain.model.ChatStreamListener;
import com.aicode.core.domain.model.FinishReason;
import com.aicode.core.domain.model.MessageRole;
import com.aicode.core.domain.model.TokenUsage;
import com.aicode.core.domain.model.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 默认流式实现测试（Week 31）：不支持增量的适配器退化为"一个整段 chunk"，契约不破。
 */
class ChatModelPortDefaultStreamTest {

    @Test
    void degradesToSingleChunkAndReturnsResult() {
        StubChatModel model = new StubChatModel("整段答案");
        CollectingListener listener = new CollectingListener();

        ChatResult result = model.chatStream(messages(), options(), listener);

        assertThat(listener.deltas).containsExactly("整段答案");
        assertThat(result.content()).isEqualTo("整段答案");
        assertThat(result.usage()).isEqualTo(new TokenUsage(1, 2, 3));
        assertThat(model.calls.get()).isEqualTo(1);
    }

    @Test
    void doesNotEmitEmptyDeltaForBlankAnswer() {
        StubChatModel model = new StubChatModel("  ");
        CollectingListener listener = new CollectingListener();

        ChatResult result = model.chatStream(messages(), options(), listener);

        assertThat(listener.deltas).isEmpty();
        assertThat(result).isNotNull();
    }

    @Test
    void rejectsAlreadyCancelledListenerWithoutCallingModel() {
        StubChatModel model = new StubChatModel("不应该被调用");
        CollectingListener listener = new CollectingListener();
        listener.cancelled = true;

        assertThatThrownBy(() -> model.chatStream(messages(), options(), listener))
                .isInstanceOf(StreamCancelledException.class);

        assertThat(model.calls.get()).isZero();
        assertThat(listener.deltas).isEmpty();
    }

    private static List<ChatMessage> messages() {
        return List.of(new ChatMessage(MessageRole.USER, "问"));
    }

    private static ChatOptions options() {
        return new ChatOptions("deepseek-chat", 0.7, 32);
    }

    private static final class StubChatModel implements ChatModelPort {
        private final String content;
        private final AtomicInteger calls = new AtomicInteger();

        private StubChatModel(String content) {
            this.content = content;
        }

        @Override
        public ChatResult chat(List<ChatMessage> messages, ChatOptions options, List<ToolDefinition> tools) {
            calls.incrementAndGet();
            return new ChatResult(content, List.of(), FinishReason.STOP, new TokenUsage(1, 2, 3), options.model());
        }
    }

    private static final class CollectingListener implements ChatStreamListener {
        private final List<String> deltas = new ArrayList<>();
        private boolean cancelled;

        @Override
        public void onDelta(String delta) {
            deltas.add(delta);
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
