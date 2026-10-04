package com.aicode.framework.platform.application;

import com.aicode.core.domain.model.TokenUsage;
import com.aicode.core.infrastructure.config.GuardrailProperties;
import com.aicode.core.infrastructure.security.DefaultGuardrailAdapter;
import com.aicode.framework.infrastructure.logging.TraceIds;
import com.aicode.framework.observability.OtelSdkTestSupport;
import com.aicode.framework.observability.domain.AgentObservabilityPort;
import com.aicode.framework.observability.domain.NoopObservabilityAdapter;
import com.aicode.framework.observability.domain.ObservabilityAttributes;
import com.aicode.framework.observability.infrastructure.MicrometerObservabilityAdapter;
import com.aicode.framework.platform.domain.model.AgentType;
import com.aicode.framework.platform.domain.model.ExecutionStatus;
import com.aicode.framework.platform.domain.model.PlatformAgentConfig;
import com.aicode.framework.platform.domain.model.PlatformAgentDefinition;
import com.aicode.framework.platform.domain.model.PlatformRole;
import com.aicode.framework.platform.domain.model.PlatformRunOutput;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.service.PlatformAgentRunner;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryAgentRegistryAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryAuditLogAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryExecutionRecordAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformRoleAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformUserAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Agent 执行链埋点测试（Week 17）：{@code agent.run} span 的属性、终态标记与 {@code agent.run.*} 指标。
 */
@ExtendWith(MockitoExtension.class)
class PlatformExecutionObservabilityTest extends OtelSdkTestSupport {

    private static final String SPAN_AGENT_RUN = "agent.run";

    @Mock
    private PlatformAgentRunner platformAgentRunner;

    private SimpleMeterRegistry registry;
    private PlatformExecutionUseCase useCase;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        AgentObservabilityPort observability = new MicrometerObservabilityAdapter(tracer, registry);

        InMemoryAgentRegistryAdapter agentRegistry = new InMemoryAgentRegistryAdapter();
        InMemoryPlatformUserAdapter users = new InMemoryPlatformUserAdapter();
        InMemoryPlatformRoleAdapter roles = new InMemoryPlatformRoleAdapter();
        PlatformPermissionChecker checker = new PlatformPermissionChecker(users, roles);
        DefaultGuardrailAdapter guardrail = new DefaultGuardrailAdapter(
                new GuardrailProperties(true, 8192, true, true, List.of("patientId", "task")));

        roles.save(new PlatformRole("operator", "Operator", false,
                Set.of("medical-assistant"), Set.of("PatientLookupTool"), Set.of("P001")));
        users.save(new PlatformUser(2L, "operator", "hash", "operator"));
        agentRegistry.save(new PlatformAgentDefinition(
                "medical-assistant", "Medical", "desc", AgentType.MEDICAL_ASSISTANT,
                PlatformAgentConfig.defaults(), Instant.now()));

