package com.aicode.framework.observability.infrastructure;

import com.aicode.core.domain.model.TokenUsage;
import com.aicode.framework.observability.domain.AgentObservabilityPort;
import com.aicode.framework.observability.domain.ObservabilityAttributes;
import com.aicode.framework.observability.domain.SpanKind;
import com.aicode.framework.observability.domain.SpanScope;
import com.aicode.framework.observability.domain.TraceContextView;
import com.aicode.framework.observability.domain.TraceDimensions;
import com.aicode.framework.observability.domain.TraceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * SkyWalking 手动埋点装饰器（Week 19）——本类只做「叠加」，不替换任何后端。
 *
 * <p>设计要点：</p>
 * <ol>
 *   <li><b>装饰而非替换</b>：{@link AgentObservabilityPort} 契约零改动，Week 17 的 Micrometer/OTel
 *       埋点与 Prometheus 指标原样保留（{@code delegate}），SkyWalking 只是多写一份本地 span。
 *       这样「加后端不改契约」的承诺在第三天后端接入时依然成立。</li>
 *   <li><b>一次埋点两处可见</b>：同一段业务代码既产出 OTel span（Langfuse / 通用 collector），
 *       也产出 SkyWalking span，无需在两处重复插桩。</li>
 *   <li><b>跨后端关联</b>：每个 SkyWalking span 都写业务标签
 *       {@code <prefix>.trace_id = <业务 traceId>}（来自 MDC，与响应头 {@code X-Trace-Id} 一致）。
 *       SkyWalking 的 traceId 是 Base64 segmentId，与 W3C 的 32 位 hex 不可比，
 *       这个标签是两套系统之间<b>唯一</b>可靠的关联手段（见 Spec 三、）。</li>
 *   <li><b>不抛异常</b>：任何 Toolkit 失败都只记 DEBUG，业务主流程不受影响（Week 17/18 同口径）。</li>
 * </ol>
 */
public class SkyWalkingObservabilityAdapter implements AgentObservabilityPort {

    private static final Logger log = LoggerFactory.getLogger(SkyWalkingObservabilityAdapter.class);

    /** 无操作名时的兜底 span 名，避免链路后端出现空名 span。 */
    static final String FALLBACK_OPERATION = ToolkitSpanBridge.FALLBACK_OPERATION;

    private final AgentObservabilityPort delegate;
    private final SkyWalkingSpanBridge bridge;
    private final String tagPrefix;

    /**
     * @param delegate  既有观测端口（Micrometer/OTel 实现）
     * @param bridge    SkyWalking 桥（真实或空实现）
     * @param tagPrefix 业务标签前缀
     */
    public SkyWalkingObservabilityAdapter(
            AgentObservabilityPort delegate, SkyWalkingSpanBridge bridge, String tagPrefix) {
        this.delegate = delegate;
        this.bridge = bridge == null ? NoopSpanBridge.INSTANCE : bridge;
        this.tagPrefix = tagPrefix == null || tagPrefix.isBlank() ? "aicode" : tagPrefix.trim();
    }

    @Override
    public SpanScope openSpan(SpanKind kind, String name, Map<String, String> attributes) {
        SpanScope delegateScope = delegate.openSpan(kind, name, attributes);
        SkyWalkingSpanBridge.SkyWalkingSpanHandle handle;
        try {
            handle = bridge.startLocalSpan(name == null || name.isBlank() ? FALLBACK_OPERATION : name);
        } catch (RuntimeException ex) {
            log.debug("[skywalking] 建 span 失败（忽略）：{}", ex.toString());
            return delegateScope;
        }
        writeTag(kind == null ? "" : kind.name(), attributes, delegateScope);
        return new CompositeScope(delegateScope, handle);
    }

    /** 写业务标签：kind、跨后端关联 traceId、以及调用方传入的属性。 */
    private void writeTag(String kind, Map<String, String> attributes, SpanScope delegateScope) {
        safeTag(tagPrefix + ".span_kind", kind);
        // 业务 traceId 来自 MDC（与响应头一致）；装饰器只读不生成，保证单源
        safeTag(tagPrefix + ".trace_id", com.aicode.framework.infrastructure.logging.TraceIds.current());
        if (attributes != null) {
            for (Map.Entry<String, String> entry : attributes.entrySet()) {
                safeTag(entry.getKey(), entry.getValue());
            }
        }
        if (delegateScope != null) {
            safeTag(tagPrefix + ".otel_trace_id", delegateScope.traceId());
        }
    }

