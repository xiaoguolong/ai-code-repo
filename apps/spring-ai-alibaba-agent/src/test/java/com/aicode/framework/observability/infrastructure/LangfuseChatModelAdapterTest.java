package com.aicode.framework.observability.infrastructure;

import com.aicode.core.domain.model.ChatMessage;
import com.aicode.core.domain.model.ChatOptions;
import com.aicode.core.domain.model.ChatResult;
import com.aicode.core.domain.model.FinishReason;
import com.aicode.core.domain.model.MessageRole;
import com.aicode.core.domain.model.TokenUsage;
import com.aicode.core.domain.model.ToolDefinition;
import com.aicode.core.domain.port.ChatModelPort;
import com.aicode.framework.observability.GuardrailStub;
import com.aicode.framework.observability.LangfuseTestFixtures;
import com.aicode.framework.observability.OtelSdkTestSupport;
import com.aicode.framework.observability.domain.AgentObservabilityPort;
import com.aicode.framework.observability.domain.LangfuseAttributes;
import com.aicode.framework.observability.domain.ModelPrice;
import com.aicode.framework.observability.domain.ObservabilityAttributes;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LLM 调用链的 Langfuse generation 观测测试（Week 18）。
 *
 * <p>覆盖：观测类型显式落 generation、模型与参数、真实 usage、按价目表算出的 cost、Prompt 版本关联、
 * 正文开关，以及「关掉 Langfuse 后 Week 17 行为一字不变」。</p>
 */
class LangfuseChatModelAdapterTest extends OtelSdkTestSupport {

