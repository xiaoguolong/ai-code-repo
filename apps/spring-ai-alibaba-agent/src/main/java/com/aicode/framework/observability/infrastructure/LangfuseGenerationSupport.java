package com.aicode.framework.observability.infrastructure;

import com.aicode.core.domain.model.ChatMessage;
import com.aicode.core.domain.model.ChatOptions;
import com.aicode.core.domain.model.ChatResult;
import com.aicode.core.domain.model.TokenUsage;
import com.aicode.core.domain.model.ToolCall;
import com.aicode.core.domain.model.ToolResult;
import com.aicode.framework.observability.domain.AgentObservabilityPort;
import com.aicode.framework.observability.domain.LangfuseAttributes;
import com.aicode.framework.observability.domain.LlmCost;
import com.aicode.framework.observability.domain.LlmGenerationRecord;
import com.aicode.framework.observability.domain.PromptReference;
import com.aicode.framework.observability.domain.SpanScope;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Langfuse 观测支持（Week 18）：把「一次模型调用 / 工具调用」翻译成 Langfuse 观测属性。
 *
 * <p>它是端口装饰器（{@code ObservableChatModelAdapter} / {@code ObservableToolPort}）与一堆纯工具类之间的
 * 唯一协作点，职责有三：</p>
 * <ol>
 *   <li>判定开关：{@code langfuse.enabled=false} 时所有方法空转（{@link #disabled()}），装饰器行为回到 Week 17；</li>
 *   <li>组装 generation / tool 观测属性（含成本、Prompt 版本、可选的脱敏正文）；</li>
 *   <li>把成本回传给调用方记录 {@code llm.cost.usd} 指标（成本也能进 Prometheus 告警）。</li>
 * </ol>
 */
public final class LangfuseGenerationSupport {

    private static final String METRIC_COST = "llm.cost.usd";

    private final boolean enabled;
    private final LlmCostCalculator costCalculator;
    private final LangfuseContentPolicy contentPolicy;
    private final LangfusePromptTracker promptTracker;
    private final ObjectMapper objectMapper;

    /**
     * 关闭态实例：所有方法空转。
     *
     * @return 空实现
     */
    public static LangfuseGenerationSupport disabled() {
        return new LangfuseGenerationSupport(false, null, null, null, null);
    }

    /**
     * 按配置创建。
     *
     * @param properties    Langfuse 配置
     * @param costCalculator 成本计算器
     * @param contentPolicy  正文采集策略
     * @param promptTracker  Prompt 版本关联
     * @param objectMapper   JSON 序列化器（模型参数）
     */
    public LangfuseGenerationSupport(
            LangfuseProperties properties,
            LlmCostCalculator costCalculator,
            LangfuseContentPolicy contentPolicy,
            LangfusePromptTracker promptTracker,
            ObjectMapper objectMapper
    ) {
        this(properties.resolvedEnabled(), costCalculator, contentPolicy, promptTracker, objectMapper);
    }

    private LangfuseGenerationSupport(
            boolean enabled,
            LlmCostCalculator costCalculator,
            LangfuseContentPolicy contentPolicy,
            LangfusePromptTracker promptTracker,
            ObjectMapper objectMapper
    ) {
        this.enabled = enabled;
        this.costCalculator = costCalculator;
        this.contentPolicy = contentPolicy;
        this.promptTracker = promptTracker;
        this.objectMapper = objectMapper;
    }

    /** Langfuse 追踪是否开启。 */
    public boolean enabled() {
        return enabled;
    }

    /**
     * 把一次模型调用写成 Langfuse generation 观测。
     *
     * @param scope    当前的 {@code llm.chat} span
     * @param messages 请求消息（内容采集开启时作为输入）
     * @param options  采样参数（写入 model.parameters）
     * @param result   模型返回（真实 token 与输出）
     * @return 估算成本；未开启、用量未知或未配单价时为空
     */
    public Optional<LlmCost> applyGeneration(
            SpanScope scope, List<ChatMessage> messages, ChatOptions options, ChatResult result) {
        if (!enabled || scope == null) {
            return Optional.empty();
        }
        String model = resolveModel(result, options);
        TokenUsage usage = result == null ? null : result.usage();
        Optional<LlmCost> cost = costCalculator.estimate(model, usage);
        PromptReference prompt = promptTracker == null ? null : promptTracker.match(systemContent(messages));

        LlmGenerationRecord record = new LlmGenerationRecord(
                model,
                modelParametersJson(options),
                usage,
                cost.orElse(null),
                prompt,
                contentPolicy != null && contentPolicy.enabled() ? contentPolicy.messagesJson(messages) : null,
                contentPolicy != null && contentPolicy.enabled() ? contentPolicy.completionJson(result) : null);

        LangfuseGenerationAttributes.toAttributes(record).forEach(scope::attribute);
        return cost;
    }

    /**
     * 把一次工具调用写成 Langfuse tool 观测（正文仅在采集开启时写入）。
     *
     * @param scope  当前的 {@code tool.call} span
     * @param call   工具调用（id / name / arguments）
     * @param result 工具结果
     */
    public void applyTool(SpanScope scope, ToolCall call, ToolResult result) {
        if (!enabled || scope == null) {
            return;
        }
        scope.attribute(LangfuseAttributes.OBSERVATION_TYPE, LangfuseAttributes.TYPE_TOOL);
        if (call != null && call.id() != null && !call.id().isBlank()) {
            scope.attribute(LangfuseAttributes.OBSERVATION_METADATA_PREFIX + "toolCallId", call.id());
        }
        if (contentPolicy == null || !contentPolicy.enabled()) {
            return;
        }
        if (call != null) {
            scope.attribute(LangfuseAttributes.OBSERVATION_INPUT, contentPolicy.toolInputJson(call));
        }
        if (result != null) {
            scope.attribute(LangfuseAttributes.OBSERVATION_OUTPUT, contentPolicy.toolOutputJson(result));
        }
    }

    /** 成本指标名（Prometheus 出口为 {@code llm_cost_usd_total}）。 */
    public static String costMetricName() {
        return METRIC_COST;
    }

    /** 真实生效模型优先取上游返回，缺失时回退请求参数。 */
    private String resolveModel(ChatResult result, ChatOptions options) {
        String fromResponse = result == null ? null : result.model();
        if (fromResponse != null && !fromResponse.isBlank()) {
            return fromResponse.trim();
        }
        return options == null ? null : options.model();
    }

    /** system 消息内容（Prompt 版本关联的判据）。 */
    private String systemContent(List<ChatMessage> messages) {
        if (messages == null) {
            return null;
        }
        return messages.stream()
                .filter(message -> message != null && message.role() == com.aicode.core.domain.model.MessageRole.SYSTEM)
                .map(ChatMessage::content)
                .filter(content -> content != null && !content.isBlank())
                .findFirst()
                .orElse(null);
    }

    /** 模型参数 JSON（供 Langfuse 展示与成本分档参考）。 */
    private String modelParametersJson(ChatOptions options) {
        if (options == null || objectMapper == null) {
            return null;
        }
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("temperature", options.temperature());
        parameters.put("max_tokens", options.maxTokens());
        try {
            return objectMapper.writeValueAsString(parameters);
        } catch (Exception ex) {
            return null;
        }
    }
}
