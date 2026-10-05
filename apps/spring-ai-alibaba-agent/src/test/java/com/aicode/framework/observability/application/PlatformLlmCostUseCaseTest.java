package com.aicode.framework.observability.application;

import com.aicode.core.domain.model.TokenUsage;
import com.aicode.framework.observability.domain.LlmCostSummaryView;
import com.aicode.framework.observability.domain.ModelPrice;
import com.aicode.framework.observability.domain.ModelPriceCatalog;
import com.aicode.framework.platform.domain.exception.PlatformAccessDeniedException;
import com.aicode.framework.platform.domain.model.AgentType;
import com.aicode.framework.platform.domain.model.ExecutionRecord;
import com.aicode.framework.platform.domain.model.ExecutionStatus;
import com.aicode.framework.platform.domain.port.ExecutionRecordPort;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LLM 成本汇总用例测试（Week 18）。
 *
 * <p>价值：成本分析不依赖 Langfuse 在线——直接聚合平台执行记录里的真实 usage，
 * 单价来自配置；<b>未配价的模型成本返回 null（不用 0 冒充未知）</b>。</p>
 */
class PlatformLlmCostUseCaseTest {

    private static final long ADMIN = 1L;
    private static final long USER = 2L;

    private final RecordingExecutions executions = new RecordingExecutions();
    private final PlatformLlmCostUseCase useCase = new PlatformLlmCostUseCase(
            executions,
            new ModelPriceCatalog(java.util.Map.of("deepseek-v4-pro", new ModelPrice(0.27, 1.10))),
            new AdminChecker());

    @Test
    void aggregatesTokensAndEstimatedCostPerModel() {
        executions.records.add(record("deepseek-v4-pro", new TokenUsage(1_000, 2_000, 3_000)));
        executions.records.add(record("deepseek-v4-pro", new TokenUsage(500, 1_000, 1_500)));
        executions.records.add(record("other-model", new TokenUsage(10, 20, 30)));

        LlmCostSummaryView summary = useCase.summarize(ADMIN);

        assertThat(summary.executionCount()).isEqualTo(3);
        assertThat(summary.models()).extracting(LlmCostSummaryView.ModelCost::model)
                .containsExactly("deepseek-v4-pro", "other-model");

        LlmCostSummaryView.ModelCost priced = summary.models().get(0);
        assertThat(priced.promptTokens()).isEqualTo(1_500);
        assertThat(priced.completionTokens()).isEqualTo(3_000);
        assertThat(priced.totalTokens()).isEqualTo(4_500);
        assertThat(priced.inputPricePerMillion()).isEqualTo(0.27);
        assertThat(priced.outputPricePerMillion()).isEqualTo(1.10);
        assertThat(priced.estimatedCostUsd()).isEqualTo(0.003705);

        LlmCostSummaryView.ModelCost unpriced = summary.models().get(1);
        assertThat(unpriced.inputPricePerMillion()).isNull();
        assertThat(unpriced.estimatedCostUsd()).isNull();

        assertThat(summary.totalEstimatedCostUsd()).isEqualTo(0.003705);
        assertThat(summary.generatedAt()).isNotNull();
    }

    @Test
    void reportsUnknownModelBucketAndNullTotalWhenNothingPriced() {
        executions.records.add(record(null, TokenUsage.unknown()));

        LlmCostSummaryView summary = useCase.summarize(ADMIN);

        assertThat(summary.models()).extracting(LlmCostSummaryView.ModelCost::model).containsExactly("unknown");
        assertThat(summary.models().get(0).estimatedCostUsd()).isNull();
        assertThat(summary.totalEstimatedCostUsd()).isNull();
    }

    @Test
    void rejectsNonAdminCaller() {
        assertThatThrownBy(() -> useCase.summarize(USER))
                .isInstanceOf(PlatformAccessDeniedException.class)
                .hasMessageContaining("admin");
    }

    private ExecutionRecord record(String model, TokenUsage usage) {
        Instant now = Instant.now();
        return new ExecutionRecord("exec-" + model + "-" + usage.totalTokens(), ADMIN, "medical-assistant",
                AgentType.MEDICAL_ASSISTANT, ExecutionStatus.COMPLETED, "{}", "{}", model, usage, "", now, now);
    }

    /** 内存执行记录端口。 */
    private static final class RecordingExecutions implements ExecutionRecordPort {

        private final List<ExecutionRecord> records = new ArrayList<>();

        @Override
        public void save(ExecutionRecord record) {
            records.add(record);
        }

        @Override
        public Optional<ExecutionRecord> findById(String executionId) {
            return records.stream().filter(r -> r.executionId().equals(executionId)).findFirst();
        }

        @Override
        public List<ExecutionRecord> listAll() {
            return List.copyOf(records);
        }

        @Override
        public List<ExecutionRecord> listByUserId(long userId) {
            return records.stream().filter(r -> r.userId() == userId).toList();
        }
    }

    /** 只有 ADMIN 是管理员的权限校验器（不触碰仓储）。 */
    private static final class AdminChecker extends PlatformPermissionChecker {

        private AdminChecker() {
            super(null, null);
        }

        @Override
        public boolean isAdmin(long userId) {
            return userId == ADMIN;
        }
    }
}
