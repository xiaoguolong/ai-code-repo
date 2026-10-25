package com.aicode.framework.operations.infrastructure;

/**
 * 运营分析的运行时参数（Week 24）。
 *
 * <p>与患者分析一样复用全局 LLM 配置，不新增模型/密钥。</p>
 *
 * @param model       模型名
 * @param temperature 采样温度
 * @param maxTokens   补全上限
 */
public record OperationsAnalysisRuntimeConfig(String model, double temperature, int maxTokens) {
}
