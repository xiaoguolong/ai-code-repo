package com.aicode.framework.observability.support;

import com.aicode.core.domain.model.TokenUsage;
import com.aicode.framework.observability.domain.AgentObservabilityPort;
import com.aicode.framework.observability.domain.NoopObservabilityAdapter;
import com.aicode.framework.observability.domain.SpanKind;
import com.aicode.framework.observability.domain.SpanScope;
import com.aicode.framework.observability.domain.TraceContextView;
import com.aicode.framework.observability.domain.TraceDimensions;
import com.aicode.framework.observability.domain.TraceScope;
import com.aicode.framework.observability.infrastructure.LangfuseContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 记录调用的观测端口假实现（单测用，Week 19）。
 *
 * <p>用于验证「SkyWalking 装饰器是否把调用透传给了既有端口」——这是「加后端不改契约」
 * 的核心证据：既有 OTel/Langfuse 埋点必须一个都不少。</p>
 */
public class RecordingObservabilityPort implements AgentObservabilityPort {

    /** 被调用的方法记录。 */
    public final List<String> calls = new ArrayList<>();

    private final AgentObservabilityPort delegate = new NoopObservabilityAdapter();
    private final LangfuseContext langfuseContext = new LangfuseContext();

    @Override
    public SpanScope openSpan(SpanKind kind, String name, Map<String, String> attributes) {
        calls.add("openSpan:" + kind + ":" + name + ":" + (attributes == null ? 0 : attributes.size()));
        return new RecordingScope(calls, name);
    }

    @Override
    public TraceScope beginTrace(TraceDimensions dimensions) {
        calls.add("beginTrace:" + (dimensions == null ? "null" : dimensions.traceName()));
        return langfuseContext.begin(dimensions);
    }

    @Override
    public void recordTokenUsage(String model, TokenUsage usage) {
        calls.add("recordTokenUsage:" + model);
        delegate.recordTokenUsage(model, usage);
    }

    @Override
    public void recordCounter(String name, double amount, String... tags) {
        calls.add("recordCounter:" + name);
    }

    @Override
    public void recordDuration(String name, long durationMs, String... tags) {
        calls.add("recordDuration:" + name);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public TraceContextView currentTraceContext(String traceId) {
        calls.add("currentTraceContext:" + traceId);
        return new TraceContextView(traceId, "otel-trace", "otel-span",
                "00-otel-trace-otel-span-01", true, true);
    }

    @Override
    public String activeSpanId() {
        calls.add("activeSpanId");
        return "otel-span";
    }

    /**
     * 某个调用是否出现（前缀匹配）。
     *
     * @param prefix 前缀
     * @return 出现返回 true
     */
    public boolean sawCall(String prefix) {
        return calls.stream().anyMatch(call -> call.startsWith(prefix));
    }

    /** 记录 span 生命周期操作的假句柄。 */
    private static final class RecordingScope implements SpanScope {

        private final List<String> calls;
        private final String name;
        private boolean closed;

        private RecordingScope(List<String> calls, String name) {
            this.calls = calls;
            this.name = name;
        }

        @Override
        public void attribute(String key, String value) {
            calls.add("attribute:" + key + "=" + value);
        }

        @Override
        public void recordError(Throwable throwable) {
            calls.add("recordError:" + (throwable == null ? "null" : throwable.getClass().getSimpleName()));
        }

        @Override
        public String traceId() {
            return "otel-trace";
        }

        @Override
        public String spanId() {
            return "otel-span";
        }

        @Override
        public boolean sampled() {
            return true;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                calls.add("close:" + name);
            }
        }
    }
}
