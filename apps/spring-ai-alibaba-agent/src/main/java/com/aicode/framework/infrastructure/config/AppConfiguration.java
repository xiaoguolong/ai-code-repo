package com.aicode.framework.infrastructure.config;

import com.aicode.core.domain.Tool;
import com.aicode.core.domain.ToolRegistry;
import com.aicode.core.domain.port.ChatModelPort;
import com.aicode.core.domain.port.PromptTemplatePort;
import com.aicode.core.domain.port.ToolPort;
import com.aicode.core.infrastructure.config.LlmProperties;
import com.aicode.core.infrastructure.springai.SpringAiToolCallbackFactory;
import com.aicode.framework.application.FrameworkRuntimeConfig;
import com.aicode.framework.domain.FrameworkAgentGraph;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Graph Agent 装配：运行时配置、工具注册表、图领域服务与 MCP 工具提供者。
 * 模型适配器（{@code ChatModelPort}）由 ai-core 依据 {@code framework.model-provider} 装配。
 */
@Configuration
@EnableConfigurationProperties({FrameworkAgentProperties.class})
public class AppConfiguration {

    /**
     * 把 LLM + Agent 配置映射为用图领域服务所需的运行时参数。
     */
    @Bean
    FrameworkRuntimeConfig frameworkRuntimeConfig(
            LlmProperties llmProperties,
            FrameworkAgentProperties frameworkAgentProperties
    ) {
        return new FrameworkRuntimeConfig(
                llmProperties.model(),
                llmProperties.temperature(),
                llmProperties.maxTokens(),
                frameworkAgentProperties.resolvedMaxIterations());
    }

    /**
     * 工具注册表（ToolPort 通用实现）。Graph 与 MCP 共用同一批领域工具。
     */
    @Bean
    ToolPort frameworkToolPort(List<Tool> tools, ObjectMapper objectMapper) {
        return new ToolRegistry(tools, objectMapper);
    }

    /**
     * Spring AI Alibaba Graph 编排的 Agent 领域服务。
     */
    @Bean
    FrameworkAgentGraph frameworkAgentGraph(
            ChatModelPort chatModelPort,
            PromptTemplatePort promptTemplatePort,
            ToolPort frameworkToolPort,
            FrameworkRuntimeConfig frameworkRuntimeConfig
    ) {
        return new FrameworkAgentGraph(chatModelPort, promptTemplatePort, frameworkToolPort, frameworkRuntimeConfig);
    }

    /**
     * MCP 工具提供者：把领域工具包成可执行回调，供 MCP Server 自动暴露。
     */
    @Bean
    ToolCallbackProvider mcpToolCallbackProvider(List<Tool> tools, ObjectMapper objectMapper) {
        return ToolCallbackProvider.from(
                new SpringAiToolCallbackFactory(objectMapper).toExecutableCallbacks(tools));
    }
}
