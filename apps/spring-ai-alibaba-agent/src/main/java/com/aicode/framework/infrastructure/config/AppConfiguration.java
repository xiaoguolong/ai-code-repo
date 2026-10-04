package com.aicode.framework.infrastructure.config;

import com.aicode.core.domain.Tool;
import com.aicode.core.domain.ToolRegistry;
import com.aicode.core.domain.port.ChatModelPort;
import com.aicode.core.domain.port.GuardrailPort;
import com.aicode.core.domain.port.PromptTemplatePort;
import com.aicode.core.domain.port.ToolPort;
import com.aicode.core.infrastructure.security.GuardrailToolPort;
import com.aicode.framework.observability.domain.AgentObservabilityPort;
import com.aicode.framework.observability.infrastructure.ObservableChatModelAdapter;
import com.aicode.framework.observability.infrastructure.ObservableToolPort;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import com.aicode.framework.platform.infrastructure.config.PlatformSecurityProperties;
import com.aicode.framework.platform.infrastructure.security.AuthorizingToolPort;
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
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Qualifier;

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
     * 工具注册表委托（ai-core ToolRegistry，不做权限校验）。
     */
    @Bean
    ToolPort toolRegistryDelegate(List<Tool> tools, ObjectMapper objectMapper) {
        return new ToolRegistry(tools, objectMapper);
    }

    /**
     * 带 RBAC + Guardrail 的工具端口。链：AuthorizingToolPort → GuardrailToolPort → ToolRegistry。
     *
     * <p>Week 17 起容器中存在三个 {@link ToolPort} bean（委托 / RBAC 链 / 埋点装饰器），
     * 因此所有装配点都用 {@link Qualifier} 显式指名，避免依赖 {@code @Primary} 的隐式解析
     * （多个 {@code @Primary} 会让 Spring 直接报 NoUniqueBeanDefinitionException）。</p>
     */
    @Bean
    ToolPort frameworkToolPort(
            @Qualifier("toolRegistryDelegate") ToolPort toolRegistryDelegate,
            GuardrailPort guardrailPort,
            PlatformPermissionChecker permissionChecker,
            PlatformSecurityProperties securityProperties,
            ObjectMapper objectMapper
    ) {
        ToolPort guarded = new GuardrailToolPort(toolRegistryDelegate, guardrailPort, objectMapper);
        return new AuthorizingToolPort(guarded, permissionChecker, securityProperties, objectMapper);
    }

    /**
     * 带 LLM 调用链埋点的模型端口（Week 17）。装饰 ai-core 装配的原始适配器，
     * 使全部 Agent / Workflow / Multi-Agent 的模型调用都产出 {@code llm.chat} span 与 token 指标。
     *
     * <p>标注 {@code @Primary}：所有按类型注入 {@link ChatModelPort} 的编排组件（Graph / Workflow /
     * Multi-Agent）自动获得带观测的版本，无需逐个改造构造器。</p>
     */
    @Bean
    @Primary
    ChatModelPort observableChatModelPort(
            @Qualifier("springAiChatModelAdapter") ChatModelPort springAiChatModelAdapter,
            AgentObservabilityPort agentObservabilityPort
    ) {
        return new ObservableChatModelAdapter(springAiChatModelAdapter, agentObservabilityPort);
    }

    /**
     * 带工具调用链埋点的工具端口（Week 17）。链：
     * ObservableToolPort → AuthorizingToolPort → GuardrailToolPort → ToolRegistry。
     *
     * <p>标注 {@code @Primary}：编排组件按类型注入 {@link ToolPort} 时拿到埋点版本（最外层），
     * 权限与 Guardrail 校验仍在同一条链上，不会被绕过。</p>
     */
    @Bean
    @Primary
    ToolPort observableToolPort(
            @Qualifier("frameworkToolPort") ToolPort frameworkToolPort,
            AgentObservabilityPort agentObservabilityPort
    ) {
        return new ObservableToolPort(frameworkToolPort, agentObservabilityPort);
    }

    /**
     * Spring AI Alibaba Graph 编排的 Agent 领域服务。
     */
    @Bean
    FrameworkAgentGraph frameworkAgentGraph(
            ChatModelPort chatModelPort,
            PromptTemplatePort promptTemplatePort,
            @Qualifier("observableToolPort") ToolPort frameworkToolPort,
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
            @Qualifier("observableToolPort") ToolPort frameworkToolPort,
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
            @Qualifier("observableToolPort") ToolPort frameworkToolPort,
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
