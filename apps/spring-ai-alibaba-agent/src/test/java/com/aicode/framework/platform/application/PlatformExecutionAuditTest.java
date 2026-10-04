package com.aicode.framework.platform.application;

import com.aicode.core.domain.model.TokenUsage;
import com.aicode.core.infrastructure.config.GuardrailProperties;
import com.aicode.core.infrastructure.security.DefaultGuardrailAdapter;
import com.aicode.framework.platform.domain.exception.PlatformAccessDeniedException;
import com.aicode.framework.platform.domain.exception.PlatformNotFoundException;
import com.aicode.framework.platform.domain.model.AgentType;
import com.aicode.framework.platform.domain.model.AuditAction;
import com.aicode.framework.platform.domain.model.AuditLogEntry;
import com.aicode.framework.platform.domain.model.AuditResult;
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
import com.aicode.framework.infrastructure.logging.TraceIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Agent 执行审计规范：Run 终态与越权读取都必须留痕。
 */
@ExtendWith(MockitoExtension.class)
class PlatformExecutionAuditTest {

    @Mock
    private PlatformAgentRunner platformAgentRunner;

    private InMemoryExecutionRecordAdapter executionRecords;
    private InMemoryAuditLogAdapter auditLog;
    private PlatformExecutionUseCase useCase;

    @BeforeEach
    void setUp() {
        InMemoryAgentRegistryAdapter agentRegistry = new InMemoryAgentRegistryAdapter();
        executionRecords = new InMemoryExecutionRecordAdapter();
        auditLog = new InMemoryAuditLogAdapter();
        InMemoryPlatformUserAdapter users = new InMemoryPlatformUserAdapter();
        InMemoryPlatformRoleAdapter roles = new InMemoryPlatformRoleAdapter();
        PlatformPermissionChecker checker = new PlatformPermissionChecker(users, roles);
        DefaultGuardrailAdapter guardrail = new DefaultGuardrailAdapter(
                new GuardrailProperties(true, 8192, true, true, List.of("patientId", "task")));
        PlatformGuardrailService guardrailService = new PlatformGuardrailService(guardrail);
        useCase = new PlatformExecutionUseCase(agentRegistry, executionRecords, platformAgentRunner,
                checker, guardrailService, new ObjectMapper(), auditLog,
                new com.aicode.framework.observability.domain.NoopObservabilityAdapter());

        roles.save(new PlatformRole("operator", "Operator", false,
                Set.of("medical-assistant"), Set.of("PatientLookupTool"), Set.of("P001")));
        roles.save(new PlatformRole("viewer", "Viewer", false, Set.of(), Set.of(), Set.of()));
        users.save(new PlatformUser(2L, "operator", "hash", "operator"));
        users.save(new PlatformUser(3L, "viewer", "hash", "viewer"));
        agentRegistry.save(new PlatformAgentDefinition(
                "medical-assistant", "Medical", "desc", AgentType.MEDICAL_ASSISTANT,
                PlatformAgentConfig.defaults(), Instant.now()));
    }

    @Test
    void recordsAgentRunSuccessAuditOnce() {
        when(platformAgentRunner.run(any(), any())).thenReturn(new PlatformRunOutput(
                Map.of("report", "ok"), new TokenUsage(1, 2, 3), "test-model"));

        useCase.runAgent(2L, "medical-assistant", Map.of("patientId", "P001"));

        List<AuditLogEntry> agentAudits = auditLog.listAll().stream()
                .filter(entry -> entry.action() == AuditAction.AGENT_RUN)
                .toList();
        assertThat(agentAudits).hasSize(1);
        assertThat(agentAudits.get(0).userId()).isEqualTo(2L);
        assertThat(agentAudits.get(0).resource()).isEqualTo("agent:medical-assistant");
        assertThat(agentAudits.get(0).result()).isEqualTo(AuditResult.SUCCESS);
    }

