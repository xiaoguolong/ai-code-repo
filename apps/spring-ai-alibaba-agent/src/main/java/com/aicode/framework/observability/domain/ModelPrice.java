package com.aicode.framework.observability.domain;

/**
 * 模型单价（Week 18，单位：USD / 百万 token）。
 *
 * @param inputPerMillion  提示 token 单价
 * @param outputPerMillion 补全 token 单价
 */
public record ModelPrice(double inputPerMillion, double outputPerMillion) {

    /**
     * 是否可用：两边都为 0（或负）视为「未配置」，不参与成本计算——
     * 与其上报 0 成本误导成本分析，不如让 Langfuse 侧按模型定义推断或干脆不显示。
     *
     * @return 至少一侧单价为正时返回 true
     */
    public boolean usable() {
        return inputPerMillion > 0 || outputPerMillion > 0;
    }
}
