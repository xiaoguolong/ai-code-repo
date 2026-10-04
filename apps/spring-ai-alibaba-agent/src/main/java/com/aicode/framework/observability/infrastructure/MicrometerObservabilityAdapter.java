package com.aicode.framework.observability.infrastructure;

import com.aicode.core.domain.model.TokenUsage;
import com.aicode.framework.infrastructure.logging.TraceIds;
import com.aicode.framework.observability.domain.AgentObservabilityPort;
import com.aicode.framework.observability.domain.ObservabilityAttributes;
import com.aicode.framework.observability.domain.SpanKind;
import com.aicode.framework.observability.domain.SpanScope;
import com.aicode.framework.observability.domain.TraceContextView;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.slf4j.MDC;

import java.time.Duration;
import java.util.Map;

/**
 * 观测端口的 Micrometer 适配器（Week 17）——唯一允许出现框架类型的实现。
 *
 * <p>职责：</p>
 * <ol>
 *   <li>把 {@link SpanKind} 映射为 Micrometer {@code Span.Kind}，按 {@link TraceIds} 派生的
 *       traceId 显式设置父上下文，保证与既有 MDC traceId 同源；</li>
 *   <li>把 token 用量与调用次数登记到 {@link MeterRegistry}（经 Actuator 暴露为 Prometheus 文本格式）；</li>
 *   <li>恢复 MDC 原值，保证 span 关闭后既有日志的 traceId 不被污染。</li>
 * </ol>
 *
 * <p>本类不抛异常：可观测故障不得影响业务主流程。</p>
 */
public class MicrometerObservabilityAdapter implements AgentObservabilityPort {

    /** 链路后端未给出模型名时的占位，避免产生空标签值。 */
    static final String UNKNOWN_MODEL = "unknown";

    private final Tracer tracer;
    private final MeterRegistry meterRegistry;

    public MicrometerObservabilityAdapter(Tracer tracer, MeterRegistry meterRegistry) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public SpanScope openSpan(SpanKind kind, String name, Map<String, String> attributes) {
        try {
            Span.Builder builder = newSpanBuilder(kind, name);
            if (attributes != null) {
                for (Map.Entry<String, String> entry : attributes.entrySet()) {
                    applyAttribute(builder, entry.getKey(), entry.getValue());
                }
            }
            applyAttribute(builder, ObservabilityAttributes.SPAN_KIND, kind == null ? "" : kind.name());
            // 必须先 start()：span context（traceId / spanId）通常在启动时才确定
            Span span = builder.start();
            // 入栈当前 span：后续子 span 才能通过 currentSpan() 识别父 span，形成正确的 span 树
            Tracer.SpanInScope spanInScope = tracer.withSpan(span);
            return new MicrometerSpanScope(span, spanInScope);
        } catch (RuntimeException ex) {
            return NoopScope.INSTANCE;
        }
    }

    @Override
    public void recordTokenUsage(String model, TokenUsage usage) {
        if (usage == null) {
            return;
        }
        String tag = modelName(model);
        if (usage.promptTokens() > 0) {
            recordCounter("llm.tokens.total", usage.promptTokens(),
                    ObservabilityAttributes.GEN_AI_REQUEST_MODEL, tag,
                    ObservabilityAttributes.TOKEN_TYPE, ObservabilityAttributes.TOKEN_TYPE_PROMPT);
        }
        if (usage.completionTokens() > 0) {
            recordCounter("llm.tokens.total", usage.completionTokens(),
                    ObservabilityAttributes.GEN_AI_REQUEST_MODEL, tag,
                    ObservabilityAttributes.TOKEN_TYPE, ObservabilityAttributes.TOKEN_TYPE_COMPLETION);
        }
    }

    @Override
    public void recordCounter(String name, double amount, String... tags) {
        if (name == null || name.isBlank()) {
            return;
        }
        try {
            meterRegistry.counter(name, parsedTags(tags)).increment(amount);
        } catch (RuntimeException ex) {
            // 忽略：指标登记失败不得影响业务
        }
    }

