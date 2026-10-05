package com.aicode.framework.observability.domain;

import com.aicode.core.domain.model.TokenUsage;

import java.util.Map;

/**
 * 观测端口空实现（{@code observability.enabled=false}）。
 *
 * <p>所有方法为空操作：不创建 span、不登记指标、不抛出异常。用于无观测需求的环境
 * （单测、纯业务部署）保持零额外开销，同时让调用方代码无需分支判断。</p>
 */
public class NoopObservabilityAdapter implements AgentObservabilityPort {

    @Override
    public SpanScope openSpan(SpanKind kind, String name, Map<String, String> attributes) {
        return NoopSpanScope.INSTANCE;
    }

    @Override
    public TraceScope beginTrace(TraceDimensions dimensions) {
        return NoopTraceScope.INSTANCE;
    }

    @Override
    public void recordTokenUsage(String model, TokenUsage usage) {
        // 观测关闭：不记录
    }

    @Override
    public void recordCounter(String name, double amount, String... tags) {
        // 观测关闭：不记录
    }

    @Override
    public void recordDuration(String name, long durationMs, String... tags) {
        // 观测关闭：不记录
    }

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public TraceContextView currentTraceContext(String traceId) {
        return TraceContextView.disabled(traceId == null ? "" : traceId);
    }

    @Override
    public String activeSpanId() {
        return "";
    }

    /** 空 trace 维度句柄：观测关闭时没有维度可言，close 无副作用。 */
    private static final class NoopTraceScope implements TraceScope {

        private static final NoopTraceScope INSTANCE = new NoopTraceScope();

        @Override
        public void close() {
            // 无资源可释放
        }
    }

    /** 空 span 句柄：无状态、可复用、所有操作幂等。 */
    private static final class NoopSpanScope implements SpanScope {

        private static final NoopSpanScope INSTANCE = new NoopSpanScope();

        @Override
        public void attribute(String key, String value) {
            // 观测关闭：不记录
        }

        @Override
        public void recordError(Throwable throwable) {
            // 观测关闭：不记录
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
