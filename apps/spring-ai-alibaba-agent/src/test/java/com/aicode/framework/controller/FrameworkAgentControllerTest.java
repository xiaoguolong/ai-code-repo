package com.aicode.framework.controller;

import com.aicode.core.domain.model.TokenUsage;
import com.aicode.framework.application.FrameworkAgentUseCase;
import com.aicode.framework.domain.model.FrameworkAgentResult;
import com.aicode.framework.domain.model.FrameworkAgentStep;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Graph Agent 控制器契约测试：只加载 Web 层，不连真实模型。
 */
@WebMvcTest(FrameworkAgentController.class)
class FrameworkAgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FrameworkAgentUseCase frameworkAgentUseCase;

    @Test
    void runsAgentAndReturnsResult() throws Exception {
        when(frameworkAgentUseCase.run(anyString())).thenReturn(new FrameworkAgentResult(
                "task-1",
                "患者张三血压偏高",
                List.of(new FrameworkAgentStep(1, "HealthMetricTool", "{\"patientId\":\"P001\"}", "{\"systolic\":148}")),
                1,
                new TokenUsage(10, 20, 30),
                "deepseek-chat"));

        mockMvc.perform(post("/api/v1/framework/agents/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"task\":\"分析患者 P001 的健康指标\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.taskId").value("task-1"))
                .andExpect(jsonPath("$.data.answer").value("患者张三血压偏高"))
                .andExpect(jsonPath("$.data.totalSteps").value(1))
                .andExpect(jsonPath("$.data.model").value("deepseek-chat"))
                .andExpect(jsonPath("$.data.steps[0].toolName").value("HealthMetricTool"))
                .andExpect(jsonPath("$.data.usage.totalTokens").value(30));
    }

    @Test
    void rejectsBlankTask() throws Exception {
        mockMvc.perform(post("/api/v1/framework/agents/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"task\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
