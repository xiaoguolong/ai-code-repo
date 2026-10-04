package com.aicode.framework.infrastructure.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import com.aicode.framework.observability.domain.AgentObservabilityPort;
import com.aicode.framework.observability.domain.NoopObservabilityAdapter;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;

/**
 * 链路追踪过滤器（Week 16 日志规范 + Week 17 W3C 对齐）。
 *
 * <p>解析优先级（Week 17 新增第 1 条）：</p>
 * <ol>
 *   <li>合法 {@code traceparent} → 采纳其 trace-id 作为 MDC traceId（真远程父）；</li>
 *   <li>否则取请求头 {@code X-Trace-Id}（原样使用，Week 16 契约不变）；</li>
 *   <li>都没有 → 新生成 32 位 hex。</li>
 * </ol>
 *
 * <p><b>单源对齐（Week 17 关键实现）</b>：解析出 traceId 后，把它派生出的 OTel（W3C）
 * trace-id 以 {@code traceparent} 头的形式<b>注入当前请求</b>，再继续过滤链。
 * 这样后续所有基于标准传播器的埋点（Spring Boot 自动 HTTP 观测 / Micrometer Tracing）
 * 都会采纳同一条 trace-id，不会另生成一套 —— 否则「日志里的 ID」与「链路后端里的 ID」
 * 会各说各话，正是可观测接入最常见的坑（本条为实测结论，见 Week 17 实现日志）。</p>
 *
 * <p>必须最先执行，否则访问日志与异常处理拿不到 traceId，故优先级取最高。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    private final AgentObservabilityPort observability;

    public TraceIdFilter(ObjectProvider<AgentObservabilityPort> observabilityProvider) {
        this.observability = observabilityProvider.getIfAvailable(NoopObservabilityAdapter::new);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        TraceContext w3c = TraceIds.parseTraceparent(request.getHeader(TraceIds.TRACEPARENT_HEADER));
        String traceId = resolveTraceId(request, w3c);
        MDC.put(TraceIds.MDC_KEY, traceId);
        if (w3c.valid()) {
            // 记录上游父 spanId：span 适配器据此构造「远程父」，链路后端才能连上上游
            MDC.put(TraceIds.MDC_PARENT_SPAN_ID, w3c.parentSpanId());
        }
        // Week 16 契约：X-Trace-Id 原样回显（客户端传什么回什么）
        response.setHeader(TraceIds.HEADER_NAME, traceId);

        // W3C 上下文：已有合法 traceparent 就沿用上游；否则按 traceId 派生注入口，供标准传播器采纳。
        String spanId = w3c.valid() ? w3c.parentSpanId() : TraceIds.newSpanId();
        HttpServletRequest traced = w3c.valid() ? request : withTraceparent(request, traceId, spanId);
        if (observability.isEnabled()) {
            // 回写 W3C traceparent：链路 ID 与注入的一致，下游/网关可直接续接
            response.setHeader(TraceIds.TRACEPARENT_HEADER,
                    TraceIds.formatTraceparent(TraceIds.otelTraceId(traceId), spanId, true));
        }
        try {
            filterChain.doFilter(traced, response);
        } finally {
            MDC.remove(TraceIds.MDC_KEY);
            MDC.remove(TraceIds.MDC_PARENT_SPAN_ID);
        }
    }

    /**
     * 按优先级解析本次请求的 traceId（W3C 头 > 自研头 > 新生成）。
     *
     * @param request 入站请求
     * @param w3c     已解析的 W3C traceparent
     * @return 非空 traceId
     */
    static String resolveTraceId(HttpServletRequest request, TraceContext w3c) {
        if (w3c.valid()) {
            return w3c.traceId();
        }
        String incoming = TraceIds.normalize(request.getHeader(TraceIds.HEADER_NAME));
        return incoming.isEmpty() ? TraceIds.newTraceId() : incoming;
    }

    /**
     * 把 traceId 对应的 W3C {@code traceparent} 注入请求，供标准传播器采纳。
     *
     * <p>已有合法 {@code traceparent} 时调用方不会走这里（不覆盖上游上下文）。</p>
     */
    private HttpServletRequest withTraceparent(HttpServletRequest request, String traceId, String spanId) {
        String traceparent = TraceIds.formatTraceparent(TraceIds.otelTraceId(traceId), spanId, true);
        if (traceparent.isEmpty()) {
            return request;
        }
        return new TraceparentRequestWrapper(request, traceparent);
    }

    /** 在保持其余请求行为不变的前提下，额外提供一个 {@code traceparent} 头。 */
    private static final class TraceparentRequestWrapper extends HttpServletRequestWrapper {

        private final String traceparent;

        private TraceparentRequestWrapper(HttpServletRequest request, String traceparent) {
            super(request);
            this.traceparent = traceparent;
        }

        @Override
        public String getHeader(String name) {
            if (TraceIds.TRACEPARENT_HEADER.equalsIgnoreCase(name)) {
                return traceparent;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (TraceIds.TRACEPARENT_HEADER.equalsIgnoreCase(name)) {
                return Collections.enumeration(java.util.List.of(traceparent));
            }
            return super.getHeaders(name);
        }
    }
}
