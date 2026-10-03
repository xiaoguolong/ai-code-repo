package com.aicode.framework.platform.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Eval API 强鉴权集成测试（拦截器 + Controller 双保险）。
 */
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-key",
        "framework.model-provider=spring-ai",
        "auth.password-salt=test-salt",
        "platform.security.enforce-direct-runs=false",
        "platform.security.web-interceptors.enabled=true"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PlatformEvalSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listEvalDatasetsWithoutLoginReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/platform/eval/datasets"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void getEvalDatasetWithoutLoginReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/platform/eval/datasets/guardrail-regression"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void runEvalDatasetWithoutLoginReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/platform/eval/datasets/guardrail-regression/runs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void getEvalRunWithoutLoginReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/platform/eval/runs/any-run-id"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
