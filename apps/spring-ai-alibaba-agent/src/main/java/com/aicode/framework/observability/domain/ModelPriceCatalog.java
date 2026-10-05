package com.aicode.framework.observability.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * 模型价目表（Week 18）：模型名 → 单价，来自配置（{@code langfuse.model-prices}）。
 *
 * <p>为什么客户端要自备价目表：Langfuse 只在「模型定义匹配得上」时才计算成本，
 * 自部署环境未必配了国产模型的价格；客户端上报的 {@code cost_details} 优先级高于服务端推断，
 * 所以带上它能让成本分析在离线环境也成立。</p>
 *
 * <p>不可用的价格行（两边都 ≤ 0）在构造时即被剔除：既不出现在 {@link #models()}，
 * 也不会产生「0 成本」这种误导性数据。</p>
 */
public final class ModelPriceCatalog {

    private final Map<String, ModelPrice> prices;

    /**
     * @param prices 模型名 → 单价；null 视为空表
     */
    public ModelPriceCatalog(Map<String, ModelPrice> prices) {
        Map<String, ModelPrice> usable = new TreeMap<>();
        if (prices != null) {
            prices.forEach((model, price) -> {
                if (model != null && !model.isBlank() && price != null && price.usable()) {
                    usable.put(model.trim(), price);
                }
            });
        }
        this.prices = Map.copyOf(new LinkedHashMap<>(usable));
    }

    /** 空价目表：任何模型都算不出成本。 */
    public static ModelPriceCatalog empty() {
        return new ModelPriceCatalog(Map.of());
    }

    /**
     * 查单价。
     *
     * @param model 模型名，可为 null/空
     * @return 已配置且可用时返回值；否则空
     */
    public Optional<ModelPrice> find(String model) {
        if (model == null || model.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(prices.get(model.trim()));
    }

    /**
     * 已配置单价的模型名（按字典序，便于稳定展示与断言）。
     *
     * @return 模型名列表，无配置时为空列表
     */
    public List<String> models() {
        return List.copyOf(prices.keySet());
    }

    /** 是否没有任何可用单价。 */
    public boolean isEmpty() {
        return prices.isEmpty();
    }
}
