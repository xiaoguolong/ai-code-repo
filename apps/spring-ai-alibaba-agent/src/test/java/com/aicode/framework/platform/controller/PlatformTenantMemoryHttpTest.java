package com.aicode.framework.platform.controller;

import com.aicode.core.domain.port.PasswordHasher;
import com.aicode.framework.platform.domain.model.PlatformMemoryRecord;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.port.PlatformMemoryPort;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 租户与记忆 HTTP 契约（Week 21，落库模式）。
 *
 * <p>用真实 seed 用户验证三件事：管理员才能列租户、记忆按租户/用户隔离、
 * 非管理员不能借 {@code userId} 参数读别人的记忆。</p>
 *
 * <p>第二租户用户 {@code doctor_b} 在 {@code @BeforeEach} 里显式 upsert：
 * 本仓库所有 {@code @SpringBootTest} 共用同一个 H2 内存库（{@code platform_test}），
 * 别的测试类可能先启动并让 seed 提前完成，只依赖「seed 一定会建 id=104」会在
 * 测试顺序变化时随机失败（第 16 周「H2 单测全绿但换顺序就红」的同类问题）。</p>
 */
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-key",
        "framework.model-provider=spring-ai",
        "auth.password-salt=test-salt",
        "platform.security.enforce-direct-runs=false",
        "platform.security.web-interceptors.enabled=true",
        "platform.persistence.mode=jdbc",
        "langfuse.enabled=false",
        "observability.enabled=false"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PlatformTenantMemoryHttpTest {

    private static final long HOSPITAL_B_USER_ID = 104L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlatformMemoryPort memoryPort;

    @Autowired
    private PlatformUserPort userPort;

    @Autowired
    private PasswordHasher passwordHasher;

    @BeforeEach
    void ensureSecondTenantUser() {
        userPort.save(new PlatformUser(HOSPITAL_B_USER_ID, "doctor_b",
                passwordHasher.hash("doctor123", "doctor_b"), "operator", "hospital-b"));
    }

    @Test
    void tenantsRequireLogin() throws Exception {
        mockMvc.perform(get("/api/v1/platform/tenants"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    /**
     * SSO 身份源必须**免登录**可用，且不返回 token。
     *
     * <p>本用例是真机验收后补的：第 21 周把 verify 写进 controller 后忘了加 Sa-Token 白名单，
     * 真机上 enterprise 登录永远 401（凭据是对的），单测层完全没有覆盖拦截器。</p>
     */
    @Test
    void verifyIsPublicAndDoesNotIssueToken() throws Exception {
        mockMvc.perform(post("/api/v1/platform/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"operator\",\"password\":\"operator123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.userId").value(2))
                .andExpect(jsonPath("$.data.tenantKey").value("default"))
                .andExpect(jsonPath("$.data.token").doesNotExist());
    }

    @Test
    void verifyRejectsWrongPasswordWithoutLogin() throws Exception {
        mockMvc.perform(post("/api/v1/platform/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"operator\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void adminListsTenantsAndTenantFieldsAreExposed() throws Exception {
        String token = login("admin", "admin123");

        mockMvc.perform(get("/api/v1/platform/tenants").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data[?(@.tenantKey=='default')]").exists())
                .andExpect(jsonPath("$.data[?(@.tenantKey=='hospital-b')]").exists())
                .andExpect(jsonPath("$.data[0].displayName").isNotEmpty());
    }

    @Test
    void operatorCannotListTenants() throws Exception {
        String token = login("operator", "operator123");

        mockMvc.perform(get("/api/v1/platform/tenants").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void memoriesAreIsolatedByTenantAndUser() throws Exception {
        memoryPort.save(new PlatformMemoryRecord("w21-iso-a", "default", 2L, "P001",
                "任务 A", "答案 A", Instant.parse("2026-10-24T00:00:00Z")), new float[]{1f, 0f});
        memoryPort.save(new PlatformMemoryRecord("w21-iso-b", "hospital-b", HOSPITAL_B_USER_ID, "P001",
                "任务 B", "答案 B", Instant.parse("2026-10-24T00:00:00Z")), new float[]{1f, 0f});

        String operatorToken = login("operator", "operator123");
        String doctorToken = login("doctor_b", "doctor123");

        JsonNode operatorItems = memories(operatorToken, "?limit=20");
        JsonNode doctorItems = memories(doctorToken, "?limit=20");

        assertThat(ids(operatorItems)).contains("w21-iso-a").doesNotContain("w21-iso-b");
        assertThat(ids(doctorItems)).contains("w21-iso-b").doesNotContain("w21-iso-a");
        assertThat(operatorItems.get(0).has("embedding")).isFalse();
    }

    @Test
    void nonAdminCannotReadAnotherUsersMemories() throws Exception {
        String token = login("operator", "operator123");

        mockMvc.perform(get("/api/v1/platform/memories?userId=" + HOSPITAL_B_USER_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private JsonNode memories(String token, String query) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/platform/memories" + query)
                        .header("Authorization", "Bearer " + token)
                        .header("X-Trace-Id", "week21-memory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("items");
    }

    private List<String> ids(JsonNode items) {
        List<String> ids = new ArrayList<>();
        items.forEach(item -> ids.add(item.path("memoryId").asText()));
        return ids;
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
