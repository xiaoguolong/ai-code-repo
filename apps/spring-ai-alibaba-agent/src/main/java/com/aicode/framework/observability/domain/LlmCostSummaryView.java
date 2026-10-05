package com.aicode.framework.observability.domain;

import java.time.Instant;
import java.util.List;

/**
 * LLM 成本汇总视图（Week 18）：按模型聚合执行记录里的真实 Token 与估算成本。
 *
 * <p>刻意不依赖 Langfuse：数据源是平台自己的执行记录，所以 Langfuse 停机时成本核算依然可用。</p>
 *
 * @param generatedAt           汇总时间
 * @param executionCount        参与聚合的执行记录条数
 * @param models                按模型聚合的结果（按模型名字典序）
 * @param totalEstimatedCostUsd 已配价模型的成本合计；全部未配价时为 null
 */
public record LlmCostSummaryView(
        Instant generatedAt,
        int executionCount,
        List<ModelCost> models,
        Double totalEstimatedCostUsd
) {

    /**
     * 单个模型的聚合结果。
     *
     * @param model                 模型名（缺失时归入 {@code unknown}）
     * @param executionCount        该模型的执行记录条数
     * @param promptTokens          提示 token 合计
     * @param completionTokens      补全 token 合计
     * @param totalTokens           总 token 合计
     * @param inputPricePerMillion  配置的提示单价，未配价时为 null
     * @param outputPricePerMillion 配置的补全单价，未配价时为 null
     * @param estimatedCostUsd      估算成本，未配价时为 null（不用 0 冒充未知）
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
}
