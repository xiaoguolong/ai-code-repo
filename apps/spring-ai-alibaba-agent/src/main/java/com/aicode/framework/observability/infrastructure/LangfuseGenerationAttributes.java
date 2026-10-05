package com.aicode.framework.observability.infrastructure;

import com.aicode.core.domain.model.TokenUsage;
import com.aicode.framework.observability.domain.LangfuseAttributes;
import com.aicode.framework.observability.domain.LlmCost;
import com.aicode.framework.observability.domain.LlmGenerationRecord;
import com.aicode.framework.observability.domain.PromptReference;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 领域事实 → Langfuse generation 属性映射（Week 18，纯函数）。
 *
 * <p>五个 JSON 属性（usage_details / cost_details / model.parameters / input / output）必须是
 * <b>单个字符串</b>，这里统一负责序列化与数值格式，避免调用方各写一遍而格式漂移。</p>
 *
 * <p>空值语义：usage 未知（全 0）时不下发 usage / cost；未配单价（cost 为 null）时不下发 cost；
 * 未关联 Prompt 时不下发 prompt 键。宁可字段缺失，也不下发误导性的 0。</p>
 */
public final class LangfuseGenerationAttributes {

    private LangfuseGenerationAttributes() {
    }

    /**
     * 构造属性 Map（保持插入顺序，便于测试与人工核对）。
     *
     * @param record 生成事实，非 null
     * @return 属性键值对；无可用字段时只含观测类型
     */
    public static Map<String, String> toAttributes(LlmGenerationRecord record) {
        Map<String, String> attributes = new LinkedHashMap<>();
        // 显式声明观测类型：不依赖 Langfuse 用 model 兜底推断 generation
        attributes.put(LangfuseAttributes.OBSERVATION_TYPE, LangfuseAttributes.TYPE_GENERATION);

        if (notBlank(record.model())) {
            attributes.put(LangfuseAttributes.OBSERVATION_MODEL_NAME, record.model().trim());
        }
        if (notBlank(record.modelParametersJson())) {
            attributes.put(LangfuseAttributes.OBSERVATION_MODEL_PARAMETERS, record.modelParametersJson().trim());
        }

        TokenUsage usage = record.usage();
        boolean usageKnown = usage != null && usage.totalTokens() > 0;
        if (usageKnown) {
            attributes.put(LangfuseAttributes.OBSERVATION_USAGE_DETAILS, usageJson(usage));
            if (record.cost() != null) {
                attributes.put(LangfuseAttributes.OBSERVATION_COST_DETAILS, costJson(record.cost()));
            }
        }

        PromptReference prompt = record.prompt();
        if (prompt != null && notBlank(prompt.name()) && notBlank(prompt.version())) {
            attributes.put(LangfuseAttributes.OBSERVATION_PROMPT_NAME, prompt.name().trim());
            attributes.put(LangfuseAttributes.OBSERVATION_PROMPT_VERSION, prompt.version().trim());
        }

        if (notBlank(record.inputJson())) {
            attributes.put(LangfuseAttributes.OBSERVATION_INPUT, record.inputJson());
        }
        if (notBlank(record.outputJson())) {
            attributes.put(LangfuseAttributes.OBSERVATION_OUTPUT, record.outputJson());
        }
        return attributes;
    }

    /** Token 明细 JSON（互斥桶 input / output / total）。 */
    static String usageJson(TokenUsage usage) {
        return "{\"input\":" + usage.promptTokens()
                + ",\"output\":" + usage.completionTokens()
                + ",\"total\":" + usage.totalTokens() + "}";
    }

    /** 成本明细 JSON（USD，去掉多余尾零，Langfuse 侧按数值解析）。 */
    static String costJson(LlmCost cost) {
        return "{\"input\":" + number(cost.input())
                + ",\"output\":" + number(cost.output())
                + ",\"total\":" + number(cost.total()) + "}";
    }

    private static String number(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
