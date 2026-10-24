package com.aicode.framework.analysis.application;

/**
 * 患者分析 Agent 运行时参数（模型、采样参数）。
 *
 * @param model       模型名
 * @param temperature 采样温度
 * @param maxTokens   单次最大输出 token
 */
public record PatientAnalysisRuntimeConfig(String model, double temperature, int maxTokens) {
}
