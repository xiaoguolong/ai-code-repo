package com.aicode.framework.observability;

import com.aicode.core.domain.model.PromptDescriptor;
import com.aicode.core.domain.model.PromptTemplate;
import com.aicode.core.domain.port.GuardrailPort;
import com.aicode.core.domain.port.PromptTemplatePort;
import com.aicode.framework.observability.domain.ModelPrice;
import com.aicode.framework.observability.domain.ModelPriceCatalog;
import com.aicode.framework.observability.infrastructure.LangfuseConfiguration;
import com.aicode.framework.observability.infrastructure.LangfuseGenerationSupport;
import com.aicode.framework.observability.infrastructure.LangfuseOtlpSpanExporter;
import com.aicode.framework.observability.infrastructure.LangfusePromptAdapter;
import com.aicode.framework.observability.infrastructure.LangfuseSpanEnricher;
import com.aicode.framework.observability.infrastructure.ObservabilityConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.test.simple.SimpleTracer;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.opentelemetry.OpenTelemetryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.OpenTelemetryTracingAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.SpanExporters;
import org.springframework.boot.actuate.autoconfigure.tracing.otlp.OtlpTracingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Langfuse 装配测试（Week 18）。
 *
 * <p>本测试守住本周最关键的一条工程决策：<b>自定义导出器不能挤掉 Boot 的 collector 导出器</b>。
 * Boot 3.4.5 的退避条件只看 {@code OtlpHttpSpanExporter} / {@code OtlpGrpcSpanExporter} 类型，
 * 所以 Langfuse 导出器必须包成自定义类型；这里用真实的 Boot 自动装配断言
 * {@code SpanExporters} 里同时存在两个导出器——即「一次埋点、两路导出」确实成立。</p>
 */
class LangfuseWiringContextTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    OtlpTracingAutoConfiguration.class,
                    OpenTelemetryAutoConfiguration.class,
                    OpenTelemetryTracingAutoConfiguration.class))
            .withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new)
            .withBean(io.micrometer.tracing.Tracer.class, SimpleTracer::new)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean("classpathPromptTemplateAdapter", PromptTemplatePort.class, LocalPrompts::new)
            .withBean(GuardrailPort.class, GuardrailStub::active)
            .withUserConfiguration(ObservabilityConfiguration.class, LangfuseConfiguration.class);

    @Test
    void defaultsToFullyDisabledWithoutLangfuseBeans() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(LangfuseOtlpSpanExporter.class);
            assertThat(context).doesNotHaveBean(LangfuseSpanEnricher.class);
            assertThat(context).doesNotHaveBean(LangfusePromptAdapter.class);
            assertThat(context.getBean(LangfuseGenerationSupport.class).enabled()).isFalse();
        });
    }

    @Test
    void enablesDualExportKeepingBootCollectorExporter() {
        runner.withPropertyValues(
                        "langfuse.enabled=true",
                        "langfuse.host=http://127.0.0.1:3000",
                        "langfuse.public-key=pk-lf-test",
                        "langfuse.secret-key=sk-lf-test",
                        "management.otlp.tracing.endpoint=http://127.0.0.1:4318/v1/traces",
                        "management.otlp.tracing.export.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(LangfuseOtlpSpanExporter.class);
                    assertThat(context).hasSingleBean(LangfuseSpanEnricher.class);
                    assertThat(context.getBean(LangfuseGenerationSupport.class).enabled()).isTrue();

                    // Boot 的 collector 导出器仍在（没有被我们的自定义类型挤掉）
                    assertThat(context.getBeanNamesForType(OtlpHttpSpanExporter.class)).hasSize(1);
                    SpanExporters exporters = context.getBean(SpanExporters.class);
                    assertThat(exporters.list()).hasSize(2);
                    assertThat(exporters.list())
                            .anyMatch(exporter -> exporter instanceof LangfuseOtlpSpanExporter);
                });
    }

    @Test
    void failsFastWhenEnabledWithoutCredentials() {
        runner.withPropertyValues(
                        "langfuse.enabled=true",
                        "langfuse.host=http://127.0.0.1:3000",
                        "management.otlp.tracing.export.enabled=false")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("langfuse.public-key");
                });
    }

    @Test
    void enablesPromptManagementIndependentlyFromTracing() {
        runner.withPropertyValues(
                        "langfuse.enabled=false",
                        "langfuse.prompt.enabled=true",
                        "langfuse.host=http://127.0.0.1:3000",
                        "langfuse.public-key=pk-lf-test",
                        "langfuse.secret-key=sk-lf-test")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(LangfusePromptAdapter.class);
                    assertThat(context).doesNotHaveBean(LangfuseOtlpSpanExporter.class);
                    // @Primary 生效：编排组件按类型注入时拿到 Langfuse 版本，仍可回退本地
                    assertThat(context.getBean(PromptTemplatePort.class)).isInstanceOf(LangfusePromptAdapter.class);
                });
    }

    @Test
    void bindsModelPriceTableFromConfiguration() {
        runner.withPropertyValues(
                        "langfuse.enabled=true",
                        "langfuse.public-key=pk-lf-test",
                        "langfuse.secret-key=sk-lf-test",
                        "langfuse.model-prices.deepseek-v4-pro.input-per-1m=0.27",
                        "langfuse.model-prices.deepseek-v4-pro.output-per-1m=1.10")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ModelPriceCatalog catalog = context.getBean(ModelPriceCatalog.class);
                    assertThat(catalog.models()).containsExactly("deepseek-v4-pro");
                    Optional<ModelPrice> price = catalog.find("deepseek-v4-pro");
                    assertThat(price).isPresent();
                    assertThat(price.get().inputPerMillion()).isEqualTo(0.27);
                    assertThat(price.get().outputPerMillion()).isEqualTo(1.10);
                });
    }

    /** 本地 Prompt 假实现（起 classpath 模板适配器的作用）。 */
    private static final class LocalPrompts implements PromptTemplatePort {

        @Override
        public PromptTemplate load(String name) {
            return render(name, Map.of());
        }

        @Override
        public PromptTemplate render(String name, Map<String, Object> variables) {
            return new PromptTemplate("v1", "local:" + name);
        }

        @Override
        public List<PromptDescriptor> list() {
            return List.of();
        }
    }
}
