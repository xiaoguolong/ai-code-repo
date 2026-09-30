package com.aicode.framework.platform.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 平台强 gate 集成测试（拦截器 + Controller 双保险）。
 */
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-key",
        "framework.model-provider=spring-ai",
        "auth.password-salt=test-salt",
        "platform.security.enforce-direct-runs=false",
        "platform.security.web-interceptors.enabled=true"
})
@AutoConfigureMockMvc
class PlatformExecutionSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listExecutionsWithoutLoginReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/platform/executions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void platformRunWithoutLoginReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/platform/agents/medical-assistant/runs")
                        .contentType("application/json")
                        .content("{\"input\":{\"patientId\":\"P001\"}}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void listAgentsWithoutLoginStillPublic() throws Exception {
        mockMvc.perform(get("/api/v1/platform/agents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }
}
