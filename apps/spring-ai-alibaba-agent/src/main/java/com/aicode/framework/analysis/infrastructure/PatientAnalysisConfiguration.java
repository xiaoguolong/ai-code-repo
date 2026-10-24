package com.aicode.framework.analysis.infrastructure;

import com.aicode.core.domain.port.ChatModelPort;
import com.aicode.core.domain.port.PromptTemplatePort;
import com.aicode.core.domain.port.ToolPort;
import com.aicode.core.infrastructure.config.LlmProperties;
import com.aicode.framework.analysis.application.PatientAnalysisRuntimeConfig;
import com.aicode.framework.analysis.domain.PatientAnalysisAgent;
import com.aicode.framework.workflow.domain.PatientRiskAssessor;
import com.aicode.framework.workflow.domain.PatientRiskToolResultMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 患者分析 Agent 装配（Week 21）。
 *
 * <p>工具端口必须用 {@code observableToolPort}：它是
 * {@code ObservableToolPort → AuthorizingToolPort → GuardrailToolPort → ToolRegistry} 链的最外层，
 * 用错 bean 会静默绕过 RBAC / Guardrail（第 17 周起容器里有三个 {@code ToolPort}）。</p>
 */
@Configuration
public class PatientAnalysisConfiguration {

    /**
     * 患者分析运行时参数（沿用全局 LLM 配置，不新增密钥/模型）。
     *
     * @param llmProperties ai-core 的 LLM 配置
     * @return 运行时参数
     */
    @Bean
    PatientAnalysisRuntimeConfig patientAnalysisRuntimeConfig(LlmProperties llmProperties) {
        return new PatientAnalysisRuntimeConfig(
                llmProperties.model(), llmProperties.temperature(), llmProperties.maxTokens());
    }

    /**
     * 患者分析 Agent 领域服务。
     *
     * @param chatModelPort          模型端口（{@code @Primary} 为带观测的版本）
     * @param promptTemplatePort     版本化 Prompt
     * @param frameworkToolPort      带 RBAC + Guardrail + 观测的工具端口
     * @param riskAssessor           规则风险评估（Week 9）
     * @param toolResultMapper       工具结果 JSON 解析
     * @param objectMapper           JSON 解析
     * @param runtimeConfig          运行时参数
     * @return Agent 实例
     */
    @Bean
    PatientAnalysisAgent patientAnalysisAgent(
            ChatModelPort chatModelPort,
            PromptTemplatePort promptTemplatePort,
            @Qualifier("observableToolPort") ToolPort frameworkToolPort,
            PatientRiskAssessor riskAssessor,
            PatientRiskToolResultMapper toolResultMapper,
            ObjectMapper objectMapper,
            PatientAnalysisRuntimeConfig runtimeConfig
    ) {
        return new PatientAnalysisAgent(
                frameworkToolPort, chatModelPort, promptTemplatePort,
                riskAssessor, toolResultMapper, objectMapper, runtimeConfig);
    }
}
