package com.aicode.framework.observability.application;

import com.aicode.framework.observability.GuardrailStub;
import com.aicode.framework.observability.LangfuseTestFixtures;
import com.aicode.framework.observability.OtelSdkTestSupport;
import com.aicode.framework.observability.domain.AgentObservabilityPort;
import com.aicode.framework.observability.domain.LangfuseStatusView;
import com.aicode.framework.observability.domain.ModelPrice;
import com.aicode.framework.observability.domain.SpanKind;
import com.aicode.framework.observability.domain.SpanScope;
import com.aicode.framework.observability.domain.TraceDimensions;
import com.aicode.framework.observability.domain.TraceScope;
import com.aicode.framework.observability.infrastructure.LangfuseContentPolicy;
import com.aicode.framework.observability.infrastructure.LangfuseContext;
import com.aicode.framework.observability.infrastructure.LangfuseProperties;
import com.aicode.framework.observability.infrastructure.MicrometerObservabilityAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Langfuse 自检用例测试（Week 18）。
 *
 * <p>自检接口要能回答三个问题：开关是否生效、导出端点对不对、本次请求的 Langfuse 维度写没写对；
 * 同时<b>绝不回显 secret key</b>（public key 也只回掩码）。</p>
 */
class PlatformObservabilityLangfuseTest extends OtelSdkTestSupport {

    private final LangfuseContext langfuseContext = new LangfuseContext();
    private AgentObservabilityPort port;
    private PlatformObservabilityUseCase useCase;

    @BeforeEach
    void setUp() {
        port = new MicrometerObservabilityAdapter(tracer, new SimpleMeterRegistry(), langfuseContext, true);
    }

    @Test
    void reportsEnabledStateEndpointAndCurrentTraceDimensions() {
        LangfuseProperties properties = LangfuseTestFixtures.tracing(false,
                Map.of("deepseek-v4-pro", new ModelPrice(0.27, 1.10)));
        useCase = new PlatformObservabilityUseCase(port, properties, langfuseContext, contentPolicy(properties));

        try (TraceScope trace = port.beginTrace(
                TraceDimensions.agentRun(1L, "medical-assistant", "MEDICAL_ASSISTANT", "P001"));
             SpanScope span = port.openSpan(SpanKind.AGENT_RUN, "agent.run", Map.of())) {
            assertThat(span.spanId()).isNotBlank();

            LangfuseStatusView status = useCase.langfuseStatus();

            assertThat(status.enabled()).isTrue();
            assertThat(status.host()).isEqualTo("http://langfuse.local:3000");
            assertThat(status.otlpEndpoint()).isEqualTo("http://langfuse.local:3000/api/public/otel/v1/traces");
            assertThat(status.projectId()).isEqualTo("ai-code-repo");
            assertThat(status.environment()).isEqualTo("test");
            assertThat(status.release()).isEqualTo("week18");
            assertThat(status.captureContent()).isFalse();
            assertThat(status.maxContentChars()).isEqualTo(2000);
            assertThat(status.configuredModels()).containsExactly("deepseek-v4-pro");
            assertThat(status.promptManagementEnabled()).isFalse();
            assertThat(status.promptLabel()).isEqualTo("production");
            assertThat(status.promptCacheTtlSeconds()).isEqualTo(60);

            assertThat(status.trace().langfuseTraceName()).isEqualTo("agent:medical-assistant");
            assertThat(status.trace().langfuseUserId()).isEqualTo("1");
            assertThat(status.trace().langfuseSessionId()).isEqualTo("P001");
            assertThat(status.trace().otelTraceId()).isNotBlank();
            assertThat(status.trace().langfuseTraceUrl())
                    .contains("/project/ai-code-repo/traces/")
                    .contains(status.trace().otelTraceId());

            String traceUrl = useCase.langfuseTraceUrl();
            assertThat(traceUrl).isEqualTo(status.trace().langfuseTraceUrl());
        }
    }

    @Test
    void neverExposesSecretKeyAndMasksPublicKey() {
        useCase = new PlatformObservabilityUseCase(port, LangfuseTestFixtures.enabled(), langfuseContext, contentPolicy(LangfuseTestFixtures.enabled()));

        LangfuseStatusView status = useCase.langfuseStatus();

        assertThat(status.publicKeyMasked()).isEqualTo("pk-lf-***");
        assertThat(status.publicKeyMasked()).doesNotContain("sk-lf-test");
        assertThat(status.toString()).doesNotContain("sk-lf-test");
    }

    @Test
    void reportsDisabledStateWithoutTraceUrl() {
        useCase = new PlatformObservabilityUseCase(port, LangfuseTestFixtures.disabled(), langfuseContext, contentPolicy(LangfuseTestFixtures.disabled()));

        LangfuseStatusView status = useCase.langfuseStatus();

        assertThat(status.enabled()).isFalse();
        assertThat(useCase.langfuseTraceUrl()).isNull();
        assertThat(status.trace().langfuseTraceName()).isNull();
        assertThat(status.configuredModels()).isEmpty();
    }

    @Test
    void omitsTraceUrlWhenProjectIdMissing() {
        LangfuseProperties noProject = LangfuseTestFixtures.enabledAt("http://langfuse.local:3000");
        LangfuseProperties withoutProject = new LangfuseProperties(
                noProject.enabled(), noProject.host(), noProject.publicKey(), noProject.secretKey(), "",
                noProject.environment(), noProject.release(), noProject.captureContent(),
                noProject.maxContentChars(), noProject.timeoutMs(), noProject.prompt(), noProject.modelPrices());
        useCase = new PlatformObservabilityUseCase(port, withoutProject, langfuseContext, contentPolicy(withoutProject));

        try (SpanScope span = port.openSpan(SpanKind.AGENT_RUN, "agent.run", Map.of())) {
            assertThat(span.spanId()).isNotBlank();
            assertThat(useCase.langfuseTraceUrl()).isNull();
        }
    }
    private LangfuseContentPolicy contentPolicy(LangfuseProperties properties) {
        return new LangfuseContentPolicy(properties, GuardrailStub.active(), new ObjectMapper());
    }
}