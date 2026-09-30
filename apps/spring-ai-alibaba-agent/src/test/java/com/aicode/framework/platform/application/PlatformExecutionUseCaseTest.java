package com.aicode.framework.platform.application;

import com.aicode.core.domain.model.TokenUsage;
import com.aicode.framework.platform.domain.exception.PlatformAgentDisabledException;
import com.aicode.framework.platform.domain.model.AgentType;
import com.aicode.framework.platform.domain.model.ExecutionStatus;
import com.aicode.framework.platform.domain.model.PlatformAgentConfig;
import com.aicode.framework.platform.domain.model.PlatformAgentDefinition;
import com.aicode.framework.platform.domain.model.PlatformRunOutput;
import com.aicode.framework.platform.domain.service.PlatformAgentRunner;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryAgentRegistryAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryExecutionRecordAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** 平台执行记录用例测试。 */
@ExtendWith(MockitoExtension.class)
class PlatformExecutionUseCaseTest {

    @Mock
    private PlatformAgentRunner platformAgentRunner;

    private InMemoryAgentRegistryAdapter agentRegistry;
    private InMemoryExecutionRecordAdapter executionRecords;
    private PlatformExecutionUseCase useCase;

    @BeforeEach
    void setUp() {
        agentRegistry = new InMemoryAgentRegistryAdapter();
        executionRecords = new InMemoryExecutionRecordAdapter();
        useCase = new PlatformExecutionUseCase(
                agentRegistry, executionRecords, platformAgentRunner, new ObjectMapper());
        agentRegistry.save(new PlatformAgentDefinition(
                "medical-assistant", "Medical", "desc", AgentType.MEDICAL_ASSISTANT,
                PlatformAgentConfig.defaults(), Instant.now()));
    }

    @Test
    void runAgentPersistsCompletedExecutionRecord() {
        when(platformAgentRunner.run(any(), any())).thenReturn(new PlatformRunOutput(
                Map.of("report", "分析报告"),
                new TokenUsage(10, 20, 30),
                "test-model"));

        var record = useCase.runAgent("medical-assistant", Map.of("patientId", "P001"));

        assertThat(record.status()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(record.outputJson()).contains("分析报告");
        assertThat(useCase.listExecutions()).hasSize(1);
    }

    @Test
    void rejectsDisabledAgent() {
        agentRegistry.updateConfig("medical-assistant", new PlatformAgentConfig(false, null, null, null));

        assertThatThrownBy(() -> useCase.runAgent("medical-assistant", Map.of("patientId", "P001")))
                .isInstanceOf(PlatformAgentDisabledException.class);
    }
}
