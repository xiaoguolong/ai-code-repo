package com.aicode.framework.workflow.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.core.domain.model.TokenUsage;
import com.aicode.framework.workflow.domain.PatientRiskWorkflow;
import com.aicode.framework.workflow.domain.model.PatientRiskWorkflowResult;
import com.aicode.framework.workflow.domain.model.RiskLevel;
import com.aicode.framework.workflow.domain.model.WorkflowStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 患者风险分析用例测试：校验与委派。 */
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
        when(workflow.start(anyString(), anyString()))
                .thenReturn(result(WorkflowStatus.COMPLETED));
        PatientRiskUseCase useCase = new PatientRiskUseCase(workflow);

        PatientRiskWorkflowResult response = useCase.run("  P001  ");

        assertThat(response.status()).isEqualTo(WorkflowStatus.COMPLETED);
        verify(workflow).start(anyString(), org.mockito.ArgumentMatchers.eq("P001"));
    }

    @Test
    void delegatesResumeToWorkflow() {
        when(workflow.resume("wf-1", true)).thenReturn(result(WorkflowStatus.COMPLETED));
        PatientRiskUseCase useCase = new PatientRiskUseCase(workflow);

        PatientRiskWorkflowResult response = useCase.resume(" wf-1 ", true);

        assertThat(response.status()).isEqualTo(WorkflowStatus.COMPLETED);
        verify(workflow).resume("wf-1", true);
    }

    @Test
    void rejectsBlankWorkflowIdOnResume() {
        PatientRiskUseCase useCase = new PatientRiskUseCase(workflow);

        assertThatThrownBy(() -> useCase.resume("  ", true))
                .isInstanceOf(InvalidChatRequestException.class);
        verify(workflow, org.mockito.Mockito.never()).resume(anyString(), anyBoolean());
    }

    private PatientRiskWorkflowResult result(WorkflowStatus status) {
        return new PatientRiskWorkflowResult(
                "wf-1", status, "P001", null, null, RiskLevel.MEDIUM, "", false, "报告",
                TokenUsage.unknown(), "test-model");
    }
}
