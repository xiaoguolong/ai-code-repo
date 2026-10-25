package com.aicode.framework.platform.application;

import com.aicode.core.domain.model.TokenUsage;
import com.aicode.core.infrastructure.config.EmbeddingProperties;
import com.aicode.core.infrastructure.config.GuardrailProperties;
import com.aicode.core.infrastructure.embedding.HashingEmbeddingAdapter;
import com.aicode.core.infrastructure.security.DefaultGuardrailAdapter;
import com.aicode.framework.observability.domain.NoopObservabilityAdapter;
import com.aicode.framework.platform.domain.model.AgentType;
import com.aicode.framework.platform.domain.model.ExecutionRecord;
import com.aicode.framework.platform.domain.model.ExecutionStatus;
import com.aicode.framework.platform.domain.model.PlatformAgentConfig;
import com.aicode.framework.platform.domain.model.PlatformAgentDefinition;
import com.aicode.framework.platform.domain.model.PlatformApproval;
import com.aicode.framework.platform.domain.model.PlatformRole;
import com.aicode.framework.platform.domain.model.PlatformRunOutput;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.service.PlatformAgentRunner;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import com.aicode.framework.platform.infrastructure.config.PlatformMemoryLifecycleProperties;
import com.aicode.framework.platform.infrastructure.config.PlatformMemoryProperties;
import com.aicode.framework.platform.infrastructure.embedding.MemoryEmbeddingIdentity;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryAgentRegistryAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryAuditLogAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryExecutionRecordAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformApprovalAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformMemoryAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformRoleAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformUserAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 平台执行用例 × HITL（Week 23）：pending 分支创建审批任务、批准/拒绝迁移执行终态。
 */
@ExtendWith(MockitoExtension.class)
class PlatformExecutionHitlTest {

    private static final Instant NOW = Instant.parse("2026-10-25T00:00:00Z");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private PlatformAgentRunner platformAgentRunner;

    private InMemoryAgentRegistryAdapter agentRegistry;
    private InMemoryExecutionRecordAdapter executions;
    private InMemoryPlatformApprovalAdapter approvals;
    private InMemoryPlatformUserAdapter users;
    private InMemoryPlatformMemoryAdapter memories;
    private PlatformMemoryUseCase memoryUseCase;
    private PlatformApprovalUseCase approvalUseCase;
    private PlatformApprovalDecisionUseCase approvalDecisionUseCase;
    private PlatformExecutionUseCase executionUseCase;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        agentRegistry = new InMemoryAgentRegistryAdapter();
        executions = new InMemoryExecutionRecordAdapter();
        approvals = new InMemoryPlatformApprovalAdapter();
        users = new InMemoryPlatformUserAdapter();
        memories = new InMemoryPlatformMemoryAdapter(clock);
        InMemoryPlatformRoleAdapter roles = new InMemoryPlatformRoleAdapter();
        roles.save(new PlatformRole("admin", "平台管理员", true, Set.of(), Set.of(), Set.of()));
        roles.save(new PlatformRole("operator", "运营人员", false,
                Set.of("patient-analysis"), Set.of(), Set.of("P002")));
        users.save(new PlatformUser(2L, "operator", "hash", "operator"));
        PlatformPermissionChecker checker = new PlatformPermissionChecker(users, roles);
        InMemoryAuditLogAdapter audit = new InMemoryAuditLogAdapter();

        HashingEmbeddingAdapter embedder = new HashingEmbeddingAdapter(
                new EmbeddingProperties("hashing", "", "", "demo-embed", 16, 10));
        memoryUseCase = new PlatformMemoryUseCase(
                memories, users, checker, embedder,
                new PlatformMemoryProperties(500, 0.05, 5, 20),
                new PlatformMemoryLifecycleProperties(30, false, "", 100), clock,
                new MemoryEmbeddingIdentity(new EmbeddingProperties("hashing", "", "", "demo-embed", 16, 10)));
        approvalUseCase = new PlatformApprovalUseCase(approvals, users, checker, audit, clock);
        approvalDecisionUseCase = new PlatformApprovalDecisionUseCase(
                approvals, executions, memoryUseCase, users, checker, audit, MAPPER, clock,
                new PlatformExecutionRecordWriter(executions, MAPPER));
        executionUseCase = new PlatformExecutionUseCase(
                agentRegistry,
                executions,
                platformAgentRunner,
                checker,
                new PlatformGuardrailService(new DefaultGuardrailAdapter(
                        new GuardrailProperties(true, 8192, true, true, List.of("patientId", "task")))),
                MAPPER,
                audit,
                new NoopObservabilityAdapter(),
                approvalUseCase);

