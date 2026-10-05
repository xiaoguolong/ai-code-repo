package com.aicode.framework.observability.infrastructure;

import com.aicode.core.domain.model.TokenUsage;
import com.aicode.framework.observability.domain.LlmCost;
import com.aicode.framework.observability.domain.ModelPrice;
import com.aicode.framework.observability.domain.ModelPriceCatalog;

import java.util.Optional;

/**
 * LLM 成本计算（Week 18）。
 *
 * <p>口径：只按<b>真实 usage</b> × 配置单价计算；usage 未知（全 0）或模型未配单价时返回空，
 * 由调用方决定「不下发 cost」（Langfuse 可用服务端模型定义兜底，但应用不编造成本）。</p>
 */
public class LlmCostCalculator {

    private static final double TOKENS_PER_MILLION = 1_000_000d;

    private final ModelPriceCatalog catalog;

    public LlmCostCalculator(ModelPriceCatalog catalog) {
        this.catalog = catalog == null ? ModelPriceCatalog.empty() : catalog;
    }

    /**
     * 估算一次调用成本（USD）。
     *
     * @param model 模型名，可为空（为空则算不出）
     * @param usage 真实用量，可为 null
     * @return 成本；模型未配价或用量未知时为空
     */
    public Optional<LlmCost> estimate(String model, TokenUsage usage) {
        if (usage == null || usage.totalTokens() <= 0) {
            return Optional.empty();
        }
        Optional<ModelPrice> price = catalog.find(model);
        if (price.isEmpty()) {
            return Optional.empty();
        }
        double input = usage.promptTokens() / TOKENS_PER_MILLION * price.get().inputPerMillion();
        double output = usage.completionTokens() / TOKENS_PER_MILLION * price.get().outputPerMillion();
        return Optional.of(LlmCost.of(input, output));
    }
}
