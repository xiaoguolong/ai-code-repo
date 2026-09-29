package com.aicode.framework.workflow.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.core.domain.model.TokenUsage;
import com.aicode.framework.workflow.domain.PatientRiskWorkflow;
import com.aicode.framework.workflow.domain.model.PatientRiskWorkflowResult;
import com.aicode.framework.workflow.domain.model.RiskLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 患者风险分析用例测试：patientId 校验与领域服务委派。
 */
@ExtendWith(MockitoExtension.class)
class PatientRiskUseCaseTest {

    @Mock
    private PatientRiskWorkflow workflow;

    @Test
    void rejectsBlankPatientId() {
        PatientRiskUseCase useCase = new PatientRiskUseCase(workflow);

        assertThatThrownBy(() -> useCase.run("   "))
                .isInstanceOf(InvalidChatRequestException.class);
        verifyNoInteractions(workflow);
    }

    @Test
    void delegatesTrimmedPatientIdToWorkflow() {
        when(workflow.run(anyString(), anyString()))
                .thenReturn(new PatientRiskWorkflowResult(
                        "wf-1", "P001", null, null, RiskLevel.MEDIUM, "", false, "报告",
                        TokenUsage.unknown(), "test-model"));
        PatientRiskUseCase useCase = new PatientRiskUseCase(workflow);

        PatientRiskWorkflowResult result = useCase.run("  P001  ");

        assertThat(result.patientId()).isEqualTo("P001");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
    }
}
