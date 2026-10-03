package com.aicode.framework.infrastructure.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 统一访问日志过滤器（Week 16 日志规范）。
 *
 * <p>输出固定 {@code key=value} 结构，便于 grep 与后续日志采集：
 * {@code [api] method=GET path=/api/v1/platform/agents status=200 durationMs=12 traceId=...}。</p>
 *
 * <p>只记录请求行与方法、状态、耗时、traceId、用户标识，不记录请求体与响应体，
 * 因此不会把密码、token、Prompt 正文写进日志。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiAccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiAccessLogFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
            log.info("[api] method={} path={} status={} durationMs={} traceId={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    durationMs,
                    TraceIds.current());
        }
    }
}
