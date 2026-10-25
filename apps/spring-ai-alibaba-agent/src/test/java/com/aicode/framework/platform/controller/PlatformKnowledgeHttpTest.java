package com.aicode.framework.platform.controller;

import com.aicode.core.domain.model.TokenUsage;
import com.aicode.framework.knowledge.domain.KnowledgeQaAgent;
import com.aicode.framework.knowledge.domain.model.KnowledgeAnswer;
import com.aicode.framework.knowledge.domain.model.KnowledgeBaseCatalog;
import com.aicode.framework.knowledge.domain.model.KnowledgeBaseCatalogEntry;
import com.aicode.framework.knowledge.domain.model.KnowledgeCitation;
import com.aicode.framework.knowledge.domain.port.KnowledgeSearchPort;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 知识库 Agent HTTP 契约（Week 25，jdbc + 完整拦截器链）。
 *
 * <p>企业检索与 Agent 用 {@code @MockitoBean} 固定（避免跨进程与模型依赖）；
 * 绑定表、V9、RBAC、执行记录都走真实实现。</p>
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
        "platform.knowledge.reconcile.enabled=false",
        "observability.enabled=false",
        "langfuse.enabled=false"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PlatformKnowledgeHttpTest {

    private static final long KB_ID = 911L;
    private static final long ENTERPRISE_USER_ID = 7L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private KnowledgeSearchPort knowledgeSearchPort;

    @MockitoBean
    private KnowledgeQaAgent knowledgeQaAgent;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
                "DELETE FROM platform_knowledge_binding WHERE knowledge_base_id = ?", KB_ID);
        when(knowledgeSearchPort.listBases(anyLong())).thenReturn(new KnowledgeBaseCatalog(
                ENTERPRISE_USER_ID,
                List.of(new KnowledgeBaseCatalogEntry(KB_ID, "慢病指南", "内部资料", 2))));
        when(knowledgeQaAgent.run(eq(KB_ID), any())).thenReturn(new KnowledgeAnswer(
                KB_ID, "慢病指南", "糖尿病随访要点",
                "HbA1c 每 3 个月复查 [糖尿病指南.pdf#2]",
                List.of(new KnowledgeCitation("D001", "糖尿病指南.pdf", 2, "HbA1c 每 3 个月复查", 0.91)),
                1, false, new TokenUsage(10, 20, 30), "test-model", List.of()));
    }

    @Test
    void adminImportsBindingsAndOperatorCanListThem() throws Exception {
        String adminToken = login("admin", "admin123");
        String operatorToken = login("operator", "operator123");

        mockMvc.perform(post("/api/v1/platform/knowledge-bindings/import")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.imported").value(1))
                .andExpect(jsonPath("$.data.updated").value(0))
                .andExpect(jsonPath("$.data.enterpriseUserId").value(7))
                .andExpect(jsonPath("$.data.items[0].knowledgeBaseId").value(KB_ID));

        mockMvc.perform(get("/api/v1/platform/knowledge-bindings")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("慢病指南"));
    }

    @Test
    void operatorRunsKnowledgeQaOnBoundBase() throws Exception {
        String adminToken = login("admin", "admin123");
        String operatorToken = login("operator", "operator123");
        importBindings(adminToken);

        MvcResult result = mockMvc.perform(post("/api/v1/platform/agents/knowledge-qa/runs")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":{\"knowledgeBaseId\":911,\"question\":\"糖尿病随访要点\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.agentType").value("KNOWLEDGE_QA"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.outputJson").value(containsString("\"hitCount\":1")))
                .andExpect(jsonPath("$.data.outputJson").value(containsString("糖尿病指南.pdf")))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("HbA1c");
    }

    @Test
    void unboundKnowledgeBaseIs404AndDoesNotCallModel() throws Exception {
        String operatorToken = login("operator", "operator123");

        mockMvc.perform(post("/api/v1/platform/agents/knowledge-qa/runs")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":{\"knowledgeBaseId\":9999,\"question\":\"q\"}}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLATFORM_NOT_FOUND"));
    }

    @Test
    void viewerCannotRunKnowledgeQa() throws Exception {
        String viewerToken = login("viewer", "viewer123");

        mockMvc.perform(post("/api/v1/platform/agents/knowledge-qa/runs")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":{\"knowledgeBaseId\":911,\"question\":\"q\"}}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void onlyAdminCanDeleteBinding() throws Exception {
        String adminToken = login("admin", "admin123");
        String operatorToken = login("operator", "operator123");
        String bindingId = importBindings(adminToken);

        mockMvc.perform(delete("/api/v1/platform/knowledge-bindings/" + bindingId)
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/platform/knowledge-bindings/" + bindingId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/platform/knowledge-bindings")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void validationAndAuthAreEnforced() throws Exception {
        String operatorToken = login("operator", "operator123");
        importBindings(login("admin", "admin123"));

        mockMvc.perform(post("/api/v1/platform/agents/knowledge-qa/runs")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":{\"knowledgeBaseId\":911,\"question\":\" \"}}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/platform/knowledge-bindings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminReconcileSyncsRename() throws Exception {
        String adminToken = login("admin", "admin123");
        importBindings(adminToken);
        when(knowledgeSearchPort.listBasesByEnterpriseUser(ENTERPRISE_USER_ID))
                .thenReturn(new KnowledgeBaseCatalog(ENTERPRISE_USER_ID, List.of(
                        new KnowledgeBaseCatalogEntry(KB_ID, "慢病指南（2026 版）", "内部资料", 3))));

        mockMvc.perform(post("/api/v1/platform/knowledge-bindings/reconcile")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.tenantKey").value("default"))
                .andExpect(jsonPath("$.data.groups").value(1))
                .andExpect(jsonPath("$.data.updated").value(1))
                .andExpect(jsonPath("$.data.missing").value(0));

        mockMvc.perform(get("/api/v1/platform/knowledge-bindings")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].name").value("慢病指南（2026 版）"))
                .andExpect(jsonPath("$.data.items[0].syncStatus").value("ACTIVE"));
    }

    @Test
    void operatorCannotReconcileButCanReadSyncStatus() throws Exception {
        String adminToken = login("admin", "admin123");
        String operatorToken = login("operator", "operator123");
        importBindings(adminToken);

        mockMvc.perform(post("/api/v1/platform/knowledge-bindings/reconcile")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/platform/knowledge-bindings")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].syncStatus").value("ACTIVE"));
    }

    @Test
    void missingBindingIs404AndDoesNotCallAgent() throws Exception {
        String adminToken = login("admin", "admin123");
        String operatorToken = login("operator", "operator123");
        importBindings(adminToken);
        jdbcTemplate.update(
                "UPDATE platform_knowledge_binding SET sync_status = 'MISSING' WHERE knowledge_base_id = ?",
                KB_ID);

        mockMvc.perform(post("/api/v1/platform/agents/knowledge-qa/runs")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":{\"knowledgeBaseId\":911,\"question\":\"糖尿病随访要点\"}}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLATFORM_NOT_FOUND"));

        verifyNoInteractions(knowledgeQaAgent);
    }

    private String importBindings(String adminToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/platform/knowledge-bindings/import")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        return data.path("items").get(0).path("bindingId").asText();
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
