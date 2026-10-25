package com.aicode.framework.platform.controller;

import com.aicode.core.domain.model.TokenUsage;
import com.aicode.framework.operations.domain.model.OperationsDailyReport;
import com.aicode.framework.operations.domain.model.OperationsSummary;
import com.aicode.framework.operations.infrastructure.OperationsAnalysisAgent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 运营分析 HTTP 契约（Week 24，jdbc + 完整拦截器链）。
 *
 * <p>Agent 用 {@code @MockitoBean} 固定日报，验证的是「HTTP → RBAC → 执行记录落库 → 输出映射」
 * 这一段；统计规则本身在 {@code OperationsAnalysisAgentTest} / {@code OperationsStatisticsTest} 覆盖。</p>
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
        "langfuse.enabled=false"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PlatformOperationsHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OperationsAnalysisAgent operationsAnalysisAgent;

    @BeforeEach
    void stubReport() {
        Instant end = Instant.parse("2026-10-25T09:00:00Z");
        OperationsSummary summary = new OperationsSummary(
                3, 2, 1, 0, 0, 0, 100, 50, 0.0, true, false);
        when(operationsAnalysisAgent.run(any(), any())).thenReturn(new OperationsDailyReport(
                end.minusSeconds(7 * 86400L), end, 7, summary, List.of(), List.of(),
                "## 运营概览\n运行正常", false, TokenUsage.unknown(), "test-model"));
    }

    @Test
    void adminRunsDailyReportAndExecutionRecordIsCompleted() throws Exception {
        String token = login("admin", "admin123");

        MvcResult result = mockMvc.perform(post("/api/v1/platform/agents/operations-analysis/runs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":{\"windowDays\":7}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.agentType").value("OPERATIONS_ANALYSIS"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.tenantKey").value("default"))
                .andExpect(jsonPath("$.data.outputJson").value(containsString("\"reportType\":\"DAILY\"")))
                .andExpect(jsonPath("$.data.outputJson").value(containsString("\"totalRuns\":3")))
                .andExpect(jsonPath("$.data.outputJson").value(containsString("运营概览")))
                .andReturn();

        String executionId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("executionId").asText();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/platform/executions/" + executionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    void operatorCannotRunTenantWideOperationsReport() throws Exception {
        String token = login("operator", "operator123");

        mockMvc.perform(post("/api/v1/platform/agents/operations-analysis/runs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":{\"windowDays\":7}}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void invalidWindowDaysReturns400() throws Exception {
        String token = login("admin", "admin123");

        mockMvc.perform(post("/api/v1/platform/agents/operations-analysis/runs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":{\"windowDays\":0}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/platform/agents/operations-analysis/runs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":{\"windowDays\":31}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresLogin() throws Exception {
        mockMvc.perform(post("/api/v1/platform/agents/operations-analysis/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":{}}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
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