    /**
     * 安全写标签：空键 / 空值不写（Toolkit 对空值不友好），异常不外抛。
     *
     * @param key   标签键
     * @param value 标签值
     */
    private void safeTag(String key, String value) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            return;
        }
        try {
            bridge.tagActiveSpan(key, value);
        } catch (RuntimeException ex) {
            log.debug("[skywalking] 写标签失败（忽略）：{}", ex.toString());
        }
    }

    @Override
    public TraceScope beginTrace(TraceDimensions dimensions) {
        return delegate.beginTrace(dimensions);
    }

    @Override
    public void recordTokenUsage(String model, TokenUsage usage) {
        delegate.recordTokenUsage(model, usage);
        // 让 SkyWalking 侧也能按模型筛链路（token 数本身已在 OTel/Langfuse 侧统计，不重复算）
        if (usage != null) {
            safeTag(ObservabilityAttributes.GEN_AI_REQUEST_MODEL, model);
        }
    }

    @Override
    public void recordCounter(String name, double amount, String... tags) {
        delegate.recordCounter(name, amount, tags);
    }

    @Override
    public void recordDuration(String name, long durationMs, String... tags) {
        delegate.recordDuration(name, durationMs, tags);
    }

    @Override
    public boolean isEnabled() {
        return delegate.isEnabled();
    }

    @Override
    public TraceContextView currentTraceContext(String traceId) {
        return delegate.currentTraceContext(traceId);
    }

    @Override
    public String activeSpanId() {
        return delegate.activeSpanId();
    }

    /**
     * SkyWalking 侧读取本请求的 traceId（自检用）。
     *
     * @return SkyWalking traceId；不可用或非请求线程时为空串
     */
    public String skyWalkingTraceId() {
        try {
            return bridge.currentTraceId();
        } catch (RuntimeException ex) {
            return "";
        }
    }
    /**
     * SkyWalking 侧读取当前 spanId（自检用）。
     *
     * @return spanId；不可用时为 -1
     */
    public int skyWalkingSpanId() {
        try {
            return bridge.currentSpanId();
        } catch (RuntimeException ex) {
            return -1;
        }
    }

    /**
     * 桥是否可用（等价于 Agent 是否真的挂上了）。
     *
     * @return 可用返回 true
     */
    public boolean bridgeAvailable() {
        return bridge.available();
    }

    /**
     * 组合句柄：同时结束 SkyWalking span 与既有 span。
     *
     * <p>顺序：先给 SkyWalking 写错误标记、再结束它，最后结束既有 span —— 因为
     * {@code ActiveSpan.error}/{@code tag} 必须作用在「当前活动 span」上，
     * 一旦 stop 就写不进去了。</p>
     */
    private final class CompositeScope implements SpanScope {

        private final SpanScope delegateScope;
        private final SkyWalkingSpanBridge.SkyWalkingSpanHandle handle;
        private boolean closed;

        private CompositeScope(SpanScope delegateScope, SkyWalkingSpanBridge.SkyWalkingSpanHandle handle) {
            this.delegateScope = delegateScope;
            this.handle = handle;
        }

        @Override
        public void attribute(String key, String value) {
            delegateScope.attribute(key, value);
            safeTag(key, value);
        }

        @Override
        public void recordError(Throwable throwable) {
            delegateScope.recordError(throwable);
            if (throwable != null) {
                try {
                    bridge.errorActiveSpan(throwable.getClass().getSimpleName());
                } catch (RuntimeException ex) {
                    log.debug("[skywalking] 标记错误失败（忽略）：{}", ex.toString());
                }
            }
        }

        @Override
        public String traceId() {
            return delegateScope.traceId();
        }

        @Override
        public String spanId() {
            return delegateScope.spanId();
        }

        @Override
        public boolean sampled() {
            return delegateScope.sampled();
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                handle.close();
            } catch (RuntimeException ex) {
                log.debug("[skywalking] 结束 span 失败（忽略）：{}", ex.toString());
            } finally {
                delegateScope.close();
            }
        }
    }
}
