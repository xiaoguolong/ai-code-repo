package com.aicode.core.infrastructure.springai;

import com.aicode.core.domain.port.ChatModelPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 条件装配测试：仅当 {@code framework.model-provider=spring-ai} 且存在 Spring AI ChatModel 时才注册适配器 Bean。
 */
class SpringAiAdapterConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class)
            .withBean(ChatModel.class, StubChatModel::new)
            .withUserConfiguration(SpringAiAdapterConfiguration.class);

    @Test
    void registersAdapterWhenProviderIsSpringAi() {
        runner.withPropertyValues("framework.model-provider=spring-ai")
                .run(context -> assertThat(context).hasSingleBean(ChatModelPort.class));
    }

    @Test
    void skipsAdapterWhenProviderIsNotSpringAi() {
        runner.run(context -> assertThat(context).doesNotHaveBean(ChatModelPort.class));
    }

    private static final class StubChatModel implements ChatModel {
        @Override
        public ChatResponse call(Prompt prompt) {
            return ChatResponse.builder()
                    .generations(List.of(new Generation(new AssistantMessage("ok"),
                            ChatGenerationMetadata.builder().finishReason("stop").build())))
                    .build();
        }
    }
}
