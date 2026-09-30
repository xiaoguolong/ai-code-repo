package com.aicode.framework.multiagent.controller;

import com.aicode.core.domain.model.TokenUsage;
import com.aicode.framework.multiagent.application.MedicalAssistantUseCase;
import com.aicode.framework.multiagent.domain.model.MedicalAssistantResult;
import com.aicode.framework.multiagent.domain.model.MedicalAssistantStep;
import com.aicode.framework.workflow.domain.model.HealthMetrics;
import com.aicode.framework.workflow.domain.model.PatientProfile;
import com.aicode.framework.workflow.domain.model.RiskLevel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 医疗助手 Multi Agent 控制器契约测试。 */
@WebMvcTest(MedicalAssistantController.class)
@TestPropertySource(properties = "platform.security.web-interceptors.enabled=false")
class MedicalAssistantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MedicalAssistantUseCase medicalAssistantUseCase;

    @Test
    void runsMultiAgentAndReturnsResult() throws Exception {
        when(medicalAssistantUseCase.run(anyString(), isNull())).thenReturn(completedResult());

        mockMvc.perform(post("/api/v1/multi-agent/medical-assistant/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":\"P001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.patientId").value("P001"))
                .andExpect(jsonPath("$.data.report").value("分析报告"))
                .andExpect(jsonPath("$.data.followUpPlan").value("随访计划"))
                .andExpect(jsonPath("$.data.steps[0].agentName").value("supervisor"));
    }

    @Test
    void rejectsBlankPatientId() throws Exception {
        mockMvc.perform(post("/api/v1/multi-agent/medical-assistant/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    private MedicalAssistantResult completedResult() {
        return new MedicalAssistantResult(
                "run-1", "P001", "task",
                new PatientProfile("P001", "张三", 62, "male", "2 型糖尿病"),
                new HealthMetrics(148, 92, 8.6, 7.9),
                RiskLevel.MEDIUM, "收缩压偏高", "分析报告", "随访计划",
                List.of(new MedicalAssistantStep(1, "supervisor", "route=DATA")),
                new TokenUsage(10, 20, 30), "deepseek-chat");
    }
}
