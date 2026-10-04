package com.aicode.framework.infrastructure.logging;

import com.aicode.framework.observability.domain.AgentObservabilityPort;
import com.aicode.framework.observability.domain.NoopObservabilityAdapter;
import com.aicode.framework.observability.domain.SpanKind;
import com.aicode.framework.observability.domain.SpanScope;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 入站请求 server span 过滤器（Week 17）。
 *
 * <p>位置：紧跟 {@link TraceIdFilter}（此时 traceId 已入 MDC，且请求内已注入/沿用
 * W3C {@code traceparent}）。</p>
 *
 * <p>行为：</p>
 * <ol>
 *   <li>若调用链上已有活动 span（框架自动观测先行时），<b>不新开 span</b> ——
 *       避免链路后端出现业务入口的两个兄弟 server span；</li>
 *   <li>否则开一个 SERVER span，其 traceId 由 MDC 派生（与既有 traceId 同源，
 *       也与 {@link TraceIdFilter} 回写的 {@code traceparent} 一致）；</li>
 *   <li>结束时补齐状态码与耗时，5xx 标记为错误。</li>
 * </ol>
 *
 * <p>traceId 在进入时即捕获：{@link TraceIdFilter} 会在请求结束时清理 MDC，
 * 若在 finally 里现取会得到空值（实测踩过）。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class HttpServerSpanFilter extends OncePerRequestFilter {

    /** server span 名（链路后端按此检索）。 */
    public static final String SPAN_NAME = "http.server";

    /** HTTP 方法属性（对齐 OTel HTTP 语义约定）。 */
    static final String ATTR_HTTP_METHOD = "http.request.method";

    /** URL 路径属性。 */
    static final String ATTR_HTTP_PATH = "url.path";

    /** 响应状态码属性。 */
    static final String ATTR_HTTP_STATUS = "http.response.status_code";

    /** 请求总耗时属性（毫秒）。 */
    static final String ATTR_DURATION_MS = "duration.ms";

    /** traceId 的 span 属性键（便于在链路后端按日志 traceId 反查）。 */
    static final String ATTR_TRACE_ID = "http.trace_id";

    private final AgentObservabilityPort observability;

    /**
     * @param observabilityProvider 观测端口；用 {@link ObjectProvider} 保证观测未装配时退化为空实现。
     *                              本类有两个构造器，故显式标注 {@link Autowired} 指定注入点。
     */
    @Autowired
    public HttpServerSpanFilter(ObjectProvider<AgentObservabilityPort> observabilityProvider) {
        this(observabilityProvider.getIfAvailable(NoopObservabilityAdapter::new));
    }

    /**
     * 直接注入端口的构造器，供单测与手工装配使用。
     *
     * @param observability 观测端口，非 null
     */
    public HttpServerSpanFilter(AgentObservabilityPort observability) {
        this.observability = observability;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startedAt = System.nanoTime();

        // 分支一：调用链上已有活动 span（框架自动观测先行）——不新开 span，交由该 span 记录
        if (!observability.activeSpanId().isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        // 分支二：本过滤器负责建立 server span
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put(ATTR_HTTP_METHOD, request.getMethod());
        attributes.put(ATTR_HTTP_PATH, request.getRequestURI());
        attributes.put(ATTR_TRACE_ID, TraceIds.otelTraceId(TraceIds.current()));

        try (SpanScope scope = observability.openSpan(SpanKind.SERVER, SPAN_NAME, attributes)) {
            try {
                filterChain.doFilter(request, response);
            } catch (ServletException | IOException | RuntimeException ex) {
                scope.recordError(ex);
                throw ex;
            } finally {
                scope.attribute(ATTR_HTTP_STATUS, String.valueOf(response.getStatus()));
                scope.attribute(ATTR_DURATION_MS, String.valueOf(elapsedMs(startedAt)));
                if (response.getStatus() >= 500) {
                    scope.recordError(new IllegalStateException("http " + response.getStatus()));
                }
            }
        }
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
