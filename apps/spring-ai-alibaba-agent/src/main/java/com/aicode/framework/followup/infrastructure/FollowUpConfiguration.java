package com.aicode.framework.followup.infrastructure;

import com.aicode.core.domain.port.ChatModelPort;
import com.aicode.core.domain.port.PromptTemplatePort;
import com.aicode.core.domain.port.ToolPort;
import com.aicode.core.infrastructure.config.LlmProperties;
import com.aicode.framework.followup.domain.FollowUpPlanner;
import com.aicode.framework.workflow.domain.PatientRiskAssessor;
import com.aicode.framework.workflow.domain.PatientRiskToolResultMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 随访 Agent 装配（Week 24）。
 *
 * <p>工具端口必须用 {@code observableToolPort}（RBAC + Guardrail + 观测链的最外层），
 * 与患者分析一致；用错 bean 会静默绕过授权（第 17 周起容器里有多个 {@code ToolPort}）。</p>
 */
@Configuration
public class FollowUpConfiguration {

    /**
     * 随访计划运行时参数（复用全局 LLM 配置）。
     *
     * @param llmProperties ai-core 的 LLM 配置
     * @return 运行时参数
     */
    @Bean
    FollowUpRuntimeConfig followUpRuntimeConfig(LlmProperties llmProperties) {
        return new FollowUpRuntimeConfig(
                llmProperties.model(), llmProperties.temperature(), llmProperties.maxTokens());
    }

    /** 规则计划（无状态）。 */
    @Bean
    FollowUpPlanner followUpPlanner() {
        return new FollowUpPlanner();
    }

    /**
     * 随访 Agent。
     *
     * @param chatModelPort       模型端口（{@code @Primary} 为带观测的版本）
     * @param promptTemplatePort  版本化 Prompt
     * @param frameworkToolPort   带 RBAC + Guardrail + 观测的工具端口
     * @param riskAssessor        规则风险评估（Week 9）
     * @param toolResultMapper    工具结果 JSON 解析
     * @param planner             规则计划
     * @param runtimeConfig       运行时参数
     * @param clock               UTC 时钟（计划基准时间可注入固定时钟）
     * @return Agent 实例
     */
    @Bean
    FollowUpAgent followUpAgent(
            ChatModelPort chatModelPort,
            PromptTemplatePort promptTemplatePort,
            @Qualifier("observableToolPort") ToolPort frameworkToolPort,
            PatientRiskAssessor riskAssessor,
            PatientRiskToolResultMapper toolResultMapper,
            FollowUpPlanner planner,
            FollowUpRuntimeConfig runtimeConfig,
            Clock clock
    ) {
        return new FollowUpAgent(
                frameworkToolPort, chatModelPort, promptTemplatePort,
                riskAssessor, toolResultMapper, planner, runtimeConfig, clock);
    }
}
