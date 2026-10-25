package com.aicode.framework.platform.controller;

import com.aicode.core.domain.model.TokenUsage;
import com.aicode.core.domain.port.PasswordHasher;
import com.aicode.framework.followup.domain.model.FollowUpPlanItem;
import com.aicode.framework.followup.domain.model.FollowUpPlanResult;
import com.aicode.framework.followup.domain.model.FollowUpPriority;
import com.aicode.framework.followup.infrastructure.FollowUpAgent;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.port.PlatformUserPort;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 随访任务 HTTP 契约（Week 24，jdbc + 完整拦截器链）。
 *
 * <p>Agent 用 {@code @MockitoBean} 固定计划（3 条 HIGH 任务），验证
 * 「运行 → 落库 → 列表 → 提醒 → 完成/重复完成 409 → 越权 403」这一段；
 * 规则与状态机在 {@code FollowUpPlannerTest} / {@code FollowUpTaskUseCaseTest} 覆盖。</p>
 */
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-key",
        "framework.model-provider=spring-ai",
        "auth.password-salt=test-salt",
        "platform.security.enforce-direct-runs=false",
        "platform.security.web-interceptors.enabled=true",
        "platform.persistence.mode=jdbc",
        "platform.memory.lifecycle.cleanup-enabled=false",
        "platform.approval.reconciliation.enabled=false",
        "observability.enabled=false",
        "langfuse.enabled=false"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PlatformFollowUpHttpTest {

    private static final String PATIENT_ID = "P007";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlatformUserPort userPort;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @MockitoBean
    private FollowUpAgent followUpAgent;

    @BeforeEach
    void stubPlan() {
        // 同一上下文/同一个 H2 库在类内多个用例间复用：先清掉本类患者的历史任务，
        // 否则「每次运行都生成 3 条」会让断言累计成 6/9/12 条。
        jdbcTemplate.update("DELETE FROM platform_followup_task WHERE patient_id = ?", PATIENT_ID);
        Instant now = Instant.now();
        List<FollowUpPlanItem> items = List.of(
                new FollowUpPlanItem("复测血压与症状", "3 天后复测", FollowUpPriority.HIGH,
                        now.plus(3, ChronoUnit.DAYS)),
                new FollowUpPlanItem("复查 HbA1c/空腹血糖", "14 天后复查", FollowUpPriority.HIGH,
                        now.plus(14, ChronoUnit.DAYS)),
                new FollowUpPlanItem("门诊复诊", "30 天后复诊", FollowUpPriority.HIGH,
                        now.plus(30, ChronoUnit.DAYS)));
        when(followUpAgent.run(eq(PATIENT_ID), any())).thenReturn(new FollowUpPlanResult(
                PATIENT_ID, "HIGH", "## 随访计划\n3 天后复测血压", items,
                false, new TokenUsage(10, 20, 30), "test-model", List.of()));
    }

    @Test
    void runsAgentAndExposesTaskListAndReminders() throws Exception {
        String token = login("operator", "operator123");

        JsonNode run = runFollowUp(token);
        assertThat(run.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(run.path("agentType").asText()).isEqualTo("FOLLOW_UP");
        JsonNode output = objectMapper.readTree(run.path("outputJson").asText());
        assertThat(output.path("taskIds")).hasSize(3);
        assertThat(output.path("tasks")).hasSize(3);
        assertThat(output.path("riskLevel").asText()).isEqualTo("HIGH");
        assertThat(output.path("planText").asText()).contains("随访计划");
        String taskId = output.path("taskIds").get(0).asText();

        mockMvc.perform(get("/api/v1/platform/followup-tasks?status=PENDING&patientId=" + PATIENT_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.tasks[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.tasks[0].source").value("AGENT"));

        mockMvc.perform(get("/api/v1/platform/followup-tasks/" + taskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.patientId").value(PATIENT_ID))
                .andExpect(jsonPath("$.data.priority").value("HIGH"));

        mockMvc.perform(get("/api/v1/platform/followup-tasks/reminders?days=7")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days").value(7))
                .andExpect(jsonPath("$.data.overdue").isArray())
                .andExpect(jsonPath("$.data.dueSoon[0].taskId").isNotEmpty());
    }

    @Test
    void completesTaskAndDuplicateCompletionConflicts() throws Exception {
        String token = login("operator", "operator123");
        String taskId = firstTaskId(runFollowUp(token));

        mockMvc.perform(post("/api/v1/platform/followup-tasks/" + taskId + "/complete")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"已复测 138/86\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.note").value("已复测 138/86"));

        mockMvc.perform(post("/api/v1/platform/followup-tasks/" + taskId + "/complete")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"again\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLATFORM_CONFLICT"));

        mockMvc.perform(post("/api/v1/platform/followup-tasks/" + taskId + "/cancel")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"late\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelsTask() throws Exception {
        String token = login("operator", "operator123");
        String taskId = firstTaskId(runFollowUp(token));

        mockMvc.perform(post("/api/v1/platform/followup-tasks/" + taskId + "/cancel")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"患者已转诊\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.completedAt").doesNotExist());
    }

    @Test
    void otherTenantUserCannotReadTask() throws Exception {
        String ownerToken = login("operator", "operator123");
        String taskId = firstTaskId(runFollowUp(ownerToken));
        userPort.save(new PlatformUser(778L, "doctor_w24",
                passwordHasher.hash("doctor123", "doctor_w24"), "operator", "hospital-b"));
        String otherToken = login("doctor_w24", "doctor123");

        mockMvc.perform(get("/api/v1/platform/followup-tasks/" + taskId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/platform/followup-tasks?patientId=" + PATIENT_ID)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void viewerCannotRunFollowUpAgent() throws Exception {
        String token = login("viewer", "viewer123");

        mockMvc.perform(post("/api/v1/platform/agents/followup/runs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":{\"patientId\":\"" + PATIENT_ID + "\"}}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void invalidStatusAndRangeAreRejected() throws Exception {
        String token = login("operator", "operator123");

        mockMvc.perform(get("/api/v1/platform/followup-tasks?status=NOPE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/platform/followup-tasks/reminders?days=31")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/api/v1/platform/followup-tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void outputContainsTaskIdsForTracing() throws Exception {
        String token = login("operator", "operator123");

        MvcResult result = mockMvc.perform(post("/api/v1/platform/agents/followup/runs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":{\"patientId\":\"" + PATIENT_ID + "\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outputJson").value(containsString("\"taskIds\"")))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("P007");
    }

    private JsonNode runFollowUp(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/platform/agents/followup/runs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":{\"patientId\":\"" + PATIENT_ID + "\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private String firstTaskId(JsonNode run) throws Exception {
        return objectMapper.readTree(run.path("outputJson").asText())
                .path("taskIds").get(0).asText();
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
