package com.aicode.framework.observability.domain;

/**
 * 一次模型调用的估算成本（Week 18，单位 USD）。
 *
 * <p>精度约定：三个字段都取 6 位小数（HALF_UP），与 Langfuse {@code cost_details} 的展示口径一致，
 * 避免「页面上几分钱、接口里一串浮点尾巴」。</p>
 *
 * @param input  提示侧成本
 * @param output 补全侧成本
 * @param total  合计成本
 */
public record LlmCost(double input, double output, double total) {

    /**
     * 构造并统一舍入到 6 位小数。
     *
     * @param input  提示侧成本
     * @param output 补全侧成本
     * @return 舍入后的成本
     */
    public static LlmCost of(double input, double output) {
        double roundedInput = round(input);
        double roundedOutput = round(output);
        return new LlmCost(roundedInput, roundedOutput, round(roundedInput + roundedOutput));
    }

    /**
     * 按本类统一口径舍入一个金额（供汇总视图复用，避免各处精度写法不同）。
     *
     * @param value 金额
     * @return 6 位小数（HALF_UP）后的金额
     */
    public static double roundUsd(double value) {
        return round(value);
    }

    private static double round(double value) {
        return java.math.BigDecimal.valueOf(value)
                .setScale(6, java.math.RoundingMode.HALF_UP)
                .doubleValue();
    }
}
