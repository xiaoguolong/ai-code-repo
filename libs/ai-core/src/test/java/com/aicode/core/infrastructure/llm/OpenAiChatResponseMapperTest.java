package com.aicode.core.infrastructure.llm;

import com.aicode.core.domain.model.ChatResult;
import com.aicode.core.domain.model.FinishReason;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OpenAI 兼容 Chat 响应映射器单元测试。
 */
class OpenAiChatResponseMapperTest {

    private final OpenAiChatResponseMapper mapper = new OpenAiChatResponseMapper();

    @Test
    void mapsContentModelAndUsage() {
        String json = """
                {
                  "model": "deepseek-chat",
                  "choices": [{"message": {"content": "你好"}}],
                  "usage": {"prompt_tokens": 10, "completion_tokens": 20, "total_tokens": 30}
                }
                """;

        ChatResult result = mapper.map(json);

        assertThat(result.content()).isEqualTo("你好");
        assertThat(result.model()).isEqualTo("deepseek-chat");
        assertThat(result.usage().promptTokens()).isEqualTo(10);
        assertThat(result.usage().completionTokens()).isEqualTo(20);
        assertThat(result.usage().totalTokens()).isEqualTo(30);
    }

    @Test
    void mapsMissingUsageAsZero() {
        String json = """
                {"choices": [{"message": {"content": "无用量"}}]}
                """;

        ChatResult result = mapper.map(json);

        assertThat(result.content()).isEqualTo("无用量");
        assertThat(result.usage().promptTokens()).isZero();
        assertThat(result.usage().totalTokens()).isZero();
    }

    @Test
    void fallsBackToReasoningContentWhenContentEmpty() {
        String json = """
                {
                  "model": "deepseek-reasoner",
                  "choices": [{"message": {"content": "", "reasoning_content": "思考过程..."}}],
                  "usage": {"prompt_tokens": 10, "completion_tokens": 20, "total_tokens": 30}
                }
                """;

        ChatResult result = mapper.map(json);

        assertThat(result.content()).isEqualTo("思考过程...");
    }

    @Test
    void throwsWhenNoChoices() {
        assertThatThrownBy(() -> mapper.map("{\"choices\": []}"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void parsesToolCallsAndFinishReason() {
        String json = """
                {
                  "model": "deepseek-chat",
                  "choices": [{
                    "finish_reason": "tool_calls",
                    "message": {
                      "content": null,
                      "tool_calls": [
                        {"id": "call-1", "type": "function", "function": {"name": "PatientTool", "arguments": "{\\"patientId\\":\\"P001\\"}"}}
                      ]
                    }
                  }],
                  "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}
                }
                """;

        ChatResult result = mapper.map(json);

        assertThat(result.hasToolCalls()).isTrue();
        assertThat(result.finishReason()).isEqualTo(FinishReason.TOOL_CALLS);
        assertThat(result.toolCalls()).hasSize(1);
        assertThat(result.toolCalls().get(0).id()).isEqualTo("call-1");
        assertThat(result.toolCalls().get(0).name()).isEqualTo("PatientTool");
        assertThat(result.toolCalls().get(0).arguments()).contains("P001");
    }

    @Test
    void mapsStopAsFinishReason() {
        String json = """
                {"choices": [{"finish_reason": "stop", "message": {"content": "最终答案"}}]}
                """;

        ChatResult result = mapper.map(json);

        assertThat(result.finishReason()).isEqualTo(FinishReason.STOP);
        assertThat(result.hasToolCalls()).isFalse();
        assertThat(result.content()).isEqualTo("最终答案");
    }
}
