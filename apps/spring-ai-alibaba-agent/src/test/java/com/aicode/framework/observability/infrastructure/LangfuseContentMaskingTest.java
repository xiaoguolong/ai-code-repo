package com.aicode.framework.observability.infrastructure;

import com.aicode.core.domain.model.ChatMessage;
import com.aicode.core.domain.model.ChatResult;
import com.aicode.core.domain.model.FinishReason;
import com.aicode.core.domain.model.MessageRole;
import com.aicode.core.domain.model.TokenUsage;
import com.aicode.core.domain.model.ToolCall;
import com.aicode.core.domain.model.ToolResult;
import com.aicode.framework.observability.GuardrailStub;
import com.aicode.framework.observability.LangfuseTestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Langfuse 正文采集策略测试（Week 18）。
 *
 * <p>红线：默认关闭时<b>一个正文字段都不写</b>；开启时也必须先经 Guardrail 脱敏再截断；
 * guardrail 未启用时采集自动失效（fail-safe 到关闭），避免把未脱敏的患者数据送进可观测系统。</p>
 */
class LangfuseContentMaskingTest {

    private static final String MESSAGE = "患者张三 13812345678 血压偏高，请复诊";
    private static final String MASKED_MESSAGE = "患者张三 138****5678 血压偏高，请复诊";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void disabledPolicyProducesNoContentAtAll() {
        LangfuseContentPolicy policy = new LangfuseContentPolicy(
                LangfuseTestFixtures.enabled(), GuardrailStub.active(), objectMapper);

        assertThat(policy.enabled()).isFalse();
        assertThat(policy.messagesJson(messages())).isNull();
        assertThat(policy.completionJson(chatResult())).isNull();
        assertThat(policy.toolInputJson(toolCall())).isNull();
        assertThat(policy.toolOutputJson(new ToolResult("{\"name\":\"张三\"}"))).isNull();
    }

    @Test
    void failsSafeToDisabledWhenGuardrailIsOff() {
        LangfuseContentPolicy policy = new LangfuseContentPolicy(
                LangfuseTestFixtures.enabledWithContent(Map.of()), GuardrailStub.inactive(), objectMapper);

        assertThat(policy.enabled()).isFalse();
        assertThat(policy.messagesJson(messages())).isNull();
    }

    @Test
    void masksPiiThroughGuardrailBeforeSerializing() {
        GuardrailStub guardrail = GuardrailStub.active();
        LangfuseContentPolicy policy = new LangfuseContentPolicy(contentProperties(2_000), guardrail, objectMapper);

        assertThat(policy.enabled()).isTrue();
        String json = policy.messagesJson(messages());

        assertThat(json).contains(MASKED_MESSAGE);
        assertThat(json).doesNotContain("13812345678");
        assertThat(json).contains("\"role\":\"user\"");
        assertThat(guardrail.sanitizeCalls()).isPositive();
    }

    @Test
    void truncatesOverlongContentSoThatBatchesStayBounded() {
        LangfuseContentPolicy policy = new LangfuseContentPolicy(
                contentProperties(20), GuardrailStub.active(), objectMapper);

        String json = policy.messagesJson(List.of(new ChatMessage(MessageRole.USER, "x".repeat(200))));

        assertThat(json).contains("truncated");
        assertThat(json.length()).isLessThan(200);
    }

    @Test
    void serializesCompletionAndToolPayloads() {
        LangfuseContentPolicy policy = new LangfuseContentPolicy(
                contentProperties(2_000), GuardrailStub.active(), objectMapper);

        assertThat(policy.completionJson(chatResult()))
                .contains("建议复诊")
                .contains("PatientLookupTool");
        assertThat(policy.toolInputJson(toolCall()))
                .contains("query_patient")
                .contains("P001");
        assertThat(policy.toolOutputJson(new ToolResult("{\"bmi\":24}")))
                .contains("bmi");
    }

    private LangfuseProperties contentProperties(int maxChars) {
        LangfuseProperties base = LangfuseTestFixtures.enabledWithContent(Map.of());
        return new LangfuseProperties(
                base.enabled(), base.host(), base.publicKey(), base.secretKey(), base.projectId(),
                base.environment(), base.release(), base.captureContent(), maxChars, base.timeoutMs(),
                base.prompt(), base.modelPrices());
    }

    private List<ChatMessage> messages() {
        return List.of(
                new ChatMessage(MessageRole.SYSTEM, "你是医疗助手"),
                new ChatMessage(MessageRole.USER, MESSAGE));
    }

    private ChatResult chatResult() {
        return new ChatResult("建议复诊", List.of(toolCall()), FinishReason.STOP,
                new TokenUsage(1, 1, 2), "deepseek-v4-pro");
    }

    private ToolCall toolCall() {
        return new ToolCall("query_patient", "PatientLookupTool", "{\"patientId\":\"P001\"}");
    }
}
