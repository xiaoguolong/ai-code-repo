package com.aicode.framework.multiagent.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.framework.multiagent.domain.MedicalAssistantSupervisorGraph;
import com.aicode.framework.multiagent.domain.model.MedicalAssistantResult;
import com.aicode.framework.multiagent.domain.model.MedicalAssistantStep;
import com.aicode.framework.workflow.domain.model.HealthMetrics;
import com.aicode.framework.workflow.domain.model.PatientProfile;
import com.aicode.framework.workflow.domain.model.RiskLevel;
import com.aicode.core.domain.model.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 医疗助手用例测试。 */
@ExtendWith(MockitoExtension.class)
class MedicalAssistantUseCaseTest {

    @Mock
    private MedicalAssistantSupervisorGraph graph;

    @InjectMocks
    private MedicalAssistantUseCase useCase;

    @Test
    void runsWithDefaultTaskWhenTaskBlank() {
        when(graph.run(anyString(), eq("P001"), eq("为患者 P001 进行综合分析并生成随访计划")))
                .thenReturn(sampleResult("为患者 P001 进行综合分析并生成随访计划"));

        MedicalAssistantResult result = useCase.run("P001", "  ");

        assertThat(result.task()).contains("P001");
        verify(graph).run(anyString(), eq("P001"), eq("为患者 P001 进行综合分析并生成随访计划"));
    }

    @Test
    void runsWithCustomTask() {
        when(graph.run(anyString(), eq("P001"), eq("自定义任务"))).thenReturn(sampleResult("自定义任务"));

        MedicalAssistantResult result = useCase.run("P001", "自定义任务");

        assertThat(result.task()).isEqualTo("自定义任务");
    }

    @Test
    void rejectsBlankPatientId() {
        assertThatThrownBy(() -> useCase.run(" ", null))
                .isInstanceOf(InvalidChatRequestException.class);
    }

    private MedicalAssistantResult sampleResult(String task) {
        return new MedicalAssistantResult(
                "run-1", "P001", task,
                new PatientProfile("P001", "张三", 62, "male", "2 型糖尿病"),
                new HealthMetrics(148, 92, 8.6, 7.9),
                RiskLevel.MEDIUM, "依据", "报告", "随访",
                List.of(new MedicalAssistantStep(1, "supervisor", "route=DATA")),
                TokenUsage.unknown(), "test-model");
    }
}
