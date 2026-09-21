package com.aicode.core.infrastructure.springai;

import com.aicode.core.domain.model.ChatMessage;
import com.aicode.core.domain.model.ChatResult;
import com.aicode.core.domain.model.FinishReason;
import com.aicode.core.domain.model.MessageRole;
import com.aicode.core.domain.model.ToolCall;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spring AI 消息/响应映射测试：领域四类消息与 Spring AI 消息互转，响应映射为 {@link ChatResult}。
 */
class SpringAiMessageMapperTest {

    private final SpringAiMessageMapper mapper = new SpringAiMessageMapper();

    @Test
    void mapsDomainMessagesToSpringAiMessages() {
        List<Message> messages = mapper.toSpringMessages(List.of(
                new ChatMessage(MessageRole.SYSTEM, "系统提示"),
                new ChatMessage(MessageRole.USER, "你好"),
                ChatMessage.assistant(List.of(new ToolCall("call_1", "PatientLookupTool", "{\"patientId\":\"P1\"}"))),
                ChatMessage.tool("call_1", "{\"name\":\"张三\"}")
        ));

        assertThat(messages).hasSize(4);
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(messages.get(0).getText()).isEqualTo("系统提示");
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(1).getText()).isEqualTo("你好");

        AssistantMessage assistant = (AssistantMessage) messages.get(2);
        assertThat(assistant.getToolCalls()).hasSize(1);
        assertThat(assistant.getToolCalls().get(0).id()).isEqualTo("call_1");
        assertThat(assistant.getToolCalls().get(0).type()).isEqualTo("function");
        assertThat(assistant.getToolCalls().get(0).name()).isEqualTo("PatientLookupTool");
        assertThat(assistant.getToolCalls().get(0).arguments()).isEqualTo("{\"patientId\":\"P1\"}");

        ToolResponseMessage tool = (ToolResponseMessage) messages.get(3);
        assertThat(tool.getResponses()).hasSize(1);
        assertThat(tool.getResponses().get(0).id()).isEqualTo("call_1");
        assertThat(tool.getResponses().get(0).responseData()).isEqualTo("{\"name\":\"张三\"}");
    }

    @Test
    void mapsToolCallResponseToChatResult() {
        AssistantMessage assistant = new AssistantMessage("", java.util.Map.of(),
                List.of(new AssistantMessage.ToolCall("call_1", "function", "WeatherTool", "{}")));
        ChatResponse response = ChatResponse.builder()
                .generations(List.of(new Generation(assistant,
                        ChatGenerationMetadata.builder().finishReason("tool_calls").build())))
                .metadata(ChatResponseMetadata.builder()
                        .model("deepseek-chat")
                        .usage(new DefaultUsage(10, 5, 15))
                        .build())
                .build();

        ChatResult result = mapper.toChatResult(response);

        assertThat(result.finishReason()).isEqualTo(FinishReason.TOOL_CALLS);
        assertThat(result.hasToolCalls()).isTrue();
        assertThat(result.toolCalls()).hasSize(1);
        assertThat(result.toolCalls().get(0).id()).isEqualTo("call_1");
        assertThat(result.toolCalls().get(0).name()).isEqualTo("WeatherTool");
        assertThat(result.usage().promptTokens()).isEqualTo(10);
        assertThat(result.usage().completionTokens()).isEqualTo(5);
        assertThat(result.usage().totalTokens()).isEqualTo(15);
        assertThat(result.model()).isEqualTo("deepseek-chat");
    }

    @Test
    void mapsPlainAnswerToStopResultWithUnknownUsageWhenMissing() {
        ChatResponse response = ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage("最终答案"),
                        ChatGenerationMetadata.builder().finishReason("stop").build())))
                .metadata(ChatResponseMetadata.builder().model("deepseek-chat").build())
                .build();

        ChatResult result = mapper.toChatResult(response);

        assertThat(result.content()).isEqualTo("最终答案");
        assertThat(result.toolCalls()).isEmpty();
        assertThat(result.finishReason()).isEqualTo(FinishReason.STOP);
        assertThat(result.usage().totalTokens()).isZero();
    }

    @Test
    void rejectsEmptyResponse() {
        assertThatThrownBy(() -> mapper.toChatResult(null))
                .isInstanceOf(com.aicode.core.domain.exception.ChatModelException.class);
    }
}
