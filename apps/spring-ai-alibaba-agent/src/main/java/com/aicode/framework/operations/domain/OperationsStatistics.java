package com.aicode.framework.operations.domain;

import com.aicode.framework.observability.domain.ModelPrice;
import com.aicode.framework.observability.domain.ModelPriceCatalog;
import com.aicode.framework.operations.domain.model.AgentRunSummary;
import com.aicode.framework.operations.domain.model.OperationsSummary;
import com.aicode.framework.platform.domain.model.ExecutionRecord;
import com.aicode.framework.platform.domain.model.ExecutionStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运营统计口径（Week 24）——**唯一**的聚合实现。
 *
 * <p>把「窗口内执行记录」算成 {@link OperationsSummary} 与 {@link AgentRunSummary}。
 * H2/内存/真库三种适配器只负责按时间窗取记录，本类保证口径一致（规范 3.3：业务规则不进适配器）。</p>
 *
 * <p>成本：逐条按 {@link ModelPriceCatalog} 的单价累加；价目表没有该模型时该条不计成本，
 * 并把 {@code costConfigured} 置 false —— 宁可显示「部分模型未配价」，也不要给出一个看起来
 * 精确但少算的成本数字。</p>
 */
public final class OperationsStatistics {

    /** USD / 百万 token 的换算基数。 */
    static final double TOKENS_PER_MILLION = 1_000_000.0;

    /**
     * 汇总统计。
     *
     * @param records       窗口内执行记录（可为空列表）
     * @param priceCatalog  模型价目表（null 视为空表）
     * @param truncated     是否因读取上限被截断
     * @return 汇总数字
     */
    public OperationsSummary summarize(
            List<ExecutionRecord> records,
            ModelPriceCatalog priceCatalog,
            boolean truncated
    ) {
        List<ExecutionRecord> safe = records == null ? List.of() : records;
        ModelPriceCatalog catalog = priceCatalog == null ? ModelPriceCatalog.empty() : priceCatalog;

        long completed = 0;
        long failed = 0;
        long pending = 0;
        long rejected = 0;
        long running = 0;
        long promptTokens = 0;
        long completionTokens = 0;
        double cost = 0.0;
        boolean costConfigured = true;

        for (ExecutionRecord record : safe) {
            switch (record.status()) {
                case COMPLETED -> completed++;
                case FAILED -> failed++;
                case PENDING_APPROVAL -> pending++;
                case REJECTED -> rejected++;
                case RUNNING -> running++;
            }
            int prompt = record.usage() == null ? 0 : record.usage().promptTokens();
            int completion = record.usage() == null ? 0 : record.usage().completionTokens();
            promptTokens += prompt;
            completionTokens += completion;
            if (prompt > 0 || completion > 0) {
                ModelPrice price = catalog.find(record.model()).orElse(null);
                if (price == null) {
                    costConfigured = false;
                } else {
                    cost += prompt / TOKENS_PER_MILLION * price.inputPerMillion()
                            + completion / TOKENS_PER_MILLION * price.outputPerMillion();
                }
            }
        }

        return new OperationsSummary(
                safe.size(), completed, failed, pending, rejected, running,
                promptTokens, completionTokens, round6(cost), costConfigured, truncated);
    }

    /**
     * 按 Agent 分组统计。
     *
     * @param records 窗口内执行记录
     * @return 按执行数倒序、同数按 agentKey 升序的分组列表
     */
    public List<AgentRunSummary> byAgent(List<ExecutionRecord> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        Map<String, long[]> grouped = new LinkedHashMap<>();
        for (ExecutionRecord record : records) {
            long[] counters = grouped.computeIfAbsent(record.agentKey(), key -> new long[3]);
            counters[0]++;
            if (record.status() == ExecutionStatus.FAILED) {
                counters[1]++;
            }
            if (record.usage() != null) {
                counters[2] += record.usage().totalTokens();
            }
        }
        List<AgentRunSummary> summaries = new ArrayList<>();
        grouped.forEach((agentKey, counters) ->
                summaries.add(new AgentRunSummary(agentKey, counters[0], counters[1], counters[2])));
        summaries.sort(Comparator.comparingLong(AgentRunSummary::runs).reversed()
                .thenComparing(AgentRunSummary::agentKey));
        return List.copyOf(summaries);
    }

    /**
     * 只算 token 合计（用于上一等长窗口的对比）。
     *
     * @param records 执行记录
     * @return prompt + completion 合计
     */
    public long tokens(List<ExecutionRecord> records) {
        if (records == null) {
            return 0L;
        }
        long total = 0L;
        for (ExecutionRecord record : records) {
            if (record.usage() != null) {
                total += (long) record.usage().promptTokens() + record.usage().completionTokens();
            }
        }
        return total;
    }

    private double round6(double value) {
        return Math.round(value * 1_000_000.0) / 1_000_000.0;
    }
}
