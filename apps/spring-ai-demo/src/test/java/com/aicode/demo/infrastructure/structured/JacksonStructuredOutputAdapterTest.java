package com.aicode.demo.infrastructure.structured;

import com.aicode.demo.domain.exception.StructuredOutputException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JacksonStructuredOutputAdapterTest {

    private final JacksonStructuredOutputAdapter adapter = new JacksonStructuredOutputAdapter(new ObjectMapper());

    @Test
    void shouldParsePlainJsonObject() {
        Map<String, Object> result = adapter.parseObject("{\"answer\":\"42\"}");

        assertThat(result).containsEntry("answer", "42");
    }

    @Test
    void shouldStripMarkdownFences() {
        Map<String, Object> result = adapter.parseObject("```json\n{\"answer\":42}\n```");

        assertThat(result).containsEntry("answer", 42);
    }

    @Test
    void shouldRejectPlainJsonArray() {
        assertThatThrownBy(() -> adapter.parseObject("[{\"a\":1}]"))
                .isInstanceOf(StructuredOutputException.class);
    }

    @Test
    void shouldRejectInvalidJson() {
        assertThatThrownBy(() -> adapter.parseObject("not json"))
                .isInstanceOf(StructuredOutputException.class);
    }

    @Test
    void shouldRejectNull() {
        assertThatThrownBy(() -> adapter.parseObject(null))
                .isInstanceOf(StructuredOutputException.class);
    }
}
