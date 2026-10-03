package com.aicode.framework.platform.controller;

import com.aicode.core.domain.exception.AuthenticationException;
import com.aicode.framework.dto.ApiErrorCode;
import com.aicode.framework.platform.application.PlatformAuthUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API / 异常规范契约测试：
 * 1) 失败响应 code 必须来自 {@link ApiErrorCode}；
 * 2) 成功与失败都必须携带非空 traceId，且与响应头 {@code X-Trace-Id} 一致；
 * 3) 响应体不得泄漏堆栈、SQL 或厂商报文。
 */
@WebMvcTest(PlatformAuthController.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = "platform.security.web-interceptors.enabled=false")
class ApiStandardsContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlatformAuthUseCase platformAuthUseCase;

    @Test
    void validationFailureUsesEnumCodeAndCarriesTraceId() throws Exception {
        mockMvc.perform(post("/api/v1/platform/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.VALIDATION_ERROR.name()))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void authenticationFailureUsesEnumCodeAndHidesCredentials() throws Exception {
        when(platformAuthUseCase.login(any())).thenThrow(new AuthenticationException("invalid credentials"));

        MvcResult result = mockMvc.perform(post("/api/v1/platform/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"operator\",\"password\":\"operator123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.UNAUTHORIZED.name()))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andReturn();

        assertNoLeak(result);
    }

    @Test
    void responseHeaderTraceIdMatchesBodyTraceId() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/platform/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertThat(headerTraceId).isNotBlank();
        assertThat(result.getResponse().getContentAsString()).contains(headerTraceId);
    }

    @Test
    void incomingTraceIdIsPropagatedInsteadOfRegenerated() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/platform/auth/login")
                        .header("X-Trace-Id", "client-trace-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(result.getResponse().getHeader("X-Trace-Id")).isEqualTo("client-trace-123");
        assertThat(result.getResponse().getContentAsString()).contains("client-trace-123");
    }

    @Test
    void everyFailureCodeBelongsToErrorCodeEnum() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/platform/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String reportedCode = body.replaceAll(".*\"code\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        assertThat(Arrays.stream(ApiErrorCode.values()).map(Enum::name)).contains(reportedCode);
    }

    private void assertNoLeak(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        assertThat(body)
                .doesNotContain("Exception")
                .doesNotContain("at com.aicode")
                .doesNotContain("SQLException")
                .doesNotContain("api.deepseek.com")
                .doesNotContain("sk-");
    }
}
