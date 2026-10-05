package com.aicode.framework.observability.dto;

import com.aicode.framework.observability.domain.LlmCostSummaryView;

import java.time.Instant;
import java.util.List;

/**
 * LLM 成本汇总响应体（Week 18）。
 *
 * @param generatedAt           汇总时间
 * @param executionCount        参与聚合的执行记录条数
 * @param models                按模型的聚合结果
 * @param totalEstimatedCostUsd 已配价模型成本合计；全未配价时为 null
 */
public record LlmCostSummaryResponse(
        Instant generatedAt,
        int executionCount,
        List<ModelCost> models,
        Double totalEstimatedCostUsd
) {

    /**
     * 单模型成本。
     *
     * @param model                 模型名
     * @param executionCount        执行记录条数
     * @param promptTokens          提示 token 合计
     * @param completionTokens      补全 token 合计
     * @param totalTokens           总 token 合计
     * @param inputPricePerMillion  提示单价（未配价为 null）
     * @param outputPricePerMillion 补全单价（未配价为 null）
     * @param estimatedCostUsd      估算成本（未配价为 null，不用 0 冒充未知）
     */
    public record ModelCost(
            String model,
            int executionCount,
            int promptTokens,
            int completionTokens,
            int totalTokens,
            Double inputPricePerMillion,
            Double outputPricePerMillion,
            Double estimatedCostUsd
    ) {
    }

    /**
     * 由领域视图转换。
     *
     * @param view 领域视图，非 null
     * @return 响应体
     */
    public static LlmCostSummaryResponse from(LlmCostSummaryView view) {
        return new LlmCostSummaryResponse(
                view.generatedAt(),
                view.executionCount(),
                view.models().stream()
                        .map(model -> new ModelCost(
                                model.model(),
                                model.executionCount(),
                                model.promptTokens(),
                                model.completionTokens(),
                                model.totalTokens(),
                                model.inputPricePerMillion(),
                                model.outputPricePerMillion(),
                                model.estimatedCostUsd()))
                        .toList(),
                view.totalEstimatedCostUsd());
    }
}
