package com.aicode.framework.platform.application;

import com.aicode.core.domain.exception.ChatModelException;
import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.core.domain.exception.StreamCancelledException;
import com.aicode.core.domain.model.ChatStreamListener;
import com.aicode.core.domain.model.TokenUsage;
import com.aicode.core.infrastructure.config.GuardrailProperties;
import com.aicode.core.infrastructure.security.DefaultGuardrailAdapter;
import com.aicode.framework.observability.domain.NoopObservabilityAdapter;
import com.aicode.framework.platform.domain.exception.PlatformAccessDeniedException;
import com.aicode.framework.platform.domain.model.AgentType;
import com.aicode.framework.platform.domain.model.ApprovalStatus;
import com.aicode.framework.platform.domain.model.ExecutionRecord;
import com.aicode.framework.platform.domain.model.ExecutionStatus;
import com.aicode.framework.platform.domain.model.PlatformAgentConfig;
import com.aicode.framework.platform.domain.model.PlatformAgentDefinition;
import com.aicode.framework.platform.domain.model.PlatformApproval;
import com.aicode.framework.platform.domain.model.PlatformRole;
import com.aicode.framework.platform.domain.model.PlatformRunOutput;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.port.ExecutionRecordPort;
import com.aicode.framework.platform.domain.service.PlatformAgentRunner;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryAgentRegistryAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryAuditLogAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryExecutionRecordAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformRoleAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformUserAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 流式执行编排测试（Week 31）：事件顺序、记录只落一次、待审、失败、取消、预检拒绝。
 */
@ExtendWith(MockitoExtension.class)
class PlatformExecutionStreamTest {

    @Mock
    private PlatformAgentRunner platformAgentRunner;

    @Mock
    private PlatformApprovalUseCase approvalUseCase;

    private InMemoryAgentRegistryAdapter agentRegistry;
    private InMemoryExecutionRecordAdapter executionRecords;
    private ExecutionRecordPort executionRecordPort;
    private PlatformExecutionUseCase useCase;

    @BeforeEach
    void setUp() {
        agentRegistry = new InMemoryAgentRegistryAdapter();
        executionRecords = new InMemoryExecutionRecordAdapter();
        executionRecordPort = spy(executionRecords);
        InMemoryPlatformUserAdapter users = new InMemoryPlatformUserAdapter();
        InMemoryPlatformRoleAdapter roles = new InMemoryPlatformRoleAdapter();
        PlatformPermissionChecker checker = new PlatformPermissionChecker(users, roles);
        DefaultGuardrailAdapter guardrail = new DefaultGuardrailAdapter(
                new GuardrailProperties(true, 8192, true, true, List.of("patientId", "task")));
        PlatformGuardrailService guardrailService = new PlatformGuardrailService(guardrail);
        useCase = new PlatformExecutionUseCase(
                agentRegistry, executionRecordPort, platformAgentRunner, checker, guardrailService,
                new ObjectMapper(), new InMemoryAuditLogAdapter(),
                new NoopObservabilityAdapter(), approvalUseCase);

        roles.save(new PlatformRole("operator", "Operator", false,
                Set.of("knowledge-qa", "medical-assistant"), Set.of(), Set.of("P001")));
        roles.save(new PlatformRole("viewer", "Viewer", false, Set.of(), Set.of(), Set.of()));
        users.save(new PlatformUser(2L, "operator", "hash", "operator"));
        users.save(new PlatformUser(3L, "viewer", "hash", "viewer"));

        agentRegistry.save(new PlatformAgentDefinition(
                "knowledge-qa", "Knowledge", "desc", AgentType.KNOWLEDGE_QA,
                PlatformAgentConfig.defaults(), Instant.now()));
        agentRegistry.save(new PlatformAgentDefinition(
                "medical-assistant", "Medical", "desc", AgentType.MEDICAL_ASSISTANT,
                PlatformAgentConfig.defaults(), Instant.now()));
    }