    @Test
    void recordsAgentRunFailureAuditWhenRunnerThrows() {
        when(platformAgentRunner.run(any(), any())).thenThrow(new IllegalStateException("model down"));

        assertThatThrownBy(() -> useCase.runAgent(2L, "medical-assistant", Map.of("patientId", "P001")))
                .isInstanceOf(IllegalStateException.class);

        List<AuditLogEntry> agentAudits = auditLog.listAll().stream()
                .filter(entry -> entry.action() == AuditAction.AGENT_RUN)
                .toList();
        assertThat(agentAudits).hasSize(1);
        assertThat(agentAudits.get(0).result()).isEqualTo(AuditResult.FAILURE);
        assertThat(executionRecords.findById(
                executionRecords.listAll().get(0).executionId()).orElseThrow().status())
                .isEqualTo(ExecutionStatus.FAILED);
    }

    @Test
    void recordsDeniedAuditWhenReadingAnotherUsersExecution() {
        when(platformAgentRunner.run(any(), any())).thenReturn(new PlatformRunOutput(
                Map.of("report", "ok"), TokenUsage.unknown(), "m"));
        String executionId = useCase.runAgent(2L, "medical-assistant", Map.of("patientId", "P001"))
                .executionId();

        assertThatThrownBy(() -> useCase.getExecution(3L, executionId))
                .isInstanceOf(PlatformAccessDeniedException.class);

        List<AuditLogEntry> denied = auditLog.listAll().stream()
                .filter(entry -> entry.action() == AuditAction.EXECUTION_READ)
                .toList();
        assertThat(denied).hasSize(1);
        assertThat(denied.get(0).result()).isEqualTo(AuditResult.DENIED);
        assertThat(denied.get(0).userId()).isEqualTo(3L);
        assertThat(denied.get(0).resource()).isEqualTo("execution:" + executionId);
    }

    @Test
    void doesNotWriteExecutionReadAuditForMissingExecution() {
        assertThatThrownBy(() -> useCase.getExecution(2L, "exec-missing"))
                .isInstanceOf(PlatformNotFoundException.class);

        assertThat(auditLog.listAll().stream()
                .filter(entry -> entry.action() == AuditAction.EXECUTION_READ)
                .toList()).isEmpty();
    }

    @Test
    void doesNotWriteAgentRunAuditWhenPermissionDenied() {
        assertThatThrownBy(() -> useCase.runAgent(3L, "medical-assistant", Map.of("patientId", "P001")))
                .isInstanceOf(PlatformAccessDeniedException.class);

        assertThat(auditLog.listAll()).isEmpty();
    }

    @Test
    void agentRunAuditCarriesTraceIdFromCurrentRequestMdc() {
        // 回归：审计 traceId 必须取自当前请求 MDC（真库验收曾发现审计 traceId 为空）
        when(platformAgentRunner.run(any(), any())).thenReturn(new PlatformRunOutput(
                Map.of("report", "ok"), TokenUsage.unknown(), "m"));
        MDC.put(TraceIds.MDC_KEY, "trace-run-mdc");
        try {
            useCase.runAgent(2L, "medical-assistant", Map.of("patientId", "P001"));
        } finally {
            MDC.remove(TraceIds.MDC_KEY);
        }

        List<AuditLogEntry> agentAudits = auditLog.listAll().stream()
                .filter(entry -> entry.action() == AuditAction.AGENT_RUN)
                .toList();
        assertThat(agentAudits).hasSize(1);
        assertThat(agentAudits.get(0).traceId()).isEqualTo("trace-run-mdc");
    }

    @Test
    void deniedReadAuditCarriesTraceIdFromCurrentRequestMdc() {
        when(platformAgentRunner.run(any(), any())).thenReturn(new PlatformRunOutput(
                Map.of("report", "ok"), TokenUsage.unknown(), "m"));
        String executionId = useCase.runAgent(2L, "medical-assistant", Map.of("patientId", "P001"))
                .executionId();

        MDC.put(TraceIds.MDC_KEY, "trace-denied-mdc");
        try {
            assertThatThrownBy(() -> useCase.getExecution(3L, executionId))
                    .isInstanceOf(PlatformAccessDeniedException.class);
        } finally {
            MDC.remove(TraceIds.MDC_KEY);
        }

        List<AuditLogEntry> denied = auditLog.listAll().stream()
                .filter(entry -> entry.action() == AuditAction.EXECUTION_READ)
                .toList();
        assertThat(denied).hasSize(1);
        assertThat(denied.get(0).traceId()).isEqualTo("trace-denied-mdc");
    }
}
