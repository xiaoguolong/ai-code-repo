package com.aicode.patient.infrastructure.config;

import com.aicode.core.domain.Tool;
import com.aicode.core.domain.ToolRegistry;
import com.aicode.core.domain.port.ChatModelPort;
import com.aicode.core.domain.port.PromptTemplatePort;
import com.aicode.core.domain.port.ToolPort;
import com.aicode.core.infrastructure.config.LlmProperties;
import com.aicode.patient.application.AgentRuntimeConfig;
import com.aicode.patient.domain.ReActAgent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 患者 Agent 装配：运行时配置、工具注册表与领域服务。
 * 模型/Prompt/HTTP 客户端等横切装配已收敛到 ai-core 的 {@code AiCoreConfiguration}。
 */
@Configuration
@EnableConfigurationProperties({AgentProperties.class})
public class AppConfiguration {

    /**
     * 把 LLM + Agent 配置映射为用例/领域所需的运行时参数。
     */
    @Bean
    AgentRuntimeConfig agentRuntimeConfig(LlmProperties llmProperties, AgentProperties agentProperties) {
        return new AgentRuntimeConfig(
                llmProperties.model(),
                llmProperties.temperature(),
                llmProperties.maxTokens(),
                agentProperties.maxIterations()
        );
    }

    /**
     * 工具注册表（ToolPort 通用实现）。持所有 {@link Tool}，按 name 分发，是工具执行的唯一咽喉点。
     */
    @Bean
    ToolPort toolPort(List<Tool> tools, ObjectMapper objectMapper) {
        return new ToolRegistry(tools, objectMapper);
    }

    /**
     * Function Calling 编排（领域服务）。依赖模型端口、Prompt 端口与工具端口。
     */
    @Bean
    ReActAgent reActAgent(
            ChatModelPort chatModelPort,
            PromptTemplatePort promptTemplatePort,
            ToolPort toolPort,
            AgentRuntimeConfig config
    ) {
        return new ReActAgent(chatModelPort, promptTemplatePort, toolPort, config);
    }
}
