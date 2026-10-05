package com.aicode.framework.observability.infrastructure;

import com.aicode.core.domain.model.TokenUsage;
import com.aicode.framework.observability.domain.LangfuseAttributes;
import com.aicode.framework.observability.domain.LlmCost;
import com.aicode.framework.observability.domain.LlmGenerationRecord;
import com.aicode.framework.observability.domain.PromptReference;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Langfuse generation 属性映射测试（Week 18）。
 *
 * <p>核心契约：{@code usage_details} / {@code cost_details} / {@code model.parameters} / {@code input} / {@code output}
 * 都是 <b>一个 JSON 字符串属性</b>（Langfuse 对非 string 值会告警并丢弃）；观测类型必须显式写为 generation，
 * 不能依赖 Langfuse 用 model 兜底推断。这些断言就是「Langfuse 里不为空」的保证。</p>
 */
class LangfuseGenerationAttributesTest {

    private static final LlmGenerationRecord FULL = new LlmGenerationRecord(
            "deepseek-v4-pro",
            "{\"temperature\":0.7,\"max_tokens\":2048}",
            new TokenUsage(11, 22, 33),
            new LlmCost(0.000003, 0.000024, 0.000027),
            new PromptReference("medical-report", "3"),
            "[{\"role\":\"system\",\"content\":\"你是医疗助手\"}]",
            "{\"content\":\"建议复诊\"}");

    @Test
    void mapsGenerationObservationWithJsonStringAttributes() {
        Map<String, String> attributes = LangfuseGenerationAttributes.toAttributes(FULL);

        assertThat(attributes.get(LangfuseAttributes.OBSERVATION_TYPE)).isEqualTo(LangfuseAttributes.TYPE_GENERATION);
        assertThat(attributes.get(LangfuseAttributes.OBSERVATION_MODEL_NAME)).isEqualTo("deepseek-v4-pro");
        assertThat(attributes.get(LangfuseAttributes.OBSERVATION_MODEL_PARAMETERS))
                .isEqualTo("{\"temperature\":0.7,\"max_tokens\":2048}");
        assertThat(attributes.get(LangfuseAttributes.OBSERVATION_USAGE_DETAILS))
                .isEqualTo("{\"input\":11,\"output\":22,\"total\":33}");
        assertThat(attributes.get(LangfuseAttributes.OBSERVATION_COST_DETAILS))
                .isEqualTo("{\"input\":0.000003,\"output\":0.000024,\"total\":0.000027}");
        assertThat(attributes.get(LangfuseAttributes.OBSERVATION_PROMPT_NAME)).isEqualTo("medical-report");
        assertThat(attributes.get(LangfuseAttributes.OBSERVATION_PROMPT_VERSION)).isEqualTo("3");
        assertThat(attributes.get(LangfuseAttributes.OBSERVATION_INPUT)).isEqualTo(FULL.inputJson());
        assertThat(attributes.get(LangfuseAttributes.OBSERVATION_OUTPUT)).isEqualTo(FULL.outputJson());
    }

    @Test
    void omitsUsageAndCostWhenUsageUnknown() {
        LlmGenerationRecord record = new LlmGenerationRecord(
                "deepseek-v4-pro", null, TokenUsage.unknown(), null, null, null, null);

        Map<String, String> attributes = LangfuseGenerationAttributes.toAttributes(record);

        assertThat(attributes).doesNotContainKeys(
                LangfuseAttributes.OBSERVATION_USAGE_DETAILS,
                LangfuseAttributes.OBSERVATION_COST_DETAILS);
        assertThat(attributes.get(LangfuseAttributes.OBSERVATION_TYPE)).isEqualTo(LangfuseAttributes.TYPE_GENERATION);
    }

    @Test
    void omitsOptionalKeysWhenAbsent() {
        LlmGenerationRecord record = new LlmGenerationRecord(
                "", null, new TokenUsage(1, 2, 3), null, null, null, null);

        Map<String, String> attributes = LangfuseGenerationAttributes.toAttributes(record);

        assertThat(attributes).doesNotContainKeys(
                LangfuseAttributes.OBSERVATION_MODEL_NAME,
                LangfuseAttributes.OBSERVATION_COST_DETAILS,
                LangfuseAttributes.OBSERVATION_PROMPT_NAME,
                LangfuseAttributes.OBSERVATION_PROMPT_VERSION,
                LangfuseAttributes.OBSERVATION_INPUT,
                LangfuseAttributes.OBSERVATION_OUTPUT);
        assertThat(attributes.get(LangfuseAttributes.OBSERVATION_USAGE_DETAILS))
                .isEqualTo("{\"input\":1,\"output\":2,\"total\":3}");
    }

    @Test
    void neverPutsRawModelParametersWhenBlank() {
        LlmGenerationRecord record = new LlmGenerationRecord(
                "m", "   ", new TokenUsage(1, 1, 2), null, null, null, null);

        assertThat(LangfuseGenerationAttributes.toAttributes(record))
                .doesNotContainKey(LangfuseAttributes.OBSERVATION_MODEL_PARAMETERS);
    }
}
