package com.aicode.framework.observability.infrastructure;

import com.aicode.core.domain.model.ToolCall;
import com.aicode.core.domain.model.ToolDefinition;
import com.aicode.core.domain.model.ToolResult;
import com.aicode.core.domain.port.ToolPort;
import com.aicode.framework.observability.domain.AgentObservabilityPort;
import com.aicode.framework.observability.domain.ObservabilityAttributes;
import com.aicode.framework.observability.domain.SpanKind;
import com.aicode.framework.observability.domain.SpanScope;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool 调用链埋点装饰器（Week 17）。
 *
 * <p>包在 {@link ToolPort} 最外层：一次工具执行 = 一个 {@code tool.call} span +
 * {@code tool.call.count} 指标。用于回答「哪个工具慢、哪个工具失败」。所有工具执行都经过
 * ToolPort 这个唯一咽喉点，因此这里埋点可一次覆盖 Graph / Workflow / Multi-Agent 全部 Agent 类型。</p>
 *
 * <p>属性只写工具名、调用 ID 与结果字符数；<b>不写工具参数与结果正文</b>
 * （医疗场景下参数与结果可能含患者标识）。</p>
 */
public class ObservableToolPort implements ToolPort {

    /** tool.call span 名。 */
    public static final String SPAN_NAME = "tool.call";

    private static final String COUNTER_CALL = "tool.call.count";

    private final ToolPort delegate;
    private final AgentObservabilityPort observability;

    public ObservableToolPort(ToolPort delegate, AgentObservabilityPort observability) {
        this.delegate = delegate;
        this.observability = observability;
    }

    @Override
    public List<ToolDefinition> definitions() {
        return delegate.definitions();
    }

    /**
     * 执行一次带观测的工具调用；失败时标记 span 错误后原样抛出，不改变调用方语义。
     */
    @Override
    public ToolResult execute(ToolCall call) {
        if (!observability.isEnabled()) {
            return delegate.execute(call);
        }
        String toolName = call == null || call.name() == null ? "" : call.name();
        Map<String, String> attributes = new LinkedHashMap<>();
        if (!toolName.isBlank()) {
            attributes.put(ObservabilityAttributes.TOOL_NAME, toolName);
        }
        if (call != null && call.id() != null && !call.id().isBlank()) {
            attributes.put(ObservabilityAttributes.TOOL_CALL_ID, call.id());
        }

        long startedAt = System.nanoTime();
        try (SpanScope scope = observability.openSpan(SpanKind.TOOL_CALL, SPAN_NAME, attributes)) {
            try {
                ToolResult result = delegate.execute(call);
                int chars = result == null || result.output() == null ? 0 : result.output().length();
                scope.attribute(ObservabilityAttributes.TOOL_RESULT_CHARS, String.valueOf(chars));
                observability.recordCounter(COUNTER_CALL, 1.0,
                        ObservabilityAttributes.TOOL_NAME, toolName,
                        ObservabilityAttributes.TOOL_OUTCOME, ObservabilityAttributes.OUTCOME_SUCCESS);
                return result;
            } catch (RuntimeException ex) {
                scope.recordError(ex);
                observability.recordCounter(COUNTER_CALL, 1.0,
                        ObservabilityAttributes.TOOL_NAME, toolName,
                        ObservabilityAttributes.TOOL_OUTCOME, ObservabilityAttributes.OUTCOME_FAILURE);
                throw ex;
            } finally {
                scope.attribute(ObservabilityAttributes.DURATION_MS, String.valueOf(elapsedMs(startedAt)));
            }
        }
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
