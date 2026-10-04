package com.aicode.framework.observability.infrastructure;

import com.aicode.core.domain.model.ChatMessage;
import com.aicode.core.domain.model.ChatOptions;
import com.aicode.core.domain.model.ChatResult;
import com.aicode.core.domain.model.ToolDefinition;
import com.aicode.core.domain.port.ChatModelPort;
import com.aicode.framework.observability.domain.AgentObservabilityPort;
import com.aicode.framework.observability.domain.ObservabilityAttributes;
import com.aicode.framework.observability.domain.SpanKind;
import com.aicode.framework.observability.domain.SpanScope;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 调用链埋点装饰器（Week 17）。
 *
 * <p>包在 {@link ChatModelPort} 外层：一次 {@code chat()} 往来 = 一个 {@code llm.chat} span +
 * {@code llm.call.count} / {@code llm.call.duration} / {@code llm.tokens.total} 指标。
 * 这是 LLM 应用<b>成本核算的最小单位</b>：Agent 一次执行可能调多轮模型，只看 agent.run 无法区分
 * 「模型慢」与「工具慢」，也无法按模型归集 token。</p>
 *
 * <p>属性只写模型名、结束原因、token 数与工具调用数量；<b>不写 Prompt 与输出正文</b>
 * （消息里可能含患者隐私，写进 span 等于复制到第三个系统）。</p>
 */
public class ObservableChatModelAdapter implements ChatModelPort {

    /** llm.chat span 名。 */
    public static final String SPAN_NAME = "llm.chat";

    private final ChatModelPort delegate;
    private final AgentObservabilityPort observability;

    public ObservableChatModelAdapter(ChatModelPort delegate, AgentObservabilityPort observability) {
        this.delegate = delegate;
        this.observability = observability;
    }

    /**
     * 发起一次带观测的聊天补全；失败时标记 span 错误后原样抛出，不改变调用方语义。
     */
    @Override
    public ChatResult chat(List<ChatMessage> messages, ChatOptions options, List<ToolDefinition> tools) {
        if (!observability.isEnabled()) {
            return delegate.chat(messages, options, tools);
        }
        String model = options == null ? null : options.model();
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put(ObservabilityAttributes.GEN_AI_SYSTEM, "spring-ai");
        if (model != null && !model.isBlank()) {
            attributes.put(ObservabilityAttributes.GEN_AI_REQUEST_MODEL, model);
        }

        long startedAt = System.nanoTime();
        try (SpanScope scope = observability.openSpan(SpanKind.LLM_CALL, SPAN_NAME, attributes)) {
            try {
                ChatResult result = delegate.chat(messages, options, tools);
                recordSuccess(scope, result, model);
                recordCall(resolvedModel(result, model), ObservabilityAttributes.OUTCOME_SUCCESS, startedAt);
                return result;
            } catch (RuntimeException ex) {
                scope.recordError(ex);
                recordCall(model, ObservabilityAttributes.OUTCOME_FAILURE, startedAt);
                throw ex;
            }
        }
    }

    /** 登记一次模型调用的次数与耗时指标（成功 / 失败共用，避免两处标签写法漂移）。 */
    private void recordCall(String model, String outcome, long startedAt) {
        String tag = MicrometerObservabilityAdapter.modelName(model);
        observability.recordCounter("llm.call.count", 1.0,
                ObservabilityAttributes.GEN_AI_REQUEST_MODEL, tag,
                ObservabilityAttributes.LLM_OUTCOME, outcome);
        observability.recordDuration("llm.call.duration", elapsedMs(startedAt),
                ObservabilityAttributes.GEN_AI_REQUEST_MODEL, tag,
                ObservabilityAttributes.LLM_OUTCOME, outcome);
    }

    /** 写入成功路径的 span 属性与 token 指标。 */
    private void recordSuccess(SpanScope scope, ChatResult result, String requestedModel) {
        if (result == null) {
            return;
        }
        String model = resolvedModel(result, requestedModel);
        scope.attribute(ObservabilityAttributes.GEN_AI_REQUEST_MODEL, model);
        scope.attribute(ObservabilityAttributes.GEN_AI_RESPONSE_FINISH_REASON, String.valueOf(result.finishReason()));
        if (!result.toolCalls().isEmpty()) {
            scope.attribute(ObservabilityAttributes.GEN_AI_TOOL_CALL_COUNT,
                    String.valueOf(result.toolCalls().size()));
        }
        var usage = result.usage();
        if (usage != null) {
            scope.attribute(ObservabilityAttributes.GEN_AI_USAGE_PROMPT_TOKENS,
                    String.valueOf(usage.promptTokens()));
            scope.attribute(ObservabilityAttributes.GEN_AI_USAGE_COMPLETION_TOKENS,
                    String.valueOf(usage.completionTokens()));
            scope.attribute(ObservabilityAttributes.GEN_AI_USAGE_TOTAL_TOKENS,
                    String.valueOf(usage.totalTokens()));
        }
        observability.recordTokenUsage(model, usage);
    }

    /** 模型名优先取上游返回（真实生效模型），缺失时回退到请求参数。 */
    private String resolvedModel(ChatResult result, String requestedModel) {
        String fromResponse = result == null ? null : result.model();
        if (fromResponse != null && !fromResponse.isBlank()) {
            return fromResponse;
        }
        return MicrometerObservabilityAdapter.modelName(requestedModel);
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
