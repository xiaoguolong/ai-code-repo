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
import com.aicode.framework.multiagent.application.MedicalAssistantRuntimeConfig;
import com.aicode.framework.multiagent.domain.MedicalAssistantSupervisor;
import com.aicode.framework.multiagent.domain.MedicalAssistantSupervisorGraph;
import com.aicode.framework.workflow.application.PatientRiskRuntimeConfig;
import com.aicode.framework.workflow.domain.PatientRiskAssessor;
import com.aicode.framework.workflow.domain.PatientRiskToolResultMapper;
import com.aicode.framework.workflow.domain.PatientRiskWorkflow;
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

    /**
     * 患者风险判断纯领域服务。
     */
    @Bean
    PatientRiskAssessor patientRiskAssessor() {
        return new PatientRiskAssessor();
    }

    /**
     * 工具结果 JSON 解析器（领域工具与值对象之间的映射）。
     */
    @Bean
    PatientRiskToolResultMapper patientRiskToolResultMapper(ObjectMapper objectMapper) {
        return new PatientRiskToolResultMapper(objectMapper);
    }

    /**
     * 把 LLM 配置映射为患者风险分析 Workflow 所需的运行时参数。
     */
    @Bean
    PatientRiskRuntimeConfig patientRiskRuntimeConfig(LlmProperties llmProperties) {
        return new PatientRiskRuntimeConfig(
                llmProperties.model(), llmProperties.temperature(), llmProperties.maxTokens());
    }

    /**
     * 患者风险分析 Workflow（领域服务）：固定多节点图 + 条件边，复用同一 ToolPort 与模型端口。
     */
    @Bean
    PatientRiskWorkflow patientRiskWorkflow(
            ChatModelPort chatModelPort,
            PromptTemplatePort promptTemplatePort,
            ToolPort frameworkToolPort,
            PatientRiskAssessor patientRiskAssessor,
            PatientRiskToolResultMapper patientRiskToolResultMapper,
            PatientRiskRuntimeConfig patientRiskRuntimeConfig
    ) {
        return new PatientRiskWorkflow(
                chatModelPort, promptTemplatePort, frameworkToolPort,
                patientRiskAssessor, patientRiskToolResultMapper, patientRiskRuntimeConfig);
    }

    /**
     * Supervisor 路由规划（规则驱动）。
     */
    @Bean
    MedicalAssistantSupervisor medicalAssistantSupervisor() {
        return new MedicalAssistantSupervisor();
    }

    /**
     * 医疗助手多 Agent 运行时参数。
     */
    @Bean
    MedicalAssistantRuntimeConfig medicalAssistantRuntimeConfig(
            LlmProperties llmProperties,
            FrameworkAgentProperties frameworkAgentProperties
    ) {
        return new MedicalAssistantRuntimeConfig(
                llmProperties.model(),
                llmProperties.temperature(),
                llmProperties.maxTokens(),
                frameworkAgentProperties.resolvedMaxIterations() + 5);
    }

    /**
     * 医疗助手 Supervisor 多 Agent Graph。
     */
    @Bean
    MedicalAssistantSupervisorGraph medicalAssistantSupervisorGraph(
            ChatModelPort chatModelPort,
            PromptTemplatePort promptTemplatePort,
            ToolPort frameworkToolPort,
            PatientRiskAssessor patientRiskAssessor,
            PatientRiskToolResultMapper patientRiskToolResultMapper,
            MedicalAssistantSupervisor medicalAssistantSupervisor,
            MedicalAssistantRuntimeConfig medicalAssistantRuntimeConfig
    ) {
        return new MedicalAssistantSupervisorGraph(
                chatModelPort, promptTemplatePort, frameworkToolPort,
                patientRiskAssessor, patientRiskToolResultMapper,
                medicalAssistantSupervisor, medicalAssistantRuntimeConfig);
    }
}
