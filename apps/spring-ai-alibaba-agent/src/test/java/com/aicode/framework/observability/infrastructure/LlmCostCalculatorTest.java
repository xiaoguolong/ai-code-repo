package com.aicode.framework.observability.infrastructure;

import com.aicode.core.domain.model.TokenUsage;
import com.aicode.framework.observability.domain.LlmCost;
import com.aicode.framework.observability.domain.ModelPrice;
import com.aicode.framework.observability.domain.ModelPriceCatalog;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LLM 成本计算测试（Week 18）。
 *
 * <p>契约：成本只用<b>真实 usage</b> × 配置单价计算，精度 6 位小数；
 * 模型未配置单价或 usage 未知时返回空（<b>不下发 cost_details</b>，绝不用 0 冒充未知）。</p>
 */
class LlmCostCalculatorTest {

    private final ModelPriceCatalog catalog = new ModelPriceCatalog(Map.of(
            "deepseek-v4-pro", new ModelPrice(0.27, 1.10)));

    private final LlmCostCalculator calculator = new LlmCostCalculator(catalog);

    @Test
    void computesCostFromRealUsageAndConfiguredPrice() {
        Optional<LlmCost> cost = calculator.estimate("deepseek-v4-pro", new TokenUsage(1_000, 2_000, 3_000));

        assertThat(cost).isPresent();
        assertThat(cost.get().input()).isEqualTo(0.00027);
        assertThat(cost.get().output()).isEqualTo(0.0022);
        assertThat(cost.get().total()).isEqualTo(0.00247);
    }

    @Test
    void roundsToSixDecimalsHalfUp() {
        ModelPriceCatalog tiny = new ModelPriceCatalog(Map.of("m", new ModelPrice(0.000001, 0.000001)));

        Optional<LlmCost> cost = new LlmCostCalculator(tiny).estimate("m", new TokenUsage(1, 1, 2));

        assertThat(cost).isPresent();
        assertThat(cost.get().total()).isEqualTo(0.000000);
    }

    @Test
    void returnsEmptyWhenModelHasNoConfiguredPrice() {
        assertThat(calculator.estimate("unknown-model", new TokenUsage(10, 20, 30))).isEmpty();
    }

    @Test
    void returnsEmptyWhenUsageUnknownOrModelBlank() {
        assertThat(calculator.estimate("deepseek-v4-pro", TokenUsage.unknown())).isEmpty();
        assertThat(calculator.estimate(null, new TokenUsage(10, 20, 30))).isEmpty();
        assertThat(calculator.estimate("  ", new TokenUsage(10, 20, 30))).isEmpty();
        assertThat(calculator.estimate("deepseek-v4-pro", null)).isEmpty();
    }

    @Test
    void catalogIgnoresRowsWithoutUsablePrice() {
        ModelPriceCatalog partial = new ModelPriceCatalog(Map.of(
                "configured", new ModelPrice(1.0, 2.0),
                "incomplete", new ModelPrice(0.0, 0.0)));

        // 单价全为 0 的行视为「未配置」：既不出现在 configuredModels，也不参与成本计算
        assertThat(partial.models()).containsExactly("configured");
        assertThat(partial.find("incomplete")).isEmpty();
        assertThat(partial.find("configured")).isPresent();
        assertThat(ModelPriceCatalog.empty().models()).isEmpty();
    }
}
