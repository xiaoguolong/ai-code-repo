package com.aicode.framework.platform.controller;

import com.aicode.framework.platform.application.PlatformAuthUseCase;
import com.aicode.framework.platform.application.PlatformAuthenticationFacade;
import com.aicode.framework.platform.application.PlatformLoginOutcome;
import com.aicode.framework.platform.domain.exception.PlatformRateLimitExceededException;
import com.aicode.framework.platform.domain.model.PlatformUser;
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

/**
 * 登录限流的 HTTP 契约（Week 22）：超阈值 429 + 统一错误码；正常请求不受影响。
 */
@WebMvcTest(PlatformAuthController.class)
@TestPropertySource(properties = {
        "platform.security.web-interceptors.enabled=false",
        "platform.security.rate-limit.trust-forwarded-for=false"
})
class LoginRateLimitHttpTest {

    private static final String LOGIN_BODY = """
            {"username":"operator","password":"operator123"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlatformAuthenticationFacade authenticationFacade;

    @MockitoBean
    private PlatformAuthUseCase platformAuthUseCase;

    @Test
    void loginReturns429WhenRateLimited() throws Exception {
        when(authenticationFacade.login(any(), anyString()))
                .thenThrow(new PlatformRateLimitExceededException("too many attempts"));

        mockMvc.perform(post("/api/v1/platform/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void verifyReturns429WhenRateLimited() throws Exception {
        when(authenticationFacade.verify(any(), anyString()))
                .thenThrow(new PlatformRateLimitExceededException("too many attempts"));

        mockMvc.perform(post("/api/v1/platform/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void normalLoginStillSucceeds() throws Exception {
        when(authenticationFacade.login(any(), anyString()))
                .thenReturn(new PlatformLoginOutcome("tok", 2L, "operator", "operator", "default"));

        mockMvc.perform(post("/api/v1/platform/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.tenantKey").value("default"));
    }

    @Test
    void verifyReturnsIdentityWithoutToken() throws Exception {
        when(authenticationFacade.verify(any(), anyString()))
                .thenReturn(new PlatformUser(2L, "operator", "hash", "operator", "default"));

        mockMvc.perform(post("/api/v1/platform/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantKey").value("default"))
                .andExpect(jsonPath("$.data.token").doesNotExist());
    }
}
