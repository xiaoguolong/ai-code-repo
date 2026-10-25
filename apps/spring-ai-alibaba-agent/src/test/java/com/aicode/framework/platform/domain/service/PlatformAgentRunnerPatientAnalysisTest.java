package com.aicode.framework.platform.domain.service;

import com.aicode.core.domain.model.TokenUsage;
import com.aicode.framework.analysis.application.PatientAnalysisUseCase;
import com.aicode.framework.analysis.domain.model.PatientAnalysisResult;
import com.aicode.framework.analysis.domain.model.PatientAnalysisStep;
import com.aicode.framework.analysis.domain.model.ReportSummary;
import com.aicode.framework.application.FrameworkAgentUseCase;
import com.aicode.framework.multiagent.application.MedicalAssistantUseCase;
import com.aicode.framework.platform.domain.model.AgentType;
import com.aicode.framework.platform.domain.model.PlatformAgentConfig;
import com.aicode.framework.platform.domain.model.PlatformAgentDefinition;
import com.aicode.framework.platform.domain.model.PlatformMemoryScope;
import com.aicode.framework.platform.domain.model.PlatformRunOutput;
import com.aicode.framework.platform.domain.port.PlatformRunScopePort;
import com.aicode.framework.workflow.application.PatientRiskUseCase;
import com.aicode.framework.workflow.domain.model.HealthMetrics;
import com.aicode.framework.workflow.domain.model.PatientProfile;
import com.aicode.framework.workflow.domain.model.RiskLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 平台调度器新增的 PATIENT_ANALYSIS 分支：把结果映射成平台执行输出。 */
@ExtendWith(MockitoExtension.class)
class PlatformAgentRunnerPatientAnalysisTest {

    @Mock
    private FrameworkAgentUseCase frameworkAgentUseCase;

    @Mock
    private PatientRiskUseCase patientRiskUseCase;

    @Mock
    private MedicalAssistantUseCase medicalAssistantUseCase;

    @Mock
    private PatientAnalysisUseCase patientAnalysisUseCase;

    @Mock
    private PlatformRunScopePort runScopePort;

    @Test
    void dispatchesPatientAnalysisWithTenantScopeAndMapsOutput() {
        PlatformMemoryScope scope = new PlatformMemoryScope("default", 2L);
        when(runScopePort.currentScope()).thenReturn(Optional.of(scope));
        when(patientAnalysisUseCase.run("P001", "生成报告", scope)).thenReturn(result("m-1"));

        PlatformRunOutput output = runner().run(agent(), Map.of("patientId", "P001", "task", "生成报告"));

        assertThat(output.usage().totalTokens()).isEqualTo(800);
        assertThat(output.model()).isEqualTo("deepseek-chat");
        assertThat(output.output())
                .containsEntry("patientId", "P001")
                .containsEntry("riskLevel", "MEDIUM")
                .containsEntry("report", "报告正文")
                .containsEntry("degraded", false)
                .containsEntry("memoryId", "m-1")
                .containsEntry("reportCount", 1)
                .containsEntry("totalSteps", 5);
        verify(patientAnalysisUseCase).run("P001", "生成报告", scope);
    }

    @Test
    void worksWithoutRunScopeByPassingNullScope() {
        when(runScopePort.currentScope()).thenReturn(Optional.empty());
        when(patientAnalysisUseCase.run("P001", null, null)).thenReturn(result(null));

        PlatformRunOutput output = runner().run(agent(), Map.of("patientId", "P001"));

        assertThat(output.output()).containsEntry("memoryId", null);
        assertThat(output.pendingApproval()).isFalse();
    }

    @Test
    void marksHighRiskAsPendingApprovalWhenScopePresent() {
        PlatformMemoryScope scope = new PlatformMemoryScope("default", 2L);
        when(runScopePort.currentScope()).thenReturn(Optional.of(scope));
        when(patientAnalysisUseCase.run("P002", "生成报告", scope))
                .thenReturn(result("P002", null, RiskLevel.HIGH));

        PlatformRunOutput output = runner().run(agent(), Map.of("patientId", "P002", "task", "生成报告"));

        assertThat(output.pendingApproval()).isTrue();
        assertThat(output.output())
                .containsEntry("patientId", "P002")
                .containsEntry("riskLevel", "HIGH")
                .containsEntry("task", "生成报告")
                .containsEntry("memoryId", null);
    }

    private PlatformAgentRunner runner() {
        return new PlatformAgentRunner(
                frameworkAgentUseCase, patientRiskUseCase, medicalAssistantUseCase,
                patientAnalysisUseCase, runScopePort);
    }

    private PlatformAgentDefinition agent() {
        return new PlatformAgentDefinition("patient-analysis", "患者分析", "Week 21",
                AgentType.PATIENT_ANALYSIS, PlatformAgentConfig.defaults(), Instant.parse("2026-10-24T00:00:00Z"));
    }

    private PatientAnalysisResult result(String memoryId) {
        return result("P001", memoryId, RiskLevel.MEDIUM);
    }

    private PatientAnalysisResult result(String patientId, String memoryId, RiskLevel riskLevel) {
        return new PatientAnalysisResult(
                patientId,
                new PatientProfile(patientId, "张三", 62, "male", "2 型糖尿病"),
                new HealthMetrics(148, 92, 8.6, 7.9),
                List.of(new ReportSummary("R001", "糖化血红蛋白", "2026-09-12", "HbA1c 8.1%", true)),
                riskLevel,
                "收缩压 148 达到 140 阈值",
                "报告正文",
                false,
                memoryId,
                List.of(
                        new PatientAnalysisStep(1, "patient_lookup", "loaded patient"),
                        new PatientAnalysisStep(2, "report_lookup", "loaded reports"),
                        new PatientAnalysisStep(3, "metric_lookup", "loaded metrics"),
                        new PatientAnalysisStep(4, "risk_analysis", "risk=" + riskLevel.name()),
                        new PatientAnalysisStep(5, "report_generation", "generated report")),
                new TokenUsage(400, 400, 800),
                "deepseek-chat");
    }
}
