package com.aicode.framework.platform.controller;

import com.aicode.core.domain.port.PasswordHasher;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 平台用户停用/恢复 HTTP 契约（Week 28，jdbc + 完整拦截器链）。
 *
 * <p>用独立用户 {@code w28-user}，不动种子账号；停用后用旧 token 访问 /auth/me 必须 401，
 * 恢复后能重新登录。</p>
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
        "platform.approval.pending-gauge.enabled=false",
        "platform.approval.reminder.enabled=false",
        "platform.knowledge.reconcile.enabled=false",
        "observability.enabled=false",
        "langfuse.enabled=false"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PlatformUserHttpTest {

    private static final long USER_ID = 106L;
    private static final String USERNAME = "w28-user";
    private static final String PASSWORD = "w28pass123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlatformUserPort userPort;

    @Autowired
    private PasswordHasher passwordHasher;

    @BeforeEach
    void ensureUser() {
        userPort.save(new PlatformUser(
                USER_ID, USERNAME, passwordHasher.hash(PASSWORD, USERNAME), "operator"));
        userPort.updateDisabledAt(USER_ID, null);
    }

    @Test
    void adminListsUsersWithDisabledState() throws Exception {
        String adminToken = login("admin", "admin123");

        mockMvc.perform(get("/api/v1/platform/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items[?(@.username=='w28-user')].disabled").value(
                        org.hamcrest.Matchers.hasItem(false)));
    }

    @Test
    void operatorCannotListOrDisableUsers() throws Exception {
        String operatorToken = login("operator", "operator123");

        mockMvc.perform(get("/api/v1/platform/users")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/v1/platform/users/" + USER_ID + "/disable")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void disableBlocksLoginAndOldTokenThenEnableRestores() throws Exception {
        String adminToken = login("admin", "admin123");
        String userToken = login(USERNAME, PASSWORD);

        mockMvc.perform(post("/api/v1/platform/users/" + USER_ID + "/disable")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.disabled").value(true))
                .andExpect(jsonPath("$.data.disabledAt").isNotEmpty());

        mockMvc.perform(post("/api/v1/platform/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/platform/auth/me")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/platform/users/" + USER_ID + "/enable")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.disabled").value(false))
                .andExpect(jsonPath("$.data.disabledAt").value(org.hamcrest.Matchers.nullValue()));

        login(USERNAME, PASSWORD);
    }

    @Test
    void cannotDisableSelfAndUnknownUserIs404() throws Exception {
        String adminToken = login("admin", "admin123");

        mockMvc.perform(post("/api/v1/platform/users/1/disable")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/platform/users/909999/disable")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLATFORM_NOT_FOUND"));
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/platform/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        return data.path("token").asText();
    }
}