    private SimpleMeterRegistry registry;
    private AgentObservabilityPort port;
    private RecordingChatModel delegate;
    private LangfusePromptTracker tracker;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        port = new MicrometerObservabilityAdapter(tracer, registry);
        delegate = new RecordingChatModel();
        tracker = new LangfusePromptTracker();
    }

    @Test
    void writesGenerationObservationWithRealUsageAndCost() {
        ObservableChatModelAdapter adapter = adapter(true, priced("deepseek-chat"));

        adapter.chat(messages("你是医疗助手"), options(), List.of());

        SpanData span = finishedSpan(ObservableChatModelAdapter.SPAN_NAME);
        assertThat(string(span, LangfuseAttributes.OBSERVATION_TYPE)).isEqualTo(LangfuseAttributes.TYPE_GENERATION);
        assertThat(string(span, LangfuseAttributes.OBSERVATION_MODEL_NAME)).isEqualTo("deepseek-chat");
        assertThat(string(span, LangfuseAttributes.OBSERVATION_MODEL_PARAMETERS))
                .isEqualTo("{\"temperature\":0.7,\"max_tokens\":2048}");
        assertThat(string(span, LangfuseAttributes.OBSERVATION_USAGE_DETAILS))
                .isEqualTo("{\"input\":1000,\"output\":2000,\"total\":3000}");
        assertThat(string(span, LangfuseAttributes.OBSERVATION_COST_DETAILS))
                .isEqualTo("{\"input\":0.00027,\"output\":0.0022,\"total\":0.00247}");

        assertThat(registry.find("llm.cost.usd")
                .tag(ObservabilityAttributes.GEN_AI_REQUEST_MODEL, "deepseek-chat")
                .counter().count()).isEqualTo(0.00247);
    }

    @Test
    void skipsUsageAndCostWhenUpstreamUsageUnknown() {
        delegate.result = new ChatResult("答案", List.of(), FinishReason.STOP, TokenUsage.unknown(), "deepseek-chat");
        ObservableChatModelAdapter adapter = adapter(true, priced("deepseek-chat"));

        adapter.chat(messages("你是医疗助手"), options(), List.of());

        SpanData span = finishedSpan(ObservableChatModelAdapter.SPAN_NAME);
        assertThat(string(span, LangfuseAttributes.OBSERVATION_USAGE_DETAILS)).isNull();
        assertThat(string(span, LangfuseAttributes.OBSERVATION_COST_DETAILS)).isNull();
        assertThat(registry.find("llm.cost.usd").counter()).isNull();
    }

    @Test
    void omitsCostWhenModelHasNoConfiguredPrice() {
        ObservableChatModelAdapter adapter = adapter(true, Map.of());

        adapter.chat(messages("你是医疗助手"), options(), List.of());

        SpanData span = finishedSpan(ObservableChatModelAdapter.SPAN_NAME);
        assertThat(string(span, LangfuseAttributes.OBSERVATION_USAGE_DETAILS))
                .isEqualTo("{\"input\":1000,\"output\":2000,\"total\":3000}");
        assertThat(string(span, LangfuseAttributes.OBSERVATION_COST_DETAILS)).isNull();
    }

    @Test
    void linksPromptNameAndVersionWhenSystemMessageMatchesLoadedPrompt() {
        tracker.recordLoaded("medical-report", "3", "你是医疗助手");
        ObservableChatModelAdapter adapter = adapter(true, Map.of());

        adapter.chat(messages("你是医疗助手"), options(), List.of());

        SpanData span = finishedSpan(ObservableChatModelAdapter.SPAN_NAME);
        assertThat(string(span, LangfuseAttributes.OBSERVATION_PROMPT_NAME)).isEqualTo("medical-report");
        assertThat(string(span, LangfuseAttributes.OBSERVATION_PROMPT_VERSION)).isEqualTo("3");
    }

    @Test
    void doesNotLinkPromptWhenSystemMessageDiffers() {
        tracker.recordLoaded("medical-report", "3", "你是医疗助手");
        ObservableChatModelAdapter adapter = adapter(true, Map.of());

        adapter.chat(messages("你是另一个助手"), options(), List.of());

        SpanData span = finishedSpan(ObservableChatModelAdapter.SPAN_NAME);
        assertThat(string(span, LangfuseAttributes.OBSERVATION_PROMPT_NAME)).isNull();
        assertThat(string(span, LangfuseAttributes.OBSERVATION_PROMPT_VERSION)).isNull();
    }

    @Test
    void capturesMaskedContentOnlyWhenEnabled() {
        ObservableChatModelAdapter capturing = adapter(true, Map.of());

        capturing.chat(messages("你是医疗助手", "患者张三 13812345678 复诊"), options(), List.of());

        SpanData captured = finishedSpan(ObservableChatModelAdapter.SPAN_NAME);
        assertThat(string(captured, LangfuseAttributes.OBSERVATION_INPUT))
                .contains("138****5678")
                .doesNotContain("13812345678");
        assertThat(string(captured, LangfuseAttributes.OBSERVATION_OUTPUT)).contains("答案");

        ObservableChatModelAdapter plain = adapter(false, Map.of());
        plain.chat(messages("你是医疗助手"), options(), List.of());

        SpanData notCaptured = finishedSpan(ObservableChatModelAdapter.SPAN_NAME);
        assertThat(string(notCaptured, LangfuseAttributes.OBSERVATION_INPUT)).isNull();
        assertThat(string(notCaptured, LangfuseAttributes.OBSERVATION_OUTPUT)).isNull();
    }

    @Test
    void keepsWeek17BehaviourWhenLangfuseSupportIsDisabled() {
        ObservableChatModelAdapter adapter = new ObservableChatModelAdapter(delegate, port);

        adapter.chat(messages("你是医疗助手"), options(), List.of());

        SpanData span = finishedSpan(ObservableChatModelAdapter.SPAN_NAME);
        assertThat(span.getAttributes().asMap().keySet())
                .noneMatch(key -> key.getKey().startsWith("langfuse."));
        assertThat(string(span, ObservabilityAttributes.GEN_AI_USAGE_TOTAL_TOKENS)).isEqualTo("3000");
        assertThat(registry.find("llm.cost.usd").counter()).isNull();
    }

    private ObservableChatModelAdapter adapter(boolean captureContent, Map<String, ModelPrice> prices) {
        return new ObservableChatModelAdapter(delegate, port,
                LangfuseTestFixtures.generationSupport(
                        LangfuseTestFixtures.tracing(captureContent, prices), tracker, GuardrailStub.active()));
    }

    private Map<String, ModelPrice> priced(String model) {
        return Map.of(model, new ModelPrice(0.27, 1.10));
    }

    private List<ChatMessage> messages(String systemContent) {
        return messages(systemContent, "你好");
    }

    private List<ChatMessage> messages(String systemContent, String userContent) {
        return List.of(
                new ChatMessage(MessageRole.SYSTEM, systemContent),
                new ChatMessage(MessageRole.USER, userContent));
    }

    private ChatOptions options() {
        return new ChatOptions("deepseek-chat", 0.7, 2048);
    }

    private String string(SpanData span, String key) {
        return span.getAttributes().get(AttributeKey.stringKey(key));
    }

    /** 可编排结果的假模型端口：默认返回真实量级的 usage，便于断言成本。 */
    private static final class RecordingChatModel implements ChatModelPort {

        private ChatResult result = new ChatResult("答案", List.of(), FinishReason.STOP,
                new TokenUsage(1_000, 2_000, 3_000), "deepseek-chat");

        @Override
        public ChatResult chat(List<ChatMessage> messages, ChatOptions options, List<ToolDefinition> tools) {
            return result;
        }
    }
}
