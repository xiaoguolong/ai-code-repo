package com.aicode.core.infrastructure.springai;

import com.aicode.core.domain.port.ChatModelPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 适配装配。仅当类路径存在 Spring AI {@link ChatModel} 且 {@code framework.model-provider=spring-ai}
 * 时生效，避免未引入 Spring AI 的 app 受影响（{@code spring-ai-model} 为 optional 依赖，规范 5.8.1）。
 */
@Configuration
@ConditionalOnClass(ChatModel.class)
@ConditionalOnProperty(prefix = "framework", name = "model-provider", havingValue = "spring-ai")
public class SpringAiAdapterConfiguration {

    /**
     * Spring AI 消息/响应映射器。
     */
    @Bean
    SpringAiMessageMapper springAiMessageMapper() {
        return new SpringAiMessageMapper();
    }

    /**
     * Spring AI 工具回调工厂（依赖 JSON 序列化工具 schema）。
     */
    @Bean
    SpringAiToolCallbackFactory springAiToolCallbackFactory(ObjectMapper objectMapper) {
        return new SpringAiToolCallbackFactory(objectMapper);
    }

    /**
     * 以 Spring AI 为后端的 {@link ChatModelPort} 适配器。
     */
    @Bean
    ChatModelPort springAiChatModelAdapter(
            ChatModel chatModel,
            SpringAiToolCallbackFactory springAiToolCallbackFactory,
            SpringAiMessageMapper springAiMessageMapper
    ) {
        return new SpringAiChatModelAdapter(chatModel, springAiToolCallbackFactory, springAiMessageMapper);
    }
}