    @Override
    public void recordDuration(String name, long durationMs, String... tags) {
        if (name == null || name.isBlank()) {
            return;
        }
        try {
            meterRegistry.timer(name, parsedTags(tags)).record(Duration.ofMillis(Math.max(durationMs, 0L)));
        } catch (RuntimeException ex) {
            // 忽略：指标登记失败不得影响业务
        }
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * 读取当前 span 的链路上下文。traceId 取 span 自身的（与 MDC 同源派生），
     * 因此响应体里的 {@code traceId} 与 {@code traceparent} 必然指向同一条链路。
     */
    @Override
    public TraceContextView currentTraceContext(String traceId) {
        Span current = tracer.currentSpan();
        if (current == null || current.context() == null) {
            return TraceContextView.disabled(traceId == null ? "" : traceId);
        }
        TraceContext context = current.context();
        String otelTraceId = context.traceId() == null ? "" : context.traceId();
        String spanId = context.spanId() == null ? "" : context.spanId();
        boolean sampled = Boolean.TRUE.equals(context.sampled());
        return new TraceContextView(
                traceId == null ? "" : traceId,
                otelTraceId,
                spanId,
                TraceIds.formatTraceparent(otelTraceId, spanId, sampled),
                sampled,
                true);
    }

    @Override
    public String activeSpanId() {
        Span current = tracer.currentSpan();
        if (current == null || current.context() == null || current.context().spanId() == null) {
            return "";
        }
        return current.context().spanId();
    }

    /**
     * 建立 span builder，并把当前 MDC traceId 派生的 OTel traceId 设为父上下文（单源对齐）。
     *
     * <p>若调用链上已存在活动 span（例如外层已有 server span），则交给 Micrometer 走正常父子关系，
     * 不再显式设置父，避免破坏已有 span 树。</p>
     */
    private Span.Builder newSpanBuilder(SpanKind kind, String name) {
        Span.Builder builder = tracer.spanBuilder();
        if (name != null && !name.isBlank()) {
            builder.name(name);
        }
        if (kind != null) {
            Span.Kind micrometerKind = toMicrometerKind(kind);
            if (micrometerKind != null) {
                builder.kind(micrometerKind);
            }
        }
        if (tracer.currentSpan() == null) {
            // 调用链上没有活动 span 时，尽力用「上游 traceparent 的真实父 spanId」建远程父。
            // 两条红线（均实测踩过）：
            //   1. 不能用全 0 父 spanId —— OTel SDK 判定父上下文无效，会忽略 traceId 另生成一条；
            //   2. 不能编造随机父 spanId —— 链路后端会出现指向不存在 span 的父子假边。
            // 都不满足时不设父（由 SDK 生成根 span）；生产路径上框架观测已建立活动 span，
            // 走上面的正常父子分支，不会落到这里。
            String upstreamTraceId = TraceIds.normalize(MDC.get(TraceIds.MDC_KEY));
            String upstreamParentSpanId = TraceIds.normalize(MDC.get(TraceIds.MDC_PARENT_SPAN_ID));
            if (!upstreamTraceId.isEmpty() && !upstreamParentSpanId.isEmpty()) {
                TraceContext parent = tracer.traceContextBuilder()
                        .traceId(TraceIds.otelTraceId(upstreamTraceId))
                        .spanId(upstreamParentSpanId)
                        .sampled(true)
                        .build();
                builder.setParent(parent);
            }
        }
        return builder;
    }

    /**
     * 领域 {@link SpanKind} → Micrometer {@code Span.Kind}（框架映射只在本类内出现）。
     *
     * <p>Micrometer 只有 SERVER / CLIENT / PRODUCER / CONSUMER 四种；进程内的
     * {@code agent.run} 与 {@code tool.call} 不对应任何一种，返回 null 表示不设置，
     * 由链路后端按默认 INTERNAL 处理（避免用 CLIENT 之类误导调用拓扑）。</p>
     */
    static Span.Kind toMicrometerKind(SpanKind kind) {
        return switch (kind) {
            case SERVER -> Span.Kind.SERVER;
            case LLM_CALL -> Span.Kind.CLIENT;
            case AGENT_RUN, TOOL_CALL -> null;
        };
    }

    /** 写入单个属性；key/value 为空时忽略。 */
    private void applyAttribute(Span.Builder builder, String key, String value) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            return;
        }
        builder.tag(key, value);
    }

    /** 把「键、值、键、值…」展平为 Micrometer {@link Tags}；奇数个时忽略末位。 */
    private Tags parsedTags(String... tags) {
        if (tags == null || tags.length < 2) {
            return Tags.empty();
        }
        Tags result = Tags.empty();
        for (int i = 0; i + 1 < tags.length; i += 2) {
            if (tags[i] == null || tags[i].isBlank() || tags[i + 1] == null) {
                continue;
            }
            result = result.and(tags[i], tags[i + 1]);
        }
        return result;
    }

    /** 模型名归一：空值统一为 {@link #UNKNOWN_MODEL}，避免空标签。 */
    static String modelName(String model) {
        return model == null || model.isBlank() ? UNKNOWN_MODEL : model;
    }

    /** Micrometer span 句柄：负责结束 span、退出当前 span 作用域与恢复 MDC。 */
    private static final class MicrometerSpanScope implements SpanScope {

        private final Span span;
        private final Tracer.SpanInScope spanInScope;
        private final String traceId;
        private final String spanId;
        private final boolean sampled;
        private final boolean mdcPresent;
        private final String mdcValue;
        private boolean closed;

        private MicrometerSpanScope(Span span, Tracer.SpanInScope spanInScope) {
            this.span = span;
            this.spanInScope = spanInScope;
            TraceContext context = span.context();
            this.traceId = context == null ? "" : nullToEmpty(context.traceId());
            this.spanId = context == null ? "" : nullToEmpty(context.spanId());
            this.sampled = context != null && Boolean.TRUE.equals(context.sampled());
            String current = MDC.get(TraceIds.MDC_KEY);
            this.mdcPresent = current != null;
            this.mdcValue = current;
        }

        @Override
        public void attribute(String key, String value) {
            if (closed || key == null || key.isBlank() || value == null || value.isBlank()) {
                return;
            }
            span.tag(key, value);
        }

        @Override
        public void recordError(Throwable throwable) {
            if (closed || throwable == null) {
                return;
            }
            span.error(throwable);
        }

        @Override
        public String traceId() {
            return traceId;
        }

        @Override
        public String spanId() {
            return spanId;
        }

        @Override
        public boolean sampled() {
            return sampled;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            // 顺序很重要：先结束 span、退出作用域，最后恢复 MDC。
            // 原因：Micrometer/OTel 的 scope 装饰器（如 Boot 的 CorrelationScopeDecorator）会在
            // 作用域关闭时改写 MDC；若先恢复再关闭作用域，恢复值会被覆盖 —— 会导致
            // 「span 结束后 MDC 里留着链路 traceId」，进而让后续审计 traceId 与访问日志不一致。
            RuntimeException failure = null;
            try {
                span.end();
            } catch (RuntimeException ex) {
                failure = ex;
            }
            if (spanInScope != null) {
                try {
                    spanInScope.close();
                } catch (RuntimeException ex) {
                    if (failure == null) {
                        failure = ex;
                    }
                }
            }
            restoreMdc();
            if (failure != null) {
                throw failure;
            }
        }

        /**
         * 恢复进入 span 前的 MDC traceId，避免线程复用时把链路 ID 串到下一个请求。
         *
         * <p>注意：框架的关联装饰器（Spring Boot {@code CorrelationScopeDecorator}）会在 span
         * 成为当前 span 时把 <b>span 自己的 traceId</b> 写进 MDC —— 这正是「日志 ID 与链路 ID
         * 一致」所依赖的行为，因此本方法只负责在 span 结束后把 MDC 还原到进入前的值，
         * 不会在 span 生命周期内与之争夺 MDC。</p>
         */
        private void restoreMdc() {
            if (mdcPresent) {
                MDC.put(TraceIds.MDC_KEY, mdcValue);
            } else {
                MDC.remove(TraceIds.MDC_KEY);
            }
        }

        private static String nullToEmpty(String value) {
            return value == null ? "" : value;
        }
    }

    /** 建立 span 失败时的兜底句柄：保证调用方 try-with-resources 不 NPE。 */
    private static final class NoopScope implements SpanScope {

        private static final NoopScope INSTANCE = new NoopScope();

        @Override
        public void attribute(String key, String value) {
            // 兜底：不记录
        }

        @Override
        public void recordError(Throwable throwable) {
            // 兜底：不记录
        }

        @Override
        public String traceId() {
            return "";
        }

        @Override
        public String spanId() {
            return "";
        }

        @Override
        public boolean sampled() {
            return false;
        }

        @Override
        public void close() {
            // 无资源可释放
        }
    }
}
