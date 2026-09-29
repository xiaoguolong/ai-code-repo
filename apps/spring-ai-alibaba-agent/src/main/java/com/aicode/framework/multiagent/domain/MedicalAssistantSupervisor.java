package com.aicode.framework.multiagent.domain;

import com.aicode.framework.multiagent.domain.model.SupervisorRoute;
import com.aicode.framework.workflow.domain.model.HealthMetrics;
import com.aicode.framework.workflow.domain.model.PatientProfile;
import com.aicode.framework.workflow.domain.model.RiskLevel;
import com.alibaba.cloud.ai.graph.OverAllState;

import java.util.Optional;

/**
 * Supervisor 路由规划：根据共享 state 缺项决定下一个 Worker。
 */
public class MedicalAssistantSupervisor {

    static final String KEY_PATIENT = "patient";
    static final String KEY_METRICS = "metrics";
    static final String KEY_RISK_LEVEL = "riskLevel";
    static final String KEY_REPORT = "report";
    static final String KEY_FOLLOW_UP_PLAN = "followUpPlan";

    /**
     * 规划下一跳路由。
     */
    public SupervisorRoute planNext(OverAllState state) {
        Optional<PatientProfile> patient = state.value(KEY_PATIENT, PatientProfile.class);
        Optional<HealthMetrics> metrics = state.value(KEY_METRICS, HealthMetrics.class);
        if (patient.isEmpty() || metrics.isEmpty()) {
            return SupervisorRoute.DATA;
        }

        Optional<RiskLevel> riskLevel = state.value(KEY_RISK_LEVEL, RiskLevel.class);
        if (riskLevel.isEmpty()) {
            return SupervisorRoute.ANALYSIS;
        }

        String report = state.value(KEY_REPORT, "");
        if (report == null || report.isBlank()) {
            return SupervisorRoute.REPORT;
        }

        String followUpPlan = state.value(KEY_FOLLOW_UP_PLAN, "");
        if (followUpPlan == null || followUpPlan.isBlank()) {
            return SupervisorRoute.FOLLOWUP;
        }

        return SupervisorRoute.FINISH;
    }
}
