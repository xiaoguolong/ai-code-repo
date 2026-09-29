package com.aicode.framework.workflow.controller;

import com.aicode.core.domain.model.TokenUsage;
import com.aicode.framework.workflow.application.PatientRiskUseCase;
import com.aicode.framework.workflow.domain.model.HealthMetrics;
import com.aicode.framework.workflow.domain.model.PatientProfile;
import com.aicode.framework.workflow.domain.model.PatientRiskWorkflowResult;
import com.aicode.framework.workflow.domain.model.RiskLevel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 患者风险分析 Workflow 控制器契约测试：只加载 Web 层，不连真实模型。
 */
@WebMvcTest(PatientRiskWorkflowController.class)
class PatientRiskWorkflowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PatientRiskUseCase patientRiskUseCase;

    @Test
    void runsWorkflowAndReturnsResult() throws Exception {
        when(patientRiskUseCase.run(anyString())).thenReturn(new PatientRiskWorkflowResult(
                "wf-1",
                "P001",
                new PatientProfile("P001", "张三", 62, "male", "2 型糖尿病"),
                new HealthMetrics(148, 92, 8.6, 7.9),
                RiskLevel.MEDIUM,
                "收缩压 148 达到 140 阈值",
                false,
                "中风险报告",
                new TokenUsage(10, 20, 30),
                "deepseek-chat"));

        mockMvc.perform(post("/api/v1/workflows/patient-risk/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":\"P001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.workflowId").value("wf-1"))
                .andExpect(jsonPath("$.data.patientId").value("P001"))
                .andExpect(jsonPath("$.data.patient.name").value("张三"))
                .andExpect(jsonPath("$.data.metrics.hba1c").value(7.9))
                .andExpect(jsonPath("$.data.riskLevel").value("MEDIUM"))
                .andExpect(jsonPath("$.data.riskLabel").value("中风险"))
                .andExpect(jsonPath("$.data.escalated").value(false))
                .andExpect(jsonPath("$.data.report").value("中风险报告"))
                .andExpect(jsonPath("$.data.model").value("deepseek-chat"))
                .andExpect(jsonPath("$.data.usage.totalTokens").value(30));
    }

    @Test
    void rejectsBlankPatientId() throws Exception {
        mockMvc.perform(post("/api/v1/workflows/patient-risk/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
