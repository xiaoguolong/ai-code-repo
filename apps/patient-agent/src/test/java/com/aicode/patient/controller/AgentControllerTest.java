package com.aicode.patient.controller;

import com.aicode.patient.application.AgentRunUseCase;
import com.aicode.patient.domain.model.AgentResult;
import com.aicode.patient.domain.model.AgentStep;
import com.aicode.core.domain.model.TokenUsage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Agent 控制器契约测试：只加载 Web 层，不连真实模型。
 */
@WebMvcTest(AgentController.class)
class AgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AgentRunUseCase agentRunUseCase;

    @Test
    void runsAgentAndReturnsResult() throws Exception {
        when(agentRunUseCase.run(any())).thenReturn(new AgentResult(
                "task-1",
                "建议随访",
                List.of(new AgentStep(1, "PatientTool", "{\"patientId\":\"P001\"}", "{\"name\":\"张三\"}")),
                1,
                new TokenUsage(10, 20, 30),
                "deepseek-chat"
        ));

        mockMvc.perform(post("/api/v1/agents/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"task\":\"评估患者风险\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").value("task-1"))
                .andExpect(jsonPath("$.data.answer").value("建议随访"))
                .andExpect(jsonPath("$.data.totalSteps").value(1))
                .andExpect(jsonPath("$.data.steps[0].stepNo").value(1))
                .andExpect(jsonPath("$.data.steps[0].toolName").value("PatientTool"))
                .andExpect(jsonPath("$.data.usage.totalTokens").value(30));
    }

    @Test
    void rejectsBlankTask() throws Exception {
        mockMvc.perform(post("/api/v1/agents/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"task\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
