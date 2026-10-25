package com.aicode.enterprise.controller;

import com.aicode.enterprise.application.InternalKnowledgeBaseView;
import com.aicode.enterprise.application.InternalKnowledgeCatalog;
import com.aicode.enterprise.application.InternalKnowledgeHit;
import com.aicode.enterprise.application.InternalKnowledgeResult;
import com.aicode.enterprise.application.InternalKnowledgeUseCase;
import com.aicode.enterprise.infrastructure.internal.InternalProperties;
import com.aicode.enterprise.infrastructure.internal.InternalTokenGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 内部知识库端点契约（Week 25）：令牌守卫、请求映射、响应形状。
 *
 * <p>standalone MockMvc：只验证控制器 + 守卫 + 异常处理；检索逻辑在
 * {@code InternalKnowledgeUseCaseTest} 覆盖。</p>
 */
class InternalKnowledgeControllerTest {

    private static final String TOKEN = "test-internal-token";

    private InternalKnowledgeUseCase useCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        useCase = mock(InternalKnowledgeUseCase.class);
        InternalTokenGuard guard = new InternalTokenGuard(new InternalProperties(TOKEN));
        mockMvc = MockMvcBuilders
                .standaloneSetup(new InternalKnowledgeController(useCase, guard))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void rejectsMissingOrWrongToken() throws Exception {
        mockMvc.perform(get("/api/v1/internal/knowledge/bases").param("platformUserId", "2"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/internal/knowledge/bases")
                        .header("X-Internal-Token", "wrong")
                        .param("platformUserId", "2"))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsCatalogForLinkedUser() throws Exception {
        when(useCase.listBases(2L)).thenReturn(new InternalKnowledgeCatalog(
                7L, List.of(new InternalKnowledgeBaseView(11L, "慢病指南", "内部", 2))));

        mockMvc.perform(get("/api/v1/internal/knowledge/bases")
                        .header("X-Internal-Token", TOKEN)
                        .param("platformUserId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.enterpriseUserId").value(7))
                .andExpect(jsonPath("$.data.bases[0].knowledgeBaseId").value(11))
                .andExpect(jsonPath("$.data.bases[0].name").value("慢病指南"));
    }

    @Test
    void returnsCatalogByEnterpriseUserId() throws Exception {
        when(useCase.listBasesByEnterpriseUser(7L)).thenReturn(new InternalKnowledgeCatalog(
                7L, List.of(new InternalKnowledgeBaseView(11L, "慢病指南", "内部", 2))));

        mockMvc.perform(get("/api/v1/internal/knowledge/bases")
                        .header("X-Internal-Token", TOKEN)
                        .param("enterpriseUserId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enterpriseUserId").value(7))
                .andExpect(jsonPath("$.data.bases[0].knowledgeBaseId").value(11));
    }

    @Test
    void requiresExactlyOneLookupMode() throws Exception {
        mockMvc.perform(get("/api/v1/internal/knowledge/bases")
                        .header("X-Internal-Token", TOKEN))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/internal/knowledge/bases")
                        .header("X-Internal-Token", TOKEN)
                        .param("platformUserId", "2")
                        .param("enterpriseUserId", "7"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsSearchHits() throws Exception {
        when(useCase.search(any())).thenReturn(new InternalKnowledgeResult(
                7L,
                new InternalKnowledgeBaseView(11L, "慢病指南", "内部", 2),
                List.of(new InternalKnowledgeHit("D001", "糖尿病指南.pdf", 1, "HbA1c 每 3 个月复查", 0.9))));

        mockMvc.perform(post("/api/v1/internal/knowledge/search")
                        .header("X-Internal-Token", TOKEN)
                        .contentType("application/json")
                        .content("{\"enterpriseUserId\":7,\"knowledgeBaseId\":11,"
                                + "\"query\":\"糖尿病随访\",\"topK\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.knowledgeBase.knowledgeBaseId").value(11))
                .andExpect(jsonPath("$.data.hits[0].documentName").value("糖尿病指南.pdf"))
                .andExpect(jsonPath("$.data.hits[0].score").value(0.9));
    }

    @Test
    void rejectsBlankQueryWith400() throws Exception {
        mockMvc.perform(post("/api/v1/internal/knowledge/search")
                        .header("X-Internal-Token", TOKEN)
                        .contentType("application/json")
                        .content("{\"enterpriseUserId\":7,\"knowledgeBaseId\":11,\"query\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
