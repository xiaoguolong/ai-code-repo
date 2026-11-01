package com.aicode.core.infrastructure.springai;

import com.aicode.core.domain.exception.ChatModelException;
import com.aicode.core.domain.exception.StreamCancelledException;
import com.aicode.core.domain.model.ChatMessage;
import com.aicode.core.domain.model.ChatOptions;
import com.aicode.core.domain.model.ChatResult;
import com.aicode.core.domain.model.ChatStreamListener;
import com.aicode.core.domain.model.FinishReason;
import com.aicode.core.domain.model.MessageRole;
import com.aicode.core.domain.model.TokenUsage;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spring AI 增量适配测试（Week 31）：顺序、累加、末包 usage、取消退订、异常映射、空流。
 */
class SpringAiChatModelAdapterStreamTest {

    private final SpringAiToolCallbackFactory factory =
            new SpringAiToolCallbackFactory(new com.fasterxml.jackson.databind.ObjectMapper());

    @Test
    void forwardsDeltasInOrderAndReturnsAccumulatedResult() {
        RecordingChatModel chatModel = new RecordingChatModel(List.of(
                chunk("你", null),
                chunk("好", null),
                chunk("", "stop", 5, 7, 12)));
        SpringAiChatModelAdapter adapter = adapter(chatModel);
        CollectingListener listener = new CollectingListener();

        ChatResult result = adapter.chatStream(
                List.of(new ChatMessage(MessageRole.USER, "你好")),
                new ChatOptions("deepseek-chat", 0.2, 128),
                listener);

        assertThat(listener.deltas).containsExactly("你", "好");
        assertThat(result.content()).isEqualTo("你好");
        assertThat(result.usage()).isEqualTo(new TokenUsage(5, 7, 12));
        assertThat(result.model()).isEqualTo("deepseek-chat");
        assertThat(result.finishReason()).isEqualTo(FinishReason.STOP);

        ToolCallingChatOptions options = (ToolCallingChatOptions) chatModel.prompt.getOptions();
        assertThat(options.getModel()).isEqualTo("deepseek-chat");
        assertThat(options.getTemperature()).isEqualTo(0.2);
        assertThat(options.getMaxTokens()).isEqualTo(128);
        assertThat(options.getInternalToolExecutionEnabled()).isFalse();
        assertThat(options.getToolCallbacks()).isEmpty();
    }

    @Test
    void keepsLastNonEmptyUsageWhenLaterChunksHaveNone() {
        RecordingChatModel chatModel = new RecordingChatModel(List.of(
                chunk("a", null, 3, 4, 7),
                chunk("b", null)));
        SpringAiChatModelAdapter adapter = adapter(chatModel);
        CollectingListener listener = new CollectingListener();

        ChatResult result = adapter.chatStream(messages(), options(), listener);

        assertThat(result.usage()).isEqualTo(new TokenUsage(3, 4, 7));
        assertThat(result.finishReason()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void stopsUpstreamWhenListenerCancels() {
        RecordingChatModel chatModel = new RecordingChatModel(List.of(
                chunk("a", null), chunk("b", null), chunk("c", null)));
        SpringAiChatModelAdapter adapter = adapter(chatModel);
        CancellingListener listener = new CancellingListener();

        assertThatThrownBy(() -> adapter.chatStream(messages(), options(), listener))
                .isInstanceOf(StreamCancelledException.class);

        assertThat(listener.deltas).containsExactly("a");
        assertThat(chatModel.streamCalls).isEqualTo(1);
    }

    @Test
    void wrapsUpstreamFailureIntoChatModelException() {
        RecordingChatModel chatModel = new RecordingChatModel(new IllegalStateException("boom"));
        SpringAiChatModelAdapter adapter = adapter(chatModel);

        assertThatThrownBy(() -> adapter.chatStream(messages(), options(), new CollectingListener()))
                .isInstanceOf(ChatModelException.class)
                .hasMessageContaining("spring ai stream failed");
    }

    @Test
    void emptyStreamReturnsEmptyContentAndUnknownUsage() {
        RecordingChatModel chatModel = new RecordingChatModel(List.of());
        SpringAiChatModelAdapter adapter = adapter(chatModel);
        CollectingListener listener = new CollectingListener();

        ChatResult result = adapter.chatStream(messages(), options(), listener);

        assertThat(listener.deltas).isEmpty();
        assertThat(result.content()).isEmpty();
        assertThat(result.usage()).isEqualTo(TokenUsage.unknown());
    }

    @Test
    void usageOnlyChunkDoesNotEmitEmptyDelta() {
        RecordingChatModel chatModel = new RecordingChatModel(List.of(
                chunk("答案", null),
                chunk("", "length", 1, 1, 2)));
        SpringAiChatModelAdapter adapter = adapter(chatModel);
        CollectingListener listener = new CollectingListener();

        ChatResult result = adapter.chatStream(messages(), options(), listener);

        assertThat(listener.deltas).containsExactly("答案");
        assertThat(result.finishReason()).isEqualTo(FinishReason.LENGTH);
    }

    private SpringAiChatModelAdapter adapter(ChatModel chatModel) {
        return new SpringAiChatModelAdapter(chatModel, factory, new SpringAiMessageMapper());
    }

    private static List<ChatMessage> messages() {
        return List.of(new ChatMessage(MessageRole.USER, "你好"));
    }

    private static ChatOptions options() {
        return new ChatOptions("deepseek-chat", 0.7, 64);
    }

    private static ChatResponse chunk(String text, String finishReason) {
        return chunk(text, finishReason, null, null, null);
    }

    private static ChatResponse chunk(String text, String finishReason, Integer prompt, Integer completion, Integer total) {
        ChatResponseMetadata.Builder metadata = ChatResponseMetadata.builder().model("deepseek-chat");
        if (prompt != null || completion != null) {
            metadata.usage(new DefaultUsage(prompt, completion, total));
        }
        ChatGenerationMetadata.Builder generation = ChatGenerationMetadata.builder();
        if (finishReason != null) {
            generation.finishReason(finishReason);
        }
        return ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(text), generation.build())))
                .metadata(metadata.build())
                .build();
    }

    /** 可编程的流式 ChatModel：按给定块发射，或直接失败。 */
    private static final class RecordingChatModel implements ChatModel {
        private final List<ChatResponse> chunks;
        private final RuntimeException failure;
        private Prompt prompt;
        private int streamCalls;

        private RecordingChatModel(List<ChatResponse> chunks) {
            this.chunks = chunks;
            this.failure = null;
        }

        private RecordingChatModel(RuntimeException failure) {
            this.chunks = List.of();
            this.failure = failure;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            throw new UnsupportedOperationException("stream-only stub");
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            this.prompt = prompt;
            this.streamCalls++;
            return failure == null ? Flux.fromIterable(chunks) : Flux.error(failure);
        }
    }

    private static class CollectingListener implements ChatStreamListener {
        private final List<String> deltas = new ArrayList<>();

        @Override
        public void onDelta(String delta) {
            deltas.add(delta);
        }
    }

    private static final class CancellingListener implements ChatStreamListener {
        private final List<String> deltas = new ArrayList<>();
        private boolean cancelled;

        @Override
        public void onDelta(String delta) {
            deltas.add(delta);
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
