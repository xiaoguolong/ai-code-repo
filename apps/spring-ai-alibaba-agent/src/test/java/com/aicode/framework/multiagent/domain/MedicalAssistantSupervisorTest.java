package com.aicode.framework.multiagent.domain;

import com.aicode.framework.multiagent.domain.model.SupervisorRoute;
import com.aicode.framework.workflow.domain.model.HealthMetrics;
import com.aicode.framework.workflow.domain.model.PatientProfile;
import com.aicode.framework.workflow.domain.model.RiskLevel;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Supervisor 路由规划测试。 */
class MedicalAssistantSupervisorTest {

    private final MedicalAssistantSupervisor supervisor = new MedicalAssistantSupervisor();

    @Test
    void routesToDataWhenPatientOrMetricsMissing() {
        OverAllState empty = state();
        assertThat(supervisor.planNext(empty)).isEqualTo(SupervisorRoute.DATA);

        OverAllState onlyPatient = state(Map.of(
                MedicalAssistantSupervisor.KEY_PATIENT,
                new PatientProfile("P001", "张三", 62, "male", "2 型糖尿病")));
        assertThat(supervisor.planNext(onlyPatient)).isEqualTo(SupervisorRoute.DATA);
    }

    @Test
    void routesToAnalysisWhenRiskMissing() {
        OverAllState state = state(Map.of(
                MedicalAssistantSupervisor.KEY_PATIENT,
                new PatientProfile("P001", "张三", 62, "male", "2 型糖尿病"),
                MedicalAssistantSupervisor.KEY_METRICS,
                new HealthMetrics(148, 92, 8.6, 7.9)));

        assertThat(supervisor.planNext(state)).isEqualTo(SupervisorRoute.ANALYSIS);
    }

    @Test
    void routesToReportWhenReportBlank() {
        OverAllState state = state(Map.of(
                MedicalAssistantSupervisor.KEY_PATIENT,
                new PatientProfile("P001", "张三", 62, "male", "2 型糖尿病"),
                MedicalAssistantSupervisor.KEY_METRICS,
                new HealthMetrics(148, 92, 8.6, 7.9),
                MedicalAssistantSupervisor.KEY_RISK_LEVEL, RiskLevel.MEDIUM,
                MedicalAssistantSupervisor.KEY_REPORT, ""));

        assertThat(supervisor.planNext(state)).isEqualTo(SupervisorRoute.REPORT);
    }

    @Test
    void routesToFollowupWhenFollowUpBlank() {
        OverAllState state = state(Map.of(
                MedicalAssistantSupervisor.KEY_PATIENT,
                new PatientProfile("P001", "张三", 62, "male", "2 型糖尿病"),
                MedicalAssistantSupervisor.KEY_METRICS,
                new HealthMetrics(148, 92, 8.6, 7.9),
                MedicalAssistantSupervisor.KEY_RISK_LEVEL, RiskLevel.MEDIUM,
                MedicalAssistantSupervisor.KEY_REPORT, "报告内容",
                MedicalAssistantSupervisor.KEY_FOLLOW_UP_PLAN, ""));

        assertThat(supervisor.planNext(state)).isEqualTo(SupervisorRoute.FOLLOWUP);
    }

    @Test
    void routesToFinishWhenAllPresent() {
        OverAllState state = state(Map.of(
                MedicalAssistantSupervisor.KEY_PATIENT,
                new PatientProfile("P001", "张三", 62, "male", "2 型糖尿病"),
                MedicalAssistantSupervisor.KEY_METRICS,
                new HealthMetrics(148, 92, 8.6, 7.9),
                MedicalAssistantSupervisor.KEY_RISK_LEVEL, RiskLevel.MEDIUM,
                MedicalAssistantSupervisor.KEY_REPORT, "报告内容",
                MedicalAssistantSupervisor.KEY_FOLLOW_UP_PLAN, "随访计划"));

        assertThat(supervisor.planNext(state)).isEqualTo(SupervisorRoute.FINISH);
    }

    private OverAllState state() {
        return state(Map.of());
    }

    private OverAllState state(Map<String, Object> values) {
        OverAllState state = new OverAllState();
        state.registerKeyAndStrategy(MedicalAssistantSupervisor.KEY_PATIENT, new ReplaceStrategy());
        state.registerKeyAndStrategy(MedicalAssistantSupervisor.KEY_METRICS, new ReplaceStrategy());
        state.registerKeyAndStrategy(MedicalAssistantSupervisor.KEY_RISK_LEVEL, new ReplaceStrategy());
        state.registerKeyAndStrategy(MedicalAssistantSupervisor.KEY_REPORT, new ReplaceStrategy());
        state.registerKeyAndStrategy(MedicalAssistantSupervisor.KEY_FOLLOW_UP_PLAN, new ReplaceStrategy());
        state.updateState(values);
        return state;
    }
}
