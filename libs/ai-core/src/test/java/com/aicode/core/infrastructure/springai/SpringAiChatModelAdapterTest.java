package com.aicode.core.infrastructure.springai;

import com.aicode.core.domain.exception.ChatModelException;
import com.aicode.core.domain.model.ChatMessage;
import com.aicode.core.domain.model.ChatOptions;
import com.aicode.core.domain.model.ChatResult;
import com.aicode.core.domain.model.MessageRole;
import com.aicode.core.domain.model.ToolDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spring AI 模型适配器测试：领域入参组装为 Spring AI Prompt（携带工具、关闭内部执行），响应映射回 {@link ChatResult}。
 */
class SpringAiChatModelAdapterTest {

    private final SpringAiToolCallbackFactory factory = new SpringAiToolCallbackFactory(new ObjectMapper());

    @Test
    void sendsMessagesToolsAndOptionsAndMapsAnswer() {
        CapturingChatModel chatModel = new CapturingChatModel(plainResponse("你好，我是助手"));
        SpringAiChatModelAdapter adapter = new SpringAiChatModelAdapter(chatModel, factory, new SpringAiMessageMapper());

        ChatResult result = adapter.chat(
                List.of(new ChatMessage(MessageRole.USER, "你好")),
                new ChatOptions("deepseek-chat", 0.2, 128),
                List.of(new ToolDefinition("WeatherTool", "查询天气", Map.of("type", "object"))));

        assertThat(result.content()).isEqualTo("你好，我是助手");
        assertThat(chatModel.prompt.getInstructions()).hasSize(1);
        ToolCallingChatOptions options = (ToolCallingChatOptions) chatModel.prompt.getOptions();
        assertThat(options.getModel()).isEqualTo("deepseek-chat");
        assertThat(options.getTemperature()).isEqualTo(0.2);
        assertThat(options.getMaxTokens()).isEqualTo(128);
        assertThat(options.getInternalToolExecutionEnabled()).isFalse();
        assertThat(options.getToolCallbacks()).hasSize(1);
    }

    @Test
    void wrapsUnderlyingFailureIntoChatModelException() {
        ChatModel failing = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                throw new IllegalStateException("boom");
            }
        };
        SpringAiChatModelAdapter adapter = new SpringAiChatModelAdapter(failing, factory, new SpringAiMessageMapper());

        assertThatThrownBy(() -> adapter.chat(
                List.of(new ChatMessage(MessageRole.USER, "你好")),
                new ChatOptions("deepseek-chat", 0.2, 128),
                List.of()))
                .isInstanceOf(ChatModelException.class)
                .hasMessageContaining("spring ai chat failed");
    }

    private static ChatResponse plainResponse(String content) {
        return ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(content),
                        ChatGenerationMetadata.builder().finishReason("stop").build())))
                .metadata(ChatResponseMetadata.builder()
                        .model("deepseek-chat")
                        .usage(new DefaultUsage(1, 2, 3))
                        .build())
                .build();
    }

    private static final class CapturingChatModel implements ChatModel {
        private final ChatResponse response;
        private Prompt prompt;

        private CapturingChatModel(ChatResponse response) {
            this.response = response;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            this.prompt = prompt;
            return response;
        }
    }
}
