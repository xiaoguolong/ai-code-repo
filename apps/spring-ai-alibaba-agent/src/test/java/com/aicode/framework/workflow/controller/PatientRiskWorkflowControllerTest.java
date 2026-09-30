package com.aicode.framework.workflow.controller;

import com.aicode.core.domain.model.TokenUsage;
import com.aicode.framework.workflow.application.PatientRiskUseCase;
import com.aicode.framework.workflow.domain.model.HealthMetrics;
import com.aicode.framework.workflow.domain.model.PatientProfile;
import com.aicode.framework.workflow.domain.model.PatientRiskWorkflowResult;
import com.aicode.framework.workflow.domain.model.RiskLevel;
import com.aicode.framework.workflow.domain.model.WorkflowStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 患者风险 Workflow 控制器契约测试。 */
@WebMvcTest(PatientRiskWorkflowController.class)
@TestPropertySource(properties = "platform.security.web-interceptors.enabled=false")
class PatientRiskWorkflowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PatientRiskUseCase patientRiskUseCase;

    @Test
    void runsWorkflowAndReturnsCompletedResult() throws Exception {
        when(patientRiskUseCase.run(anyString())).thenReturn(completedResult());

        mockMvc.perform(post("/api/v1/workflows/patient-risk/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":\"P001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.report").value("中风险报告"));
    }

    @Test
    void returnsPendingApprovalForHighRisk() throws Exception {
        when(patientRiskUseCase.run(anyString())).thenReturn(new PatientRiskWorkflowResult(
                "wf-pending", WorkflowStatus.PENDING_APPROVAL, "P001",
                new PatientProfile("P001", "张三", 62, "male", "2 型糖尿病"),
                new HealthMetrics(162, 98, 8.6, 7.9),
                RiskLevel.HIGH, "收缩压 162 达到 160 阈值", false, "",
                TokenUsage.unknown(), "deepseek-chat"));

        mockMvc.perform(post("/api/v1/workflows/patient-risk/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":\"P001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.data.report").value(""))
                .andExpect(jsonPath("$.data.escalated").value(false));
    }

    @Test
    void resumesWorkflow() throws Exception {
        when(patientRiskUseCase.resume(anyString(), anyBoolean())).thenReturn(new PatientRiskWorkflowResult(
                "wf-1", WorkflowStatus.COMPLETED, "P001", null, null, RiskLevel.HIGH,
                "依据", true, "高风险报告", new TokenUsage(10, 20, 30), "deepseek-chat"));

        mockMvc.perform(post("/api/v1/workflows/patient-risk/runs/wf-1/resume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.escalated").value(true));
    }

    @Test
    void getsWorkflowRun() throws Exception {
        when(patientRiskUseCase.getRun("wf-1")).thenReturn(completedResult());

        mockMvc.perform(get("/api/v1/workflows/patient-risk/runs/wf-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workflowId").value("wf-1"));
    }

    @Test
    void rejectsBlankPatientId() throws Exception {
        mockMvc.perform(post("/api/v1/workflows/patient-risk/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private PatientRiskWorkflowResult completedResult() {
        return new PatientRiskWorkflowResult(
                "wf-1", WorkflowStatus.COMPLETED, "P001",
                new PatientProfile("P001", "张三", 62, "male", "2 型糖尿病"),
                new HealthMetrics(148, 92, 8.6, 7.9),
                RiskLevel.MEDIUM, "收缩压 148 达到 140 阈值", false, "中风险报告",
                new TokenUsage(10, 20, 30), "deepseek-chat");
    }
}
