package com.aicode.demo.infrastructure.llm;

import com.aicode.demo.domain.model.ChatResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiChatResponseMapperTest {

    private final OpenAiChatResponseMapper mapper = new OpenAiChatResponseMapper();

    @Test
    void shouldMapContentAndUsage() {
        String json = """
                {
                  "model": "deepseek-chat",
                  "choices": [{"message": {"role": "assistant", "content": "pong"}}],
                  "usage": {"prompt_tokens": 8, "completion_tokens": 2, "total_tokens": 10}
                }
                """;

        ChatResult result = mapper.map(json);

        assertThat(result.content()).isEqualTo("pong");
        assertThat(result.model()).isEqualTo("deepseek-chat");
        assertThat(result.usage().promptTokens()).isEqualTo(8);
        assertThat(result.usage().completionTokens()).isEqualTo(2);
        assertThat(result.usage().totalTokens()).isEqualTo(10);
    }

    @Test
    void shouldUseUnknownUsage_whenUsageMissing() {
        String json = """
                {
                  "model": "deepseek-chat",
                  "choices": [{"message": {"content": "ok"}}]
                }
                """;

        ChatResult result = mapper.map(json);

        assertThat(result.usage().totalTokens()).isZero();
    }

    @Test
    void shouldFail_whenChoicesEmpty() {
        assertThatThrownBy(() -> mapper.map("{\"choices\":[]}"))
                .isInstanceOf(IllegalStateException.class);
    }
}
