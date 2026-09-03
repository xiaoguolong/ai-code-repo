package com.aicode.demo.infrastructure.prompt;

import com.aicode.demo.domain.model.PromptDescriptor;
import com.aicode.demo.domain.model.PromptTemplate;
import com.aicode.demo.infrastructure.config.ChatAppProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClasspathPromptTemplateAdapterTest {

    private final ChatAppProperties properties = new ChatAppProperties("v1", "memory", 20, "postgre_sql");
    private final ClasspathPromptTemplateAdapter adapter = new ClasspathPromptTemplateAdapter(properties);

    @Test
    void shouldLoadSystemPrompt() {
        PromptTemplate prompt = adapter.loadSystemPrompt();

        assertThat(prompt.version()).isEqualTo("v1");
        assertThat(prompt.content()).contains("helpful assistant");
    }

    @Test
    void shouldLoadJsonPrompt() {
        PromptTemplate prompt = adapter.load("json");

        assertThat(prompt.version()).isEqualTo("v1");
        assertThat(prompt.content()).contains("JSON");
    }

    @Test
    void shouldLoadTranslatorPrompt() {
        PromptTemplate prompt = adapter.load("translator");

        assertThat(prompt.version()).isEqualTo("v1");
        assertThat(prompt.content()).contains("translator");
    }

    @Test
    void shouldLoadCoderPrompt() {
        PromptTemplate prompt = adapter.load("coder");

        assertThat(prompt.version()).isEqualTo("v1");
        assertThat(prompt.content()).contains("Java");
    }

    @Test
    void shouldLoadQaPrompt() {
        PromptTemplate prompt = adapter.load("qa");

        assertThat(prompt.version()).isEqualTo("v1");
        assertThat(prompt.content()).contains("知识库");
    }

    @Test
    void shouldListAvailablePrompts() {
        List<PromptDescriptor> descriptors = adapter.list();

        assertThat(descriptors).extracting(PromptDescriptor::name)
                .contains("system", "json", "translator", "coder", "qa");
    }

    @Test
    void shouldRejectInvalidName() {
        assertThatThrownBy(() -> adapter.load("../etc/passwd"))
                .isInstanceOf(com.aicode.demo.domain.exception.InvalidChatRequestException.class);
    }
}