        agentRegistry.save(new PlatformAgentDefinition(
                "patient-analysis", "患者分析", "Week 23 HITL", AgentType.PATIENT_ANALYSIS,
                PlatformAgentConfig.defaults(), NOW));
    }

    @Test
    void highRiskRunStopsAtPendingApprovalAndCreatesApprovalTask() throws Exception {
        when(platformAgentRunner.run(any(), any())).thenReturn(pendingOutput());

        ExecutionRecord pending = executionUseCase.runAgent(
                2L, "patient-analysis", Map.of("patientId", "P002", "task", "任务"));

        assertThat(pending.status()).isEqualTo(ExecutionStatus.PENDING_APPROVAL);
        assertThat(pending.finishedAt()).isNull();
        JsonNode output = MAPPER.readTree(pending.outputJson());
        assertThat(output.path("approvalId").asText()).isNotBlank();
        assertThat(output.path("approvalStatus").asText()).isEqualTo("PENDING");
        assertThat(output.path("memoryId").isMissingNode() || output.path("memoryId").isNull()).isTrue();
        assertThat(approvals.findById(output.path("approvalId").asText())).isPresent();
    }

    @Test
    void approveMovesExecutionToCompletedAndWritesMemory() throws Exception {
        when(platformAgentRunner.run(any(), any())).thenReturn(pendingOutput());
        ExecutionRecord pending = executionUseCase.runAgent(
                2L, "patient-analysis", Map.of("patientId", "P002", "task", "任务"));
        String approvalId = MAPPER.readTree(pending.outputJson()).path("approvalId").asText();

        ApprovalDecision decision =
                approvalDecisionUseCase.decide(2L, approvalId, true, "同意");

        assertThat(decision.executionStatus()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(decision.memoryId()).isNotBlank();
        assertThat(executions.findById(pending.executionId()).orElseThrow().status())
                .isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(memoryUseCase.list(2L, null, "P002", null, 5)).hasSize(1);
    }

    @Test
    void rejectMovesExecutionToRejectedWithoutMemory() throws Exception {
        when(platformAgentRunner.run(any(), any())).thenReturn(pendingOutput());
        ExecutionRecord pending = executionUseCase.runAgent(
                2L, "patient-analysis", Map.of("patientId", "P002", "task", "任务"));
        String approvalId = MAPPER.readTree(pending.outputJson()).path("approvalId").asText();

        ApprovalDecision decision =
                approvalDecisionUseCase.decide(2L, approvalId, false, "拒绝");

        assertThat(decision.executionStatus()).isEqualTo(ExecutionStatus.REJECTED);
        assertThat(executions.findById(pending.executionId()).orElseThrow().status())
                .isEqualTo(ExecutionStatus.REJECTED);
        assertThat(memoryUseCase.list(2L, null, "P002", null, 5)).isEmpty();
    }

    @Test
    void nonPendingRunDoesNotCreateApprovalTask() {
        when(platformAgentRunner.run(any(), any())).thenReturn(
                new PlatformRunOutput(Map.of("report", "报告"), new TokenUsage(1, 1, 2), "model"));

        ExecutionRecord completed = executionUseCase.runAgent(
                2L, "patient-analysis", Map.of("patientId", "P002", "task", "任务"));

        assertThat(completed.status()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(approvals.listAll(com.aicode.framework.platform.domain.model.ApprovalStatus.PENDING, 20))
                .isEmpty();
    }

    @Test
    void pendingOutputWithoutApprovalUseCaseFailsLoudly() {
        PlatformExecutionUseCase misconfigured = new PlatformExecutionUseCase(
                agentRegistry, executions, platformAgentRunner,
                new PlatformPermissionChecker(users, rolesForChecker()), new PlatformGuardrailService(
                        new DefaultGuardrailAdapter(new GuardrailProperties(
                                true, 8192, true, true, List.of("patientId", "task")))),
                MAPPER, new InMemoryAuditLogAdapter(), new NoopObservabilityAdapter());
        when(platformAgentRunner.run(any(), any())).thenReturn(pendingOutput());

        assertThatThrownBy(() -> misconfigured.runAgent(
                2L, "patient-analysis", Map.of("patientId", "P002", "task", "任务")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(executions.findById(executions.listByUserId(2L).get(0).executionId())
                .orElseThrow().status()).isEqualTo(ExecutionStatus.FAILED);
    }

    private PlatformRunOutput pendingOutput() {
        return new PlatformRunOutput(
                Map.of(
                        "patientId", "P002",
                        "riskLevel", "HIGH",
                        "report", "报告正文",
                        "task", "任务"),
                new TokenUsage(10, 20, 30),
                "deepseek-chat",
                true);
    }

    private InMemoryPlatformRoleAdapter rolesForChecker() {
        InMemoryPlatformRoleAdapter roles = new InMemoryPlatformRoleAdapter();
        roles.save(new PlatformRole("admin", "平台管理员", true, Set.of(), Set.of(), Set.of()));
        roles.save(new PlatformRole("operator", "运营人员", false,
                Set.of("patient-analysis"), Set.of(), Set.of("P002")));
        return roles;
    }
}