        useCase = new PlatformExecutionUseCase(
                agentRegistry, new InMemoryExecutionRecordAdapter(), platformAgentRunner, checker,
                new PlatformGuardrailService(guardrail), new ObjectMapper(),
                new InMemoryAuditLogAdapter(), observability);
    }

    @AfterEach
    void clearMdc() {
        org.slf4j.MDC.clear();
    }

    @Test
    void recordsAgentRunSpanWithBusinessAttributesAndTokenUsage() {
        when(platformAgentRunner.run(any(), any())).thenReturn(new PlatformRunOutput(
                Map.of("report", "ok"), new TokenUsage(10, 20, 30), "deepseek-chat"));

        useCase.runAgent(2L, "medical-assistant", Map.of("patientId", "P001"));

        SpanData span = finishedSpan(SPAN_AGENT_RUN);
        assertThat(span.getAttributes().get(AttributeKey.stringKey(ObservabilityAttributes.AGENT_KEY)))
                .isEqualTo("medical-assistant");
        assertThat(span.getAttributes().get(AttributeKey.stringKey(ObservabilityAttributes.AGENT_TYPE)))
                .isEqualTo("MEDICAL_ASSISTANT");
        assertThat(span.getAttributes().get(AttributeKey.stringKey(ObservabilityAttributes.USER_ID)))
                .isEqualTo("2");
        assertThat(span.getAttributes().get(AttributeKey.stringKey(ObservabilityAttributes.EXECUTION_STATUS)))
                .isEqualTo("COMPLETED");
        assertThat(span.getAttributes().get(AttributeKey.stringKey(ObservabilityAttributes.GEN_AI_REQUEST_MODEL)))
                .isEqualTo("deepseek-chat");
        assertThat(span.getAttributes().get(AttributeKey.stringKey(ObservabilityAttributes.GEN_AI_USAGE_TOTAL_TOKENS)))
                .isEqualTo("30");
        assertThat(span.getAttributes().get(AttributeKey.stringKey(ObservabilityAttributes.EXECUTION_ID)))
                .isNotBlank();
        assertThat(span.getStatus().getStatusCode())
                .isEqualTo(io.opentelemetry.api.trace.StatusCode.UNSET);
    }

    @Test
    void recordsAgentRunMetricsWithStatusTag() {
        when(platformAgentRunner.run(any(), any())).thenReturn(new PlatformRunOutput(
                Map.of("report", "ok"), new TokenUsage(1, 1, 2), "deepseek-chat"));

        useCase.runAgent(2L, "medical-assistant", Map.of("patientId", "P001"));

        assertThat(registry.find("agent.run.count")
                .tag(ObservabilityAttributes.AGENT_KEY, "medical-assistant")
                .tag(ObservabilityAttributes.EXECUTION_STATUS, "COMPLETED")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.find("agent.run.duration")
                .tag(ObservabilityAttributes.AGENT_KEY, "medical-assistant")
                .timer()).isNotNull();
    }

    @Test
    void marksAgentRunSpanAsErrorAndCountsFailure() {
        when(platformAgentRunner.run(any(), any()))
                .thenThrow(new IllegalStateException("graph produced no state"));

        assertThatThrownBy(() -> useCase.runAgent(2L, "medical-assistant", Map.of("patientId", "P001")))
                .isInstanceOf(IllegalStateException.class);

        SpanData span = finishedSpan(SPAN_AGENT_RUN);
        assertThat(span.getAttributes().get(AttributeKey.stringKey(ObservabilityAttributes.EXECUTION_STATUS)))
                .isEqualTo("FAILED");
        assertThat(span.getStatus().getStatusCode())
                .isEqualTo(io.opentelemetry.api.trace.StatusCode.ERROR);
        assertThat(registry.find("agent.run.count")
                .tag(ObservabilityAttributes.EXECUTION_STATUS, "FAILED")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void agentRunSpanSharesTraceIdWithMdcTraceId() {
        // 生产路径：TraceIdFilter 把 traceId 写进 MDC；带上游 traceparent 时还把其 spanId 记入 MDC，
        // 适配器据此把同一 traceId 设为 span 的父上下文 —— 链路后端 traceId 与请求 traceId 可互推。
        org.slf4j.MDC.put(TraceIds.MDC_KEY, "client-trace-123");
        org.slf4j.MDC.put(TraceIds.MDC_PARENT_SPAN_ID, "00f067aa0ba902b7");
        when(platformAgentRunner.run(any(), any())).thenReturn(new PlatformRunOutput(
                Map.of("report", "ok"), new TokenUsage(1, 1, 2), "deepseek-chat"));

        useCase.runAgent(2L, "medical-assistant", Map.of("patientId", "P001"));

        assertThat(finishedSpan(SPAN_AGENT_RUN).getTraceId())
                .isEqualTo(TraceIds.otelTraceId("client-trace-123"));
        assertThat(finishedSpan(SPAN_AGENT_RUN).getParentSpanId()).isEqualTo("00f067aa0ba902b7");
    }

    @Test
    void sanitizedOutputIsNotCopiedIntoSpanAttributes() {
        when(platformAgentRunner.run(any(), any())).thenReturn(new PlatformRunOutput(
                Map.of("report", "联系电话13812345678"), new TokenUsage(1, 1, 2), "m"));

        useCase.runAgent(2L, "medical-assistant", Map.of("patientId", "P001"));

        assertThat(finishedSpan(SPAN_AGENT_RUN).getAttributes().asMap().values())
                .noneMatch(value -> String.valueOf(value).contains("13812345678")
                        || String.valueOf(value).contains("138****5678")
                        || String.valueOf(value).contains("P001"));
    }

    @Test
    void disabledObservabilityKeepsAgentRunWorkingWithoutSpans() {
        InMemoryPlatformUserAdapter users = new InMemoryPlatformUserAdapter();
        InMemoryPlatformRoleAdapter roles = new InMemoryPlatformRoleAdapter();
        roles.save(new PlatformRole("operator", "Operator", false,
                Set.of("medical-assistant"), Set.of("PatientLookupTool"), Set.of("P001")));
        users.save(new PlatformUser(2L, "operator", "hash", "operator"));
        InMemoryAgentRegistryAdapter agents = new InMemoryAgentRegistryAdapter();
        agents.save(new PlatformAgentDefinition(
                "medical-assistant", "Medical", "desc", AgentType.MEDICAL_ASSISTANT,
                PlatformAgentConfig.defaults(), Instant.now()));

        PlatformExecutionUseCase disabled = new PlatformExecutionUseCase(
                agents, new InMemoryExecutionRecordAdapter(), platformAgentRunner,
                new PlatformPermissionChecker(users, roles),
                new PlatformGuardrailService(new DefaultGuardrailAdapter(
                        new GuardrailProperties(true, 8192, true, true, List.of("patientId", "task")))),
                new ObjectMapper(), new InMemoryAuditLogAdapter(), new NoopObservabilityAdapter());

        when(platformAgentRunner.run(any(), any())).thenReturn(new PlatformRunOutput(
                Map.of("report", "ok"), new TokenUsage(1, 1, 2), "deepseek-chat"));
        var record = disabled.runAgent(2L, "medical-assistant", Map.of("patientId", "P001"));

        assertThat(record.status()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(disabled.listExecutions(2L)).hasSize(1);
        assertThat(finishedSpans()).isEmpty();
        assertThat(registry.getMeters()).isEmpty();
    }

    @Test
    void listExecutionsDoesNotCreateSpans() {
        useCase.listExecutions(2L);

        assertThat(finishedSpans()).isEmpty();
    }
}
