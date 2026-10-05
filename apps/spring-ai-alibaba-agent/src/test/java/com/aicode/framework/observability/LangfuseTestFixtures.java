package com.aicode.framework.observability;

import com.aicode.core.domain.port.GuardrailPort;
import com.aicode.framework.observability.domain.ModelPrice;
import com.aicode.framework.observability.infrastructure.LangfuseContentPolicy;
import com.aicode.framework.observability.infrastructure.LangfuseGenerationSupport;
import com.aicode.framework.observability.infrastructure.LangfuseProperties;
import com.aicode.framework.observability.infrastructure.LangfusePromptTracker;
import com.aicode.framework.observability.infrastructure.LlmCostCalculator;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Langfuse 测试夹具（Week 18）：集中构造 {@link LangfuseProperties} 与相关协作者，
 * 避免每个测试重复写 12 个位置参数。
 */
public final class LangfuseTestFixtures {

    private LangfuseTestFixtures() {
    }

    /** 默认关闭（本地 / 单测口径）：零导出、零富化、正文不采集、Prompt 走本地。 */
    public static LangfuseProperties disabled() {
        return build(false, false, false, Map.of());
    }

    /** 开启追踪（含 trace 维度富化），正文采集关闭。 */
    public static LangfuseProperties enabled() {
        return build(true, false, false, Map.of());
    }

    /** 开启追踪 + 正文采集 + 价目表。 */
    public static LangfuseProperties enabledWithContent(Map<String, ModelPrice> prices) {
        return build(true, true, false, prices);
    }

    /** 开启 Prompt 管理（追踪关闭，验证两个能力相互独立）。 */
    public static LangfuseProperties promptEnabled() {
        return build(false, false, true, Map.of());
    }

    /** 指定 host 的开启态（用于指向本地 HttpServer 夹具）。 */
    public static LangfuseProperties enabledAt(String host) {
        return new LangfuseProperties(
                true, host, "pk-lf-test", "sk-lf-test", "ai-code-repo",
                "test", "week18", false, 2000, 5000,
                new LangfuseProperties.Prompt(false, "production", 60), Map.of());
    }

    /** 指定 host 且开启 Prompt 管理（可指定缓存 TTL）。 */
    public static LangfuseProperties promptEnabledAt(String host, int cacheTtlSeconds) {
        return new LangfuseProperties(
                false, host, "pk-lf-test", "sk-lf-test", "ai-code-repo",
                "test", "week18", false, 2000, 5000,
                new LangfuseProperties.Prompt(true, "production", cacheTtlSeconds), Map.of());
    }

    /** 开启追踪，正文采集按参数，单价按参数（正文上限 2000 字符，便于断言）。 */
    public static LangfuseProperties tracing(boolean captureContent, Map<String, ModelPrice> prices) {
        Map<String, LangfuseProperties.ModelPriceProperties> rows = priceRows(prices);
        return new LangfuseProperties(
                true, "http://langfuse.local:3000", "pk-lf-test", "sk-lf-test", "ai-code-repo",
                "test", "week18", captureContent, 2000, 5000,
                new LangfuseProperties.Prompt(false, "production", 60), rows);
    }

    /** 构造被测的生成观测支持对象（与 Spring 装配同构，只是手工 new）。 */
    public static LangfuseGenerationSupport generationSupport(
            LangfuseProperties properties, LangfusePromptTracker tracker, GuardrailPort guardrail) {
        ObjectMapper objectMapper = new ObjectMapper();
        return new LangfuseGenerationSupport(
                properties,
                new LlmCostCalculator(properties.modelPriceCatalog()),
                new LangfuseContentPolicy(properties, guardrail, objectMapper),
                tracker,
                objectMapper);
    }

    private static Map<String, LangfuseProperties.ModelPriceProperties> priceRows(Map<String, ModelPrice> prices) {
        Map<String, LangfuseProperties.ModelPriceProperties> rows = new LinkedHashMap<>();
        prices.forEach((model, price) -> rows.put(model,
                new LangfuseProperties.ModelPriceProperties(price.inputPerMillion(), price.outputPerMillion())));
        return rows;
    }

    private static LangfuseProperties build(
            boolean enabled, boolean captureContent, boolean promptEnabled, Map<String, ModelPrice> prices) {
        Map<String, LangfuseProperties.ModelPriceProperties> modelPrices = new LinkedHashMap<>();
        prices.forEach((model, price) -> modelPrices.put(model,
                new LangfuseProperties.ModelPriceProperties(price.inputPerMillion(), price.outputPerMillion())));
        return new LangfuseProperties(
                enabled,
                "http://langfuse.local:3000",
                "pk-lf-test",
                "sk-lf-test",
                "ai-code-repo",
                "test",
                "week18",
                captureContent,
                40,
                5000,
                new LangfuseProperties.Prompt(promptEnabled, "production", 60),
                modelPrices);
    }
}
