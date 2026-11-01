package com.aicode.framework.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 流式开关关闭契约（Week 31）：503 + {@code STREAM_DISABLED}，前端据此回退同步端点。
 */
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-key",
        "framework.model-provider=spring-ai",
        "auth.password-salt=test-salt",
        "platform.security.enforce-direct-runs=false",
        "platform.security.web-interceptors.enabled=true",
        "platform.persistence.mode=jdbc",
        "platform.memory.lifecycle.cleanup-enabled=false",
        "observability.enabled=false",
        "langfuse.enabled=false",
        "platform.execution.stream.enabled=false"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PlatformStreamDisabledHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void disabledStreamReturns503WithErrorCode() throws Exception {
        String token = login("admin", "admin123");

        mockMvc.perform(post("/api/v1/platform/agents/knowledge-qa/runs/stream")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":{\"knowledgeBaseId\":1,\"question\":\"q\"}}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("STREAM_DISABLED"));
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/platform/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("token").asText();
    }
}
