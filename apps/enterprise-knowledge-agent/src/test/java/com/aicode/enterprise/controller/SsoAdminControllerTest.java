package com.aicode.enterprise.controller;

import com.aicode.enterprise.application.SsoAdminUseCase;
import com.aicode.enterprise.domain.model.SsoUserLink;
import com.aicode.enterprise.infrastructure.sso.AdminProperties;
import com.aicode.enterprise.infrastructure.sso.AdminTokenGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SSO 运维端点 MVC 契约（Week 23，A1）：令牌守卫、请求映射、响应形状。
 *
 * <p>用 standalone MockMvc（不启动 Spring/MyBatis 上下文）：本类的目的是控制器契约，
 * 真实落库由 {@code JdbcSsoUserLinkAdapterTest} 覆盖；避免与其它适配器测试共享 H2 状态。</p>
 */
class SsoAdminControllerTest {

    private static final String TOKEN = "test-admin-token";

    private SsoAdminUseCase useCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        useCase = mock(SsoAdminUseCase.class);
        AdminTokenGuard guard = new AdminTokenGuard(new AdminProperties(TOKEN));
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SsoAdminController(useCase, guard))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void rejectsMissingOrWrongToken() throws Exception {
        mockMvc.perform(get("/api/v1/admin/sso-links"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/admin/sso-links").header("X-Admin-Token", "wrong"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void linksAndReturnsMapping() throws Exception {
        when(useCase.link(9001L, 7L, "default")).thenReturn(
                new SsoUserLink(9001L, 7L, "default", Instant.parse("2026-10-25T00:00:00Z")));

        mockMvc.perform(post("/api/v1/admin/sso-links")
                        .header("X-Admin-Token", TOKEN)
                        .contentType("application/json")
                        .content("{\"platformUserId\":9001,\"enterpriseUserId\":7,\"tenantKey\":\"default\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.platformUserId").value(9001))
                .andExpect(jsonPath("$.data.enterpriseUserId").value(7))
                .andExpect(jsonPath("$.data.tenantKey").value("default"))
                .andExpect(jsonPath("$.data.linkedAt").isNotEmpty());
    }

    @Test
    void unlinksWithDeleteLocalUserFlag() throws Exception {
        when(useCase.unlink(9001L, true)).thenReturn(
                new SsoAdminUseCase.SsoUnlinkResult(9001L, 7L, true, true));

        mockMvc.perform(delete("/api/v1/admin/sso-links/9001")
                        .header("X-Admin-Token", TOKEN)
                        .param("deleteLocalUser", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.unlinked").value(true))
                .andExpect(jsonPath("$.data.localUserDeleted").value(true));
        verify(useCase).unlink(eq(9001L), eq(true));
    }

    @Test
    void listsLinks() throws Exception {
        when(useCase.list(50)).thenReturn(java.util.List.of(
                new SsoUserLink(9001L, 7L, "default", Instant.parse("2026-10-25T00:00:00Z"))));

        mockMvc.perform(get("/api/v1/admin/sso-links")
                        .header("X-Admin-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].platformUserId").value(9001));
    }
}
