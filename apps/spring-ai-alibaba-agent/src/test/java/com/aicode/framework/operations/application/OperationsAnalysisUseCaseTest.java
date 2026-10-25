package com.aicode.framework.operations.application;

import com.aicode.core.domain.exception.ChatModelException;
import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.core.domain.model.ChatOptions;
import com.aicode.core.domain.model.PromptTemplate;
import com.aicode.core.domain.model.TokenUsage;
import com.aicode.core.domain.port.ChatModelPort;
import com.aicode.core.domain.port.PromptTemplatePort;
import com.aicode.framework.observability.domain.ModelPriceCatalog;
import com.aicode.framework.operations.domain.OperationsAnomalyDetector;
import com.aicode.framework.operations.domain.OperationsReportComposer;
import com.aicode.framework.operations.domain.OperationsStatistics;
import com.aicode.framework.operations.domain.model.OperationsAnomalyCode;
import com.aicode.framework.operations.domain.model.OperationsDailyReport;
import com.aicode.framework.operations.infrastructure.OperationsAnalysisAgent;
import com.aicode.framework.operations.infrastructure.OperationsAnalysisRuntimeConfig;
import com.aicode.framework.platform.domain.model.AgentType;
import com.aicode.framework.platform.domain.model.ApprovalStatus;
import com.aicode.framework.platform.domain.model.ExecutionRecord;
import com.aicode.framework.platform.domain.model.ExecutionStatus;
import com.aicode.framework.platform.domain.model.PlatformApproval;
import com.aicode.framework.platform.domain.model.PlatformRole;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import com.aicode.framework.platform.infrastructure.config.PlatformOperationsProperties;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryExecutionRecordAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformApprovalAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformRoleAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformUserAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 运营分析用例（Week 24）：窗口边界、租户/用户可见性、待审计数与参数校验。
 */
class OperationsAnalysisUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-25T09:00:00Z");
    private static final long ADMIN_ID = 1L;
    private static final long OPERATOR_ID = 2L;
    private static final long OTHER_OPERATOR_ID = 3L;

    private InMemoryExecutionRecordAdapter executions;
    private InMemoryPlatformApprovalAdapter approvals;
    private OperationsAnalysisUseCase useCase;

    @BeforeEach
    void setUp() {
        executions = new InMemoryExecutionRecordAdapter();
        approvals = new InMemoryPlatformApprovalAdapter();

        InMemoryPlatformRoleAdapter roles = new InMemoryPlatformRoleAdapter();
        roles.save(new PlatformRole("admin", "平台管理员", true, Set.of(), Set.of(), Set.of()));
        roles.save(new PlatformRole("operator", "运营人员", false, Set.of(), Set.of(), Set.of()));
        InMemoryPlatformUserAdapter users = new InMemoryPlatformUserAdapter();
        users.save(new PlatformUser(ADMIN_ID, "admin", "hash", "admin"));
        users.save(new PlatformUser(OPERATOR_ID, "operator", "hash", "operator"));
        users.save(new PlatformUser(OTHER_OPERATOR_ID, "operator2", "hash", "operator"));
        PlatformPermissionChecker checker = new PlatformPermissionChecker(users, roles);

        ChatModelPort chatModelPort = mock(ChatModelPort.class);
        when(chatModelPort.chat(anyList(), any(ChatOptions.class)))
                .thenThrow(new ChatModelException("model down"));
        PromptTemplatePort prompts = mock(PromptTemplatePort.class);
        when(prompts.load(anyString())).thenReturn(new PromptTemplate("v1", "system"));

        OperationsAnalysisAgent agent = new OperationsAnalysisAgent(
                chatModelPort, prompts, new OperationsStatistics(), new OperationsAnomalyDetector(),
                new OperationsReportComposer(), ModelPriceCatalog.empty(),
                new OperationsAnalysisRuntimeConfig("test-model", 0.2, 512));

        useCase = new OperationsAnalysisUseCase(
                executions, approvals, checker, agent,
                new PlatformOperationsProperties(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void adminSeesWholeTenantAndOperatorSeesOnlyOwnRuns() {
        saveExecution("e1", OPERATOR_ID, ExecutionStatus.COMPLETED, 100, 50, NOW.minusSeconds(3600));
        saveExecution("e2", OTHER_OPERATOR_ID, ExecutionStatus.FAILED, 0, 0, NOW.minusSeconds(7200));

        OperationsDailyReport adminReport = useCase.run(ADMIN_ID, 7);
        assertThat(adminReport.summary().totalRuns()).isEqualTo(2);
        assertThat(adminReport.summary().completed()).isEqualTo(1);
        assertThat(adminReport.summary().failed()).isEqualTo(1);
        assertThat(adminReport.windowDays()).isEqualTo(7);
        assertThat(adminReport.windowEnd()).isEqualTo(NOW);
        assertThat(adminReport.windowStart()).isEqualTo(NOW.minusSeconds(7 * 86400L));

        OperationsDailyReport operatorReport = useCase.run(OPERATOR_ID, 7);
        assertThat(operatorReport.summary().totalRuns()).isEqualTo(1);
        assertThat(operatorReport.summary().completed()).isEqualTo(1);
        assertThat(operatorReport.summary().failed()).isZero();
    }

    @Test
    void windowExcludesRecordsOutsideTheRange() {
        saveExecution("inside", OPERATOR_ID, ExecutionStatus.COMPLETED, 0, 0, NOW.minusSeconds(3600));
        saveExecution("tooOld", OPERATOR_ID, ExecutionStatus.FAILED, 0, 0, NOW.minusSeconds(8 * 86400L));

        OperationsDailyReport report = useCase.run(OPERATOR_ID, 7);

        assertThat(report.summary().totalRuns()).isEqualTo(1);
        assertThat(report.summary().failed()).isZero();
    }

    @Test
    void pendingApprovalsAreScopedByVisibilityAndFeedTheBacklogRule() {
        for (int i = 0; i < 21; i++) {
            approvals.save(new PlatformApproval(
                    "a" + i, "e" + i, "default", OPERATOR_ID, "patient-analysis", "P002", "HIGH", "任务" + i,
                    ApprovalStatus.PENDING, "", null, NOW, null));
        }
        saveExecution("e1", OPERATOR_ID, ExecutionStatus.COMPLETED, 0, 0, NOW.minusSeconds(3600));

        OperationsDailyReport report = useCase.run(OPERATOR_ID, 7);

        assertThat(report.anomalies()).extracting(anomaly -> anomaly.code())
                .contains(OperationsAnomalyCode.APPROVAL_BACKLOG);
        assertThat(report.anomalies().stream()
                .filter(anomaly -> anomaly.code() == OperationsAnomalyCode.APPROVAL_BACKLOG)
                .findFirst()
                .orElseThrow()
                .value()).isEqualTo(21);
    }

    @Test
    void tokenSpikeComparesAgainstThePreviousEqualWindow() {
        saveExecution("current", OPERATOR_ID, ExecutionStatus.COMPLETED, 100, 100, NOW.minusSeconds(3600));
        saveExecution("previous", OPERATOR_ID, ExecutionStatus.COMPLETED, 10, 10, NOW.minusSeconds(8 * 86400L));

        OperationsDailyReport report = useCase.run(OPERATOR_ID, 7);

        assertThat(report.anomalies()).extracting(anomaly -> anomaly.code())
                .contains(OperationsAnomalyCode.TOKEN_SPIKE);
    }

    @Test
    void windowDaysDefaultsAndValidatesRange() {
        assertThat(useCase.run(OPERATOR_ID, null).windowDays()).isEqualTo(7);
        assertThatThrownBy(() -> useCase.run(OPERATOR_ID, 0))
                .isInstanceOf(InvalidChatRequestException.class);
        assertThatThrownBy(() -> useCase.run(OPERATOR_ID, 31))
                .isInstanceOf(InvalidChatRequestException.class);
    }

    @Test
    void marksTruncatedWhenReadLimitIsHit() {
        PlatformOperationsProperties properties = new PlatformOperationsProperties();
        properties.setMaxRecords(1);
        ChatModelPort chatModelPort = mock(ChatModelPort.class);
        when(chatModelPort.chat(anyList(), any(ChatOptions.class)))
                .thenThrow(new ChatModelException("model down"));
        PromptTemplatePort prompts = mock(PromptTemplatePort.class);
        when(prompts.load(anyString())).thenReturn(new PromptTemplate("v1", "system"));
        OperationsAnalysisUseCase limited = new OperationsAnalysisUseCase(
                executions,
                approvals,
                new PlatformPermissionChecker(
                        usersWithTwoOperators(),
                        rolesWithOperator()),
                new OperationsAnalysisAgent(chatModelPort, prompts, new OperationsStatistics(),
                        new OperationsAnomalyDetector(), new OperationsReportComposer(),
                        ModelPriceCatalog.empty(),
                        new OperationsAnalysisRuntimeConfig("test-model", 0.2, 512)),
                properties, Clock.fixed(NOW, ZoneOffset.UTC));

        saveExecution("e1", OPERATOR_ID, ExecutionStatus.COMPLETED, 0, 0, NOW.minusSeconds(3600));
        saveExecution("e2", OPERATOR_ID, ExecutionStatus.COMPLETED, 0, 0, NOW.minusSeconds(7200));

        assertThat(limited.run(OPERATOR_ID, 7).summary().truncated()).isTrue();
    }

    private InMemoryPlatformUserAdapter usersWithTwoOperators() {
        InMemoryPlatformUserAdapter users = new InMemoryPlatformUserAdapter();
        users.save(new PlatformUser(ADMIN_ID, "admin", "hash", "admin"));
        users.save(new PlatformUser(OPERATOR_ID, "operator", "hash", "operator"));
        return users;
    }

    private InMemoryPlatformRoleAdapter rolesWithOperator() {
        InMemoryPlatformRoleAdapter roles = new InMemoryPlatformRoleAdapter();
        roles.save(new PlatformRole("admin", "平台管理员", true, Set.of(), Set.of(), Set.of()));
        roles.save(new PlatformRole("operator", "运营人员", false, Set.of(), Set.of(), Set.of()));
        return roles;
    }

    @Test
    void pendingApprovalEntityIsNotCountedAsFailure() {
        approvals.save(new PlatformApproval(
                "a1", "e1", "default", OPERATOR_ID, "patient-analysis", "P002", "HIGH", "任务",
                ApprovalStatus.PENDING, "", null, NOW, null));

        assertThat(useCase.run(OPERATOR_ID, 7).summary().pendingApproval()).isZero();
        assertThat(approvals.countByUser(OPERATOR_ID, ApprovalStatus.PENDING)).isEqualTo(1);
    }

    private void saveExecution(
            String id, long userId, ExecutionStatus status, int prompt, int completion, Instant startedAt) {
        executions.save(new ExecutionRecord(
                id, userId, "patient-analysis", AgentType.PATIENT_ANALYSIS, status,
                "{}", "{}", "deepseek-chat", new TokenUsage(prompt, completion, prompt + completion),
                "", startedAt, status == ExecutionStatus.RUNNING ? null : startedAt));
    }
}
