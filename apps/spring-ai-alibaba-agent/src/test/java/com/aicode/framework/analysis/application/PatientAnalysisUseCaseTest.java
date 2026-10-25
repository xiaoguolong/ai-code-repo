package com.aicode.framework.analysis.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.core.domain.model.TokenUsage;
import com.aicode.framework.analysis.domain.PatientAnalysisAgent;
import com.aicode.framework.analysis.domain.model.PatientAnalysisResult;
import com.aicode.framework.analysis.domain.model.PatientAnalysisStep;
import com.aicode.framework.analysis.domain.model.ReportSummary;
import com.aicode.framework.platform.application.PlatformMemoryUseCase;
import com.aicode.framework.platform.domain.model.PlatformMemoryRecord;
import com.aicode.framework.platform.domain.model.PlatformMemoryScope;
import com.aicode.framework.workflow.domain.model.HealthMetrics;
import com.aicode.framework.workflow.domain.model.PatientProfile;
import com.aicode.framework.workflow.domain.model.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 患者分析用例：入参校验、记忆写入（有 scope 才写）、记忆失败不影响主结果。
 */
@ExtendWith(MockitoExtension.class)
class PatientAnalysisUseCaseTest {

    private static final PlatformMemoryScope SCOPE = new PlatformMemoryScope("default", 2L);

    @Mock
    private PatientAnalysisAgent agent;

    @Mock
    private PlatformMemoryUseCase memoryUseCase;

    private PatientAnalysisUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new PatientAnalysisUseCase(agent, memoryUseCase);
    }

    @Test
    void rejectsBlankPatientId() {
        assertThatThrownBy(() -> useCase.run("  ", null, SCOPE))
                .isInstanceOf(InvalidChatRequestException.class);
        verify(agent, never()).run(anyString(), anyString());
    }

    @Test
    void writesMemoryWhenScopeProvidedAndReturnsMemoryId() {
        when(agent.run("P001", "生成报告")).thenReturn(result());
        when(memoryUseCase.save(eq(SCOPE), eq("P001"), eq("生成报告"), eq("报告正文")))
                .thenReturn(new PlatformMemoryRecord("m-1", "default", 2L, "P001",
                        "生成报告", "报告正文", Instant.parse("2026-10-24T00:00:00Z")));

        PatientAnalysisResult result = useCase.run("P001", "生成报告", SCOPE);

        assertThat(result.memoryId()).isEqualTo("m-1");
        assertThat(result.report()).isEqualTo("报告正文");
    }

    @Test
    void skipsMemoryWhenScopeMissing() {
        when(agent.run("P001", "生成报告")).thenReturn(result());

        PatientAnalysisResult result = useCase.run("P001", "生成报告", null);

        assertThat(result.memoryId()).isNull();
        verify(memoryUseCase, never()).save(any(), anyString(), anyString(), anyString());
    }

    @Test
    void memoryFailureDoesNotFailTheRun() {
        when(agent.run("P001", "生成报告")).thenReturn(result());
        when(memoryUseCase.save(any(), anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("db down"));

        PatientAnalysisResult result = useCase.run("P001", "生成报告", SCOPE);

        assertThat(result.memoryId()).isNull();
        assertThat(result.report()).isEqualTo("报告正文");
    }

    @Test
    void highRiskSkipsMemoryAndRequiresApproval() {
        when(agent.run("P002", "生成报告")).thenReturn(result(RiskLevel.HIGH));

        PatientAnalysisResult result = useCase.run("P002", "生成报告", SCOPE);

        assertThat(result.requiresApproval()).isTrue();
        assertThat(result.memoryId()).isNull();
        assertThat(result.report()).isEqualTo("报告正文");
        verify(memoryUseCase, never()).save(any(), anyString(), anyString(), anyString());
    }

    private PatientAnalysisResult result() {
        return result(RiskLevel.MEDIUM);
    }

    private PatientAnalysisResult result(RiskLevel riskLevel) {
        return new PatientAnalysisResult(
                "P001",
                new PatientProfile("P001", "张三", 62, "male", "2 型糖尿病"),
                new HealthMetrics(148, 92, 8.6, 7.9),
                List.of(new ReportSummary("R001", "糖化血红蛋白", "2026-09-12", "HbA1c 8.1%", true)),
                riskLevel,
                "收缩压 148 达到 140 阈值",
                "报告正文",
                false,
                null,
                List.of(new PatientAnalysisStep(1, "patient_lookup", "loaded")),
                new TokenUsage(1, 2, 3),
                "test-model");
    }
}
