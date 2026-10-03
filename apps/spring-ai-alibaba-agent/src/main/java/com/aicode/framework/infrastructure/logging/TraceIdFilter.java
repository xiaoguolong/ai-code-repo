package com.aicode.framework.infrastructure.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 链路追踪过滤器（Week 16 日志规范）。
 *
 * <p>行为：取请求头 {@code X-Trace-Id}（无则生成 UUID）→ 写入 {@link MDC}
 * → 回写同名响应头 → 请求结束清理 MDC，避免线程池复用造成 traceId 串号。</p>
 *
 * <p>必须最先执行，否则访问日志与异常处理拿不到 traceId，故优先级取最高。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String incoming = TraceIds.normalize(request.getHeader(TraceIds.HEADER_NAME));
        String traceId = incoming.isEmpty() ? UUID.randomUUID().toString() : incoming;
        MDC.put(TraceIds.MDC_KEY, traceId);
        response.setHeader(TraceIds.HEADER_NAME, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TraceIds.MDC_KEY);
        }
    }
}