    @Test
    void deliversDeltasThenFinalAndPersistsCompletedOnce() {
        when(platformAgentRunner.run(any(), any(), any())).thenAnswer(invocation -> {
            ChatStreamListener listener = invocation.getArgument(2);
            listener.onDelta("答");
            listener.onDelta("案");
            return new PlatformRunOutput(Map.of("answer", "答案"), new TokenUsage(3, 2, 5), "test-model");
        });
        CollectingSink sink = new CollectingSink();

        ExecutionRecord record = useCase.executeStream(prepare(), sink);

        assertThat(sink.started).isNotNull();
        assertThat(sink.deltas).containsExactly("答案");
        assertThat(sink.completed).isNotNull();
        assertThat(sink.failed).isNull();
        assertThat(sink.completed.status()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(record.status()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(record.outputJson()).contains("答案");
        assertThat(record.usage().totalTokens()).isEqualTo(5);
        verify(executionRecordPort, times(2)).save(any(ExecutionRecord.class));
        assertThat(executionRecords.listAll()).hasSize(1);
    }

    @Test
    void pendingApprovalSendsFinalWithPendingRecord() {
        when(platformAgentRunner.run(any(), any(), any())).thenReturn(new PlatformRunOutput(
                Map.of("report", "高风险报告", "riskLevel", "HIGH", "task", "分析"),
                new TokenUsage(6, 7, 13), "test-model", true));
        when(approvalUseCase.create(anyString(), anyLong(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new PlatformApproval(
                        "approval-1", "execution-x", "default", 2L, "knowledge-qa", "P002", "HIGH",
                        "分析", ApprovalStatus.PENDING, "", null, Instant.now(), null));
        CollectingSink sink = new CollectingSink();

        ExecutionRecord record = useCase.executeStream(prepare(), sink);

        assertThat(record.status()).isEqualTo(ExecutionStatus.PENDING_APPROVAL);
        assertThat(sink.completed).isNotNull();
        assertThat(sink.completed.status()).isEqualTo(ExecutionStatus.PENDING_APPROVAL);
        assertThat(sink.completed.outputJson()).contains("approval-1");
        assertThat(sink.failed).isNull();
    }

    @Test
    void failureMarksFailedAndNotifiesSink() {
        when(platformAgentRunner.run(any(), any(), any()))
                .thenThrow(new ChatModelException("upstream 502"));
        CollectingSink sink = new CollectingSink();

        assertThatThrownBy(() -> useCase.executeStream(prepare(), sink))
                .isInstanceOf(ChatModelException.class);

        assertThat(sink.failed).isNotNull();
        assertThat(sink.failed.status()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(sink.completed).isNull();
        assertThat(executionRecords.listAll()).singleElement()
                .extracting(ExecutionRecord::status).isEqualTo(ExecutionStatus.FAILED);
    }

    @Test
    void cancellationMarksCancelledAndNeverCompletes() {
        when(platformAgentRunner.run(any(), any(), any())).thenAnswer(invocation -> {
            ChatStreamListener listener = invocation.getArgument(2);
            listener.onDelta("半");
            throw new StreamCancelledException("client gone");
        });
        CollectingSink sink = new CollectingSink();

        ExecutionRecord record = useCase.executeStream(prepare(), sink);

        assertThat(record.status()).isEqualTo(ExecutionStatus.CANCELLED);
        assertThat(sink.cancelled).isNotNull();
        assertThat(sink.completed).isNull();
        assertThat(sink.failed).isNull();
        assertThat(executionRecords.listAll()).singleElement()
                .extracting(ExecutionRecord::status).isEqualTo(ExecutionStatus.CANCELLED);
    }

    @Test
    void prepareStreamRejectsUnsupportedAgentWithoutCreatingRecord() {
        assertThatThrownBy(() -> useCase.prepareStream(
                2L, "medical-assistant", Map.of("patientId", "P001")))
                .isInstanceOf(InvalidChatRequestException.class)
                .hasMessageContaining("does not support streaming");

        assertThat(executionRecords.listAll()).isEmpty();
    }

    @Test
    void prepareStreamRejectsUnauthorizedUser() {
        assertThatThrownBy(() -> useCase.prepareStream(
                3L, "knowledge-qa", Map.of("knowledgeBaseId", 9L, "question", "q")))
                .isInstanceOf(PlatformAccessDeniedException.class);

        assertThat(executionRecords.listAll()).isEmpty();
    }

    private PreparedExecution prepare() {
        return useCase.prepareStream(2L, "knowledge-qa",
                Map.of("knowledgeBaseId", 9L, "question", "糖尿病患者随访频率？"));
    }

    /** 记录所有事件的测试出口。 */
    private static final class CollectingSink implements RunEventSink {
        private final List<String> deltas = new ArrayList<>();
        private ExecutionRecord started;
        private ExecutionRecord completed;
        private ExecutionRecord failed;
        private ExecutionRecord cancelled;

        @Override
        public void onStarted(ExecutionRecord running) {
            started = running;
        }

        @Override
        public void onDelta(String text) {
            deltas.add(text);
        }

        @Override
        public void onCompleted(ExecutionRecord terminal) {
            completed = terminal;
        }

        @Override
        public void onFailed(ExecutionRecord terminal, RuntimeException error) {
            failed = terminal;
        }

        @Override
        public void onCancelled(ExecutionRecord terminal) {
            cancelled = terminal;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isTerminal() {
            return completed != null || failed != null || cancelled != null;
        }
    }
}
