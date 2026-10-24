package com.aicode.framework.platform.controller;

import com.aicode.framework.platform.application.PlatformAuthUseCase;
import com.aicode.framework.platform.application.PlatformAuthenticationFacade;
import com.aicode.framework.platform.application.PlatformLoginOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 平台认证控制器契约测试（Week 22 起经限流门面，不再直连认证用例）。 */
@WebMvcTest(PlatformAuthController.class)
@TestPropertySource(properties = {
        "platform.security.web-interceptors.enabled=false",
        "platform.security.rate-limit.trust-forwarded-for=false"
})
class PlatformAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlatformAuthenticationFacade authenticationFacade;

    @MockitoBean
    private PlatformAuthUseCase platformAuthUseCase;

    @Test
    void loginReturnsToken() throws Exception {
        when(authenticationFacade.login(any(), anyString())).thenReturn(
                new PlatformLoginOutcome("tok", 2L, "operator", "operator", "default"));

        mockMvc.perform(post("/api/v1/platform/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"operator","password":"operator123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.token").value("tok"))
                .andExpect(jsonPath("$.data.roleKey").value("operator"));
    }
}
