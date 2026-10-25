package com.aicode.framework.platform.controller;

import com.aicode.framework.platform.domain.model.PlatformMemoryRecord;
import com.aicode.framework.platform.domain.model.PlatformMemoryScope;
import com.aicode.framework.platform.domain.port.PlatformMemoryPort;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 记忆生命周期 HTTP 契约（Week 23）：删除、清理、向量重建的权限与响应形状。
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
class PlatformMemoryMaintenanceHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlatformMemoryPort memoryPort;

    @Test
    void operatorDeletesOwnMemory() throws Exception {
        String memoryId = insertMemory("default", 2L);
        String token = login("operator", "operator123");

        mockMvc.perform(delete("/api/v1/platform/memories/" + memoryId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.memoryId").value(memoryId))
                .andExpect(jsonPath("$.data.deleted").value(1));

        mockMvc.perform(get("/api/v1/platform/memories?limit=20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].memoryId",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(memoryId))));
    }

    @Test
    void deletingForeignMemoryLooksLikeNotFound() throws Exception {
        String memoryId = insertMemory("hospital-b", 104L);
        String token = login("operator", "operator123");

        mockMvc.perform(delete("/api/v1/platform/memories/" + memoryId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLATFORM_NOT_FOUND"));
    }

    @Test
    void purgeRequiresAdmin() throws Exception {
        String operatorToken = login("operator", "operator123");
        mockMvc.perform(post("/api/v1/platform/memories/purge-expired")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        String adminToken = login("admin", "admin123");
        mockMvc.perform(post("/api/v1/platform/memories/purge-expired?limit=10")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.deleted").isNumber())
                .andExpect(jsonPath("$.data.limit").value(10));
    }

    @Test
    void rebuildRequiresAdminAndReturnsCounts() throws Exception {
        insertMemory("default", 2L);
        String operatorToken = login("operator", "operator123");
        mockMvc.perform(post("/api/v1/platform/memories/rebuild-embeddings")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden());

        String adminToken = login("admin", "admin123");
        mockMvc.perform(post("/api/v1/platform/memories/rebuild-embeddings?limit=10")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.scanned").isNumber())
                .andExpect(jsonPath("$.data.rebuilt").isNumber())
                .andExpect(jsonPath("$.data.failed").isNumber())
                .andExpect(jsonPath("$.data.currentModel").isNotEmpty());
    }

    private String insertMemory(String tenantKey, long userId) {
        String memoryId = "w23-http-" + UUID.randomUUID();
        Instant now = Instant.now();
        memoryPort.save(new PlatformMemoryRecord(
                memoryId, tenantKey, userId, "P001", "任务", "答案",
                now, now.plus(1, ChronoUnit.DAYS), "hashing:default:1024"), new float[]{1f, 0f, 0f});
        assertThat(memoryPort.listRecent(new PlatformMemoryScope(tenantKey, userId), 50))
                .extracting(PlatformMemoryRecord::memoryId)
                .contains(memoryId);
        return memoryId;
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
