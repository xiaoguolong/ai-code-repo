package com.aicode.framework.knowledge.infrastructure;

import com.aicode.core.domain.port.ChatModelPort;
import com.aicode.core.domain.port.PromptTemplatePort;
import com.aicode.core.domain.port.ToolPort;
import com.aicode.core.infrastructure.config.LlmProperties;
import com.aicode.framework.knowledge.application.KnowledgeRuntimeConfig;
import com.aicode.framework.knowledge.domain.KnowledgeQaAgent;
import com.aicode.framework.knowledge.domain.port.KnowledgeSearchPort;
import com.aicode.framework.platform.infrastructure.config.PlatformKnowledgeProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 知识库 Agent 装配（Week 25）。
 *
 * <p>工具端口必须用 {@code observableToolPort}（RBAC + Guardrail + 观测链的最外层），
 * 与患者分析/随访一致——用 {@code toolRegistryDelegate} 会静默绕过授权。</p>
 */
@Configuration
public class KnowledgeConfiguration {

    /**
     * 知识库问答运行时参数（复用全局 LLM 配置）。
     *
     * @param llmProperties ai-core 的 LLM 配置
     * @return 运行时参数
     */
    @Bean
    KnowledgeRuntimeConfig knowledgeRuntimeConfig(LlmProperties llmProperties) {
        return new KnowledgeRuntimeConfig(
                llmProperties.model(), llmProperties.temperature(), llmProperties.maxTokens());
    }

    /**
     * 知识库检索适配器（调用 enterprise 内部只读端点）。
     *
     * <p>始终注册：配置缺失或关闭时在调用期显式失败，避免「bean 不存在 → 启动失败」
     * 把整个平台拖下水（知识库是可选能力）。</p>
     *
     * @param properties   知识库配置
     * @param objectMapper JSON
     * @return 检索端口实现
     */
    @Bean
    KnowledgeSearchPort knowledgeSearchPort(
            PlatformKnowledgeProperties properties,
            ObjectMapper objectMapper
    ) {
        return new EnterpriseKnowledgeSearchAdapter(properties, objectMapper);
    }

    /**
     * 知识库问答 Agent。
     *
     * @param chatModelPort       模型端口
     * @param promptTemplatePort  版本化 Prompt
     * @param frameworkToolPort   带 RBAC + Guardrail + 观测的工具端口
     * @param objectMapper        JSON
     * @param runtimeConfig       运行时参数
     * @return Agent 实例
     */
    @Bean
    KnowledgeQaAgent knowledgeQaAgent(
            ChatModelPort chatModelPort,
            PromptTemplatePort promptTemplatePort,
            @Qualifier("observableToolPort") ToolPort frameworkToolPort,
            ObjectMapper objectMapper,
            KnowledgeRuntimeConfig runtimeConfig
    ) {
        return new KnowledgeQaAgent(
                frameworkToolPort, chatModelPort, promptTemplatePort, objectMapper, runtimeConfig);
    }
}
