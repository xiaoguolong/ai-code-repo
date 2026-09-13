package com.aicode.patient.infrastructure.llm;

import com.aicode.patient.domain.model.ChatResult;
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
}
