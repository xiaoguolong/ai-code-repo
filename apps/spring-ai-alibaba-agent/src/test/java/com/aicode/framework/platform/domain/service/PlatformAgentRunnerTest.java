package com.aicode.framework.platform.domain.service;

import com.aicode.core.domain.model.TokenUsage;
import com.aicode.framework.application.FrameworkAgentUseCase;
import com.aicode.framework.domain.model.FrameworkAgentResult;
import com.aicode.framework.multiagent.application.MedicalAssistantUseCase;
import com.aicode.framework.multiagent.domain.model.MedicalAssistantResult;
import com.aicode.framework.multiagent.domain.model.MedicalAssistantStep;
import com.aicode.framework.platform.domain.model.AgentType;
import com.aicode.framework.platform.domain.model.PlatformAgentConfig;
import com.aicode.framework.platform.domain.model.PlatformAgentDefinition;
import com.aicode.framework.workflow.application.PatientRiskUseCase;
import com.aicode.framework.workflow.domain.model.HealthMetrics;
import com.aicode.framework.workflow.domain.model.PatientProfile;
import com.aicode.framework.workflow.domain.model.PatientRiskWorkflowResult;
import com.aicode.framework.workflow.domain.model.RiskLevel;
import com.aicode.framework.workflow.domain.model.WorkflowStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** PlatformAgentRunner 调度测试。 */
@ExtendWith(MockitoExtension.class)
class PlatformAgentRunnerTest {

    @Mock
    private FrameworkAgentUseCase frameworkAgentUseCase;
    @Mock
    private PatientRiskUseCase patientRiskUseCase;
    @Mock
    private MedicalAssistantUseCase medicalAssistantUseCase;

    @InjectMocks
    private PlatformAgentRunner runner;

    @Test
    void runsMedicalAssistantAgent() {
        when(medicalAssistantUseCase.run("P001", null)).thenReturn(new MedicalAssistantResult(
                "run-1", "P001", "task", null, null, RiskLevel.MEDIUM, "依据",
                "报告", "随访", List.of(new MedicalAssistantStep(1, "supervisor", "route=DATA")),
                new TokenUsage(1, 2, 3), "test-model"));

        var output = runner.run(agent(AgentType.MEDICAL_ASSISTANT), Map.of("patientId", "P001"));

        assertThat(output.output()).containsEntry("report", "报告");
        assertThat(output.usage().totalTokens()).isEqualTo(3);
    }

    @Test
    void runsPatientRiskWorkflowAgent() {
        when(patientRiskUseCase.run("P001")).thenReturn(new PatientRiskWorkflowResult(
                "wf-1", WorkflowStatus.COMPLETED, "P001",
                new PatientProfile("P001", "张三", 62, "male", "2 型糖尿病"),
                new HealthMetrics(148, 92, 8.6, 7.9),
                RiskLevel.MEDIUM, "依据", false, "报告",
                TokenUsage.unknown(), "test-model"));

        var output = runner.run(agent(AgentType.PATIENT_RISK_WORKFLOW), Map.of("patientId", "P001"));

        assertThat(output.output()).containsEntry("status", "COMPLETED");
    }

    @Test
    void runsFrameworkReactAgent() {
        when(frameworkAgentUseCase.run("hello")).thenReturn(new FrameworkAgentResult(
                "task-1", "answer", List.of(), 0, new TokenUsage(5, 5, 10), "test-model"));

        var output = runner.run(agent(AgentType.FRAMEWORK_REACT), Map.of("task", "hello"));

        assertThat(output.output()).containsEntry("answer", "answer");
    }

    private PlatformAgentDefinition agent(AgentType type) {
        return new PlatformAgentDefinition(
                "key", "name", "desc", type, PlatformAgentConfig.defaults(), Instant.now());
    }
}
