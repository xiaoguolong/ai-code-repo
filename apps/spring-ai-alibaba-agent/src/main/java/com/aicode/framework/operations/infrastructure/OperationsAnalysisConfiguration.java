package com.aicode.framework.operations.infrastructure;

import com.aicode.core.domain.port.ChatModelPort;
import com.aicode.core.domain.port.PromptTemplatePort;
import com.aicode.core.infrastructure.config.LlmProperties;
import com.aicode.framework.observability.domain.ModelPriceCatalog;
import com.aicode.framework.operations.domain.OperationsAnomalyDetector;
import com.aicode.framework.operations.domain.OperationsReportComposer;
import com.aicode.framework.operations.domain.OperationsStatistics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 运营分析 Agent 装配（Week 24）。
 *
 * <p>与患者分析一致：模型端口用 {@code @Primary} 的带观测版本，Prompt 走版本化模板；
 * 不新增任何密钥/模型配置。</p>
 */
@Configuration
public class OperationsAnalysisConfiguration {

    /**
     * 运营分析运行时参数（复用全局 LLM 配置）。
     *
     * @param llmProperties ai-core 的 LLM 配置
     * @return 运行时参数
     */
    @Bean
    OperationsAnalysisRuntimeConfig operationsAnalysisRuntimeConfig(LlmProperties llmProperties) {
        return new OperationsAnalysisRuntimeConfig(
                llmProperties.model(), llmProperties.temperature(), llmProperties.maxTokens());
    }

    /** 统计口径（无状态）。 */
    @Bean
    OperationsStatistics operationsStatistics() {
        return new OperationsStatistics();
    }

    /** 异常规则（无状态）。 */
    @Bean
    OperationsAnomalyDetector operationsAnomalyDetector() {
        return new OperationsAnomalyDetector();
    }

    /** 降级日报模板（无状态）。 */
    @Bean
    OperationsReportComposer operationsReportComposer() {
        return new OperationsReportComposer();
    }

    /**
     * 运营分析 Agent。
     *
     * @param chatModelPort      模型端口
     * @param promptTemplatePort 版本化 Prompt
     * @param statistics         统计口径
     * @param anomalyDetector    异常规则
     * @param reportComposer     降级模板
     * @param priceCatalog       模型价目表（成本估算）
     * @param runtimeConfig      运行时参数
     * @return Agent 实例
     */
    @Bean
    OperationsAnalysisAgent operationsAnalysisAgent(
            ChatModelPort chatModelPort,
            PromptTemplatePort promptTemplatePort,
            OperationsStatistics statistics,
            OperationsAnomalyDetector anomalyDetector,
            OperationsReportComposer reportComposer,
            ModelPriceCatalog priceCatalog,
            OperationsAnalysisRuntimeConfig runtimeConfig
    ) {
        return new OperationsAnalysisAgent(
                chatModelPort, promptTemplatePort, statistics, anomalyDetector,
                reportComposer, priceCatalog, runtimeConfig);
    }
}
