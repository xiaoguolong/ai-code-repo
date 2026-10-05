package com.aicode.framework.observability.infrastructure;

import com.aicode.core.domain.port.GuardrailPort;
import com.aicode.core.domain.port.PromptTemplatePort;
import com.aicode.framework.observability.domain.AgentObservabilityPort;
import com.aicode.framework.observability.domain.ModelPriceCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Langfuse 装配（Week 18）。
 *
 * <p>三类 bean，开关互相独立：</p>
 * <ol>
 *   <li><b>常驻能力</b>（无条件）：配置、请求期维度、价目表、成本计算、正文策略、生成观测支持、Prompt 版本记录。
 *       它们在 Langfuse 关闭时也注册，但 {@code enabled=false} 时全部空转——这样自检接口能如实报告
 *       「关着」而不是「没配」，成本汇总 API 也不依赖 Langfuse 在线。</li>
 *   <li><b>追踪</b>（{@code langfuse.enabled=true}）：一个自定义类型的 {@link SpanExporter}
 *       （不挤掉 Boot 的 collector 导出器，见 {@link LangfuseOtlpSpanExporter} 类注释）
 *       + 一个 {@link SpanProcessor}（把 trace 维度写给每个 span）。</li>
 *   <li><b>Prompt 管理</b>（{@code langfuse.prompt.enabled=true}）：API 客户端 + {@code @Primary} 的
 *       {@link PromptTemplatePort} 装饰器。只需 host 与密钥，与追踪开关无关。</li>
 * </ol>
 */
@Configuration
@EnableConfigurationProperties(LangfuseProperties.class)
public class LangfuseConfiguration {

    /** 请求期 trace 维度持有者（常驻：观测关闭时只是没人写它）。 */
    @Bean
    LangfuseContext langfuseContext() {
        return new LangfuseContext();
    }

    /** Prompt 版本关联记录（常驻：只有 Langfuse Prompt 命中时才会写入）。 */
    @Bean
    LangfusePromptTracker langfusePromptTracker() {
        return new LangfusePromptTracker();
    }

    /** 价目表（常驻：成本汇总 API 与成本指标都依赖它，未配置即为空表）。 */
    @Bean
    ModelPriceCatalog langfuseModelPriceCatalog(LangfuseProperties properties) {
        return properties.modelPriceCatalog();
    }

    /** 成本计算器。 */
    @Bean
    LlmCostCalculator langfuseLlmCostCalculator(ModelPriceCatalog modelPriceCatalog) {
        return new LlmCostCalculator(modelPriceCatalog);
    }

    /** 正文采集策略（含「guardrail 未启用则采集自动关闭」的联锁）。 */
    @Bean
    LangfuseContentPolicy langfuseContentPolicy(
            LangfuseProperties properties, GuardrailPort guardrailPort, ObjectMapper objectMapper) {
        return new LangfuseContentPolicy(properties, guardrailPort, objectMapper);
    }

    /** 生成 / 工具观测属性支持（装饰器依赖它；关闭时空转）。 */
    @Bean
    LangfuseGenerationSupport langfuseGenerationSupport(
            LangfuseProperties properties,
            LlmCostCalculator costCalculator,
            LangfuseContentPolicy contentPolicy,
            LangfusePromptTracker promptTracker,
            ObjectMapper objectMapper
    ) {
        return new LangfuseGenerationSupport(properties, costCalculator, contentPolicy, promptTracker, objectMapper);
    }

    /**
     * Langfuse OTLP 导出器。声明类型为 {@link SpanExporter} 是有意为之：
     * 若声明成 {@code OtlpHttpSpanExporter}，Spring Boot 的 OTLP 自动装配会整体退避，
     * 既有通用 collector 导出会静默消失。
     */
    @Bean
    @ConditionalOnProperty(prefix = "langfuse", name = "enabled", havingValue = "true")
    SpanExporter langfuseOtlpSpanExporter(LangfuseProperties properties) {
        return new LangfuseOtlpSpanExporter(properties);
    }

    /**
     * trace 维度富化器（OTel SpanProcessor，被 Boot 的 {@code SpanProcessors} 收集）。
     *
     * <p>只在开启 Langfuse 时注册：关闭时不产生任何 {@code langfuse.*} 属性。</p>
     */
    @Bean
    @ConditionalOnProperty(prefix = "langfuse", name = "enabled", havingValue = "true")
    SpanProcessor langfuseSpanEnricher(LangfuseContext langfuseContext, LangfuseProperties properties) {
        return new LangfuseSpanEnricher(langfuseContext, properties);
    }

    /**
     * Prompt API 客户端。密钥缺失时启动即失败（配置错误比「数据悄悄不出现」好排查）。
     */
    @Bean
    @ConditionalOnProperty(prefix = "langfuse.prompt", name = "enabled", havingValue = "true")
    LangfusePromptClient langfusePromptClient(LangfuseProperties properties, ObjectMapper objectMapper) {
        properties.requireCredentials();
        return new LangfusePromptClient(properties, objectMapper);
    }

    /**
     * Prompt 管理适配器。{@code @Primary} 让全部编排组件（Graph / Workflow / Multi-Agent）
     * 按类型注入时自动拿到它，无需逐个改构造器。
     */
    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "langfuse.prompt", name = "enabled", havingValue = "true")
    PromptTemplatePort langfusePromptTemplatePort(
            @Qualifier("classpathPromptTemplateAdapter") PromptTemplatePort classpathPromptTemplateAdapter,
            LangfusePromptClient langfusePromptClient,
            LangfusePromptTracker langfusePromptTracker,
            LangfuseProperties properties,
            AgentObservabilityPort agentObservabilityPort
    ) {
        return new LangfusePromptAdapter(classpathPromptTemplateAdapter, langfusePromptClient,
                langfusePromptTracker, properties, agentObservabilityPort);
    }
}
