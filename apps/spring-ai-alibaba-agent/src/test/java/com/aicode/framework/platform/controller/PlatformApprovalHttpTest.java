package com.aicode.framework.platform.controller;

import com.aicode.core.domain.model.TokenUsage;
import com.aicode.core.domain.port.PasswordHasher;
import com.aicode.framework.platform.domain.model.PlatformRunOutput;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.port.PlatformUserPort;
import com.aicode.framework.platform.domain.service.PlatformAgentRunner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 患者分析 HITL HTTP 契约（Week 23，jdbc + 完整拦截器链）。
 *
 * <p>用 {@code @MockitoBean PlatformAgentRunner} 固定 HIGH 风险输出，避免真机模型依赖；
 * 审批、记忆、执行记录都走真实用例与落库，验证「待审 → 批准 → 写记忆 → COMPLETED」全链路。</p>
 */
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-key",
        "framework.model-provider=spring-ai",
        "auth.password-salt=test-salt",
        "platform.security.enforce-direct-runs=false",
        "platform.security.web-interceptors.enabled=true",
        "platform.persistence.mode=jdbc",
        "platform.memory.lifecycle.cleanup-enabled=false",
        "platform.approval.reminder.enabled=false",
        "observability.enabled=false",
        "langfuse.enabled=false"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PlatformApprovalHttpTest {

    private static final long HOSPITAL_B_USER_ID = 104L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlatformUserPort userPort;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private PlatformAgentRunner platformAgentRunner;

    @BeforeEach
    void stubHighRiskRunner() {
        userPort.save(new PlatformUser(HOSPITAL_B_USER_ID, "doctor_b",
                passwordHasher.hash("doctor123", "doctor_b"), "operator", "hospital-b"));
        when(platformAgentRunner.run(any(), any())).thenReturn(new PlatformRunOutput(
                Map.of(
                        "patientId", "P002",
                        "riskLevel", "HIGH",
                        "report", "报告正文",
                        "task", "为患者生成慢病风险分析报告"),
                new TokenUsage(10, 20, 30),
                "deepseek-chat",
                true));
    }

    @Test
    void highRiskRunReturnsPendingApprovalAndApprovalListContainsIt() throws Exception {
        String token = login("operator", "operator123");

        JsonNode run = runHighRisk(token);
        assertThat(run.path("status").asText()).isEqualTo("PENDING_APPROVAL");
        assertThat(run.path("finishedAt").isNull()).isTrue();
        String approvalId = objectMapper.readTree(run.path("outputJson").asText())
                .path("approvalId").asText();
        assertThat(approvalId).isNotBlank();

        MvcResult list = mockMvc.perform(get("/api/v1/platform/approvals?status=PENDING")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andReturn();
        JsonNode items = objectMapper.readTree(list.getResponse().getContentAsString())
                .path("data").path("items");
        assertThat(items.findValuesAsText("approvalId")).contains(approvalId);

        mockMvc.perform(get("/api/v1/platform/approvals/" + approvalId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.patientId").value("P002"));
    }

    @Test
    void approveWritesMemoryAndCompletesExecution() throws Exception {
        String token = login("operator", "operator123");
        JsonNode run = runHighRisk(token);
        String approvalId = objectMapper.readTree(run.path("outputJson").asText())
                .path("approvalId").asText();

        MvcResult decision = mockMvc.perform(post("/api/v1/platform/approvals/" + approvalId + "/decision")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true,\"comment\":\"同意进入随访\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.executionStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.memoryId").isNotEmpty())
                .andExpect(jsonPath("$.data.followUpTaskIds", org.hamcrest.Matchers.hasSize(3)))
                .andReturn();
        String memoryId = objectMapper.readTree(decision.getResponse().getContentAsString())
                .path("data").path("memoryId").asText();

        mockMvc.perform(get("/api/v1/platform/executions/" + run.path("executionId").asText())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.outputJson")
                        .value(org.hamcrest.Matchers.containsString(memoryId)));

        mockMvc.perform(get("/api/v1/platform/memories?patientId=P002&limit=20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.memoryId=='" + memoryId + "')]").exists())
                .andExpect(jsonPath("$.data.items[?(@.patientId=='P002')]").exists());
    }

    @Test
    void rejectMarksExecutionRejectedWithoutMemory() throws Exception {
        String token = login("operator", "operator123");
        JsonNode run = runHighRisk(token);
        String approvalId = objectMapper.readTree(run.path("outputJson").asText())
                .path("approvalId").asText();

        mockMvc.perform(post("/api/v1/platform/approvals/" + approvalId + "/decision")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":false,\"comment\":\"指标需复测\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.executionStatus").value("REJECTED"))
                .andExpect(jsonPath("$.data.memoryId").value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(get("/api/v1/platform/executions/" + run.path("executionId").asText())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    void duplicateDecisionReturns409() throws Exception {
        String token = login("operator", "operator123");
        JsonNode run = runHighRisk(token);
        String approvalId = objectMapper.readTree(run.path("outputJson").asText())
                .path("approvalId").asText();

        mockMvc.perform(post("/api/v1/platform/approvals/" + approvalId + "/decision")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/platform/approvals/" + approvalId + "/decision")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLATFORM_CONFLICT"));
    }

    @Test
    void nonOwnerCannotReadOrDecideApproval() throws Exception {
        String ownerToken = login("operator", "operator123");
        JsonNode run = runHighRisk(ownerToken);
        String approvalId = objectMapper.readTree(run.path("outputJson").asText())
                .path("approvalId").asText();
        String otherToken = login("doctor_b", "doctor123");

        mockMvc.perform(get("/api/v1/platform/approvals/" + approvalId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/v1/platform/approvals/" + approvalId + "/decision")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void remindersEndpointListsOverduePendingApprovalWithoutDecidingIt() throws Exception {
        String token = login("operator", "operator123");
        JsonNode run = runHighRisk(token);
        String approvalId = objectMapper.readTree(run.path("outputJson").asText())
                .path("approvalId").asText();
        backdate(approvalId, 30);

        mockMvc.perform(get("/api/v1/platform/approvals/reminders?limit=20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.thresholdHours").value(24))
                .andExpect(jsonPath("$.data.items[?(@.approvalId=='" + approvalId + "')]").exists())
                .andExpect(jsonPath("$.data.items[0].overdueHours")
                        .value(org.hamcrest.Matchers.greaterThanOrEqualTo(29)));

        // 提醒只读：审批仍是待审（decidedBy/decidedAt 未变由用例单测覆盖，响应体不暴露 decidedBy）
        mockMvc.perform(get("/api/v1/platform/approvals/" + approvalId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void adminRemindersIncludeOtherUsersOverdueApproval() throws Exception {
        String operatorToken = login("operator", "operator123");
        JsonNode run = runHighRisk(operatorToken);
        String approvalId = objectMapper.readTree(run.path("outputJson").asText())
                .path("approvalId").asText();
        backdate(approvalId, 48);
        String adminToken = login("admin", "admin123");

        mockMvc.perform(get("/api/v1/platform/approvals/reminders")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.approvalId=='" + approvalId + "')]").exists());
    }

    @Test
    void invalidStatusReturns400() throws Exception {
        String token = login("operator", "operator123");

        mockMvc.perform(get("/api/v1/platform/approvals?status=NOPE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private void backdate(String approvalId, long hours) {
        jdbcTemplate.update(
                "UPDATE platform_approval SET created_at = ? WHERE approval_id = ?",
                Timestamp.from(Instant.now().minus(hours, ChronoUnit.HOURS)), approvalId);
    }

    private JsonNode runHighRisk(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/platform/agents/patient-analysis/runs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":{\"patientId\":\"P002\",\"task\":\"为患者生成慢病风险分析报告\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
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
