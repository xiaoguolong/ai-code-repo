package com.aicode.patient.infrastructure.deprecation;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** 弃用标记：所有响应必须带三个头，且不改变业务状态码。 */
class DeprecatedApiFilterTest {

    private static final String SUCCESSOR_URL =
            "http://localhost:8084/api/v1/platform/agents/patient-analysis/runs";

    @Test
    void addsDeprecationSunsetAndSuccessorLinkHeaders() throws Exception {
        DeprecatedApiFilter filter = new DeprecatedApiFilter(SUCCESSOR_URL);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/agents/runs");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> ((MockHttpServletResponse) res).setStatus(200);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Deprecation")).isEqualTo("true");
        assertThat(response.getHeader("Sunset")).isEqualTo(DeprecatedApiFilter.SUNSET_AT);
        assertThat(response.getHeader("Link")).isEqualTo("<" + SUCCESSOR_URL + ">; rel=\"successor-version\"");
    }

    @Test
    void keepsExistingHeadersIntact() throws Exception {
        DeprecatedApiFilter filter = new DeprecatedApiFilter(SUCCESSOR_URL);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            MockHttpServletResponse servletResponse = (MockHttpServletResponse) res;
            servletResponse.setStatus(401);
            servletResponse.setHeader("X-Trace-Id", "trace-1");
        };

        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/agents/runs"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("X-Trace-Id")).isEqualTo("trace-1");
        assertThat(response.getHeader("Deprecation")).isEqualTo("true");
    }
}
