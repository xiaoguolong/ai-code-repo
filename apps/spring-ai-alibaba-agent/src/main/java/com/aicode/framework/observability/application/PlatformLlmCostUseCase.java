package com.aicode.framework.observability.application;

import com.aicode.framework.observability.domain.LlmCostSummaryView;
import com.aicode.framework.observability.domain.LlmCost;
import com.aicode.framework.observability.domain.ModelPrice;
import com.aicode.framework.observability.domain.ModelPriceCatalog;
import com.aicode.framework.platform.domain.exception.PlatformAccessDeniedException;
import com.aicode.framework.platform.domain.model.ExecutionRecord;
import com.aicode.framework.platform.domain.port.ExecutionRecordPort;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import org.springframework.stereotype.Service;

import com.aicode.core.domain.model.TokenUsage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * LLM 成本汇总用例（Week 18）。
 *
 * <p>刻意<b>不依赖 Langfuse</b>：数据源是平台自己的执行记录（Week 16 已落库），
 * 单价来自配置价目表，因此 Langfuse 停机时成本核算依然可用，
 * 也便于在没有可观测平台的环境里先做成本治理。</p>
 *
 * <p>权限：仅管理员（成本与用量属经营数据）。</p>
 */
@Service
public class PlatformLlmCostUseCase {

    /** 模型名缺失时的归集桶，避免出现空模型名行。 */
    static final String UNKNOWN_MODEL = "unknown";

    private final ExecutionRecordPort executionRecordPort;
    private final ModelPriceCatalog modelPriceCatalog;
    private final PlatformPermissionChecker permissionChecker;

    public PlatformLlmCostUseCase(
            ExecutionRecordPort executionRecordPort,
            ModelPriceCatalog modelPriceCatalog,
            PlatformPermissionChecker permissionChecker
    ) {
        this.executionRecordPort = executionRecordPort;
        this.modelPriceCatalog = modelPriceCatalog;
        this.permissionChecker = permissionChecker;
    }

    /**
     * 按模型聚合 Token 与估算成本。
     *
     * @param userId 调用者用户 ID（必须是管理员）
     * @return 汇总视图；无执行记录时 models 为空列表、合计为 null
     * @throws PlatformAccessDeniedException 非管理员调用
     */
    public LlmCostSummaryView summarize(long userId) {
        if (!permissionChecker.isAdmin(userId)) {
            throw new PlatformAccessDeniedException("admin role required for llm cost summary");
        }
        List<ExecutionRecord> records = executionRecordPort.listAll();
        Map<String, Accumulator> buckets = new TreeMap<>();

        for (ExecutionRecord record : records) {
            String model = record.model() == null || record.model().isBlank() ? UNKNOWN_MODEL : record.model().trim();
            Accumulator accumulator = buckets.computeIfAbsent(model, key -> new Accumulator());
            TokenUsage usage = record.usage() == null ? TokenUsage.unknown() : record.usage();
            accumulator.add(usage);
        }

        List<LlmCostSummaryView.ModelCost> models = new ArrayList<>();
        double totalCost = 0d;
        boolean anyCost = false;
        for (Map.Entry<String, Accumulator> entry : buckets.entrySet()) {
            String model = entry.getKey();
            Accumulator accumulator = entry.getValue();
            Optional<ModelPrice> price = modelPriceCatalog.find(model);
            Double cost = price
                    .map(value -> estimate(value, accumulator).total())
                    .orElse(null);
            if (cost != null) {
                totalCost += cost;
                anyCost = true;
            }
            models.add(new LlmCostSummaryView.ModelCost(
                    model,
                    accumulator.count,
                    accumulator.promptTokens,
                    accumulator.completionTokens,
                    accumulator.totalTokens,
                    price.map(ModelPrice::inputPerMillion).orElse(null),
                    price.map(ModelPrice::outputPerMillion).orElse(null),
                    cost));
        }

        return new LlmCostSummaryView(
                Instant.now(),
                records.size(),
                List.copyOf(models),
                anyCost ? LlmCost.roundUsd(totalCost) : null);
    }

    /** 用聚合后的 token 与单价算成本（口径与单次调用一致：USD / 百万 token，6 位小数）。 */
    private LlmCost estimate(ModelPrice price, Accumulator accumulator) {
        double input = accumulator.promptTokens / 1_000_000d * price.inputPerMillion();
        double output = accumulator.completionTokens / 1_000_000d * price.outputPerMillion();
        return LlmCost.of(input, output);
    }

    /** 单模型累加器。 */
    private static final class Accumulator {

        private int count;
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;

        private void add(TokenUsage usage) {
            count++;
            promptTokens += usage.promptTokens();
            completionTokens += usage.completionTokens();
            totalTokens += usage.totalTokens();
        }
    }
}
