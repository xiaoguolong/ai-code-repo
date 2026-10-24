package com.aicode.patient.infrastructure.deprecation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * patient-agent（8083）弃用标记（Week 21）。
 *
 * <p>只加响应头，**不改任何业务行为**：第 5–7 周的 19 个用例仍是回归基线。
 * 三个头按 RFC 8594 / draft-dalal-deprecation-header 语义：</p>
 *
 * <ul>
 *   <li>{@code Deprecation: true} —— 该资源已弃用；</li>
 *   <li>{@code Sunset} —— 计划下线时间；</li>
 *   <li>{@code Link: rel="successor-version"} —— 替代入口（8084 的 patient-analysis）。</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class DeprecatedApiFilter extends OncePerRequestFilter {

    /** 计划下线时间（RFC 1123 格式）。 */
    public static final String SUNSET_AT = "Wed, 31 Dec 2026 23:59:59 GMT";

    private final String successorUrl;

    /**
     * @param successorUrl 替代入口地址（默认指向平台 8084 的患者分析 Agent）
     */
    public DeprecatedApiFilter(
            @Value("${patient.deprecated.successor-url:http://localhost:8084/api/v1/platform/agents/patient-analysis/runs}")
            String successorUrl
    ) {
        this.successorUrl = successorUrl == null || successorUrl.isBlank()
                ? "http://localhost:8084/api/v1/platform/agents/patient-analysis/runs"
                : successorUrl.trim();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        response.setHeader("Deprecation", "true");
        response.setHeader("Sunset", SUNSET_AT);
        response.setHeader("Link", "<" + successorUrl + ">; rel=\"successor-version\"");
        filterChain.doFilter(request, response);
    }
}
