package com.aicode.framework.followup.infrastructure;

/**
 * 随访计划的运行时参数（Week 24）。
 *
 * @param model       模型名
 * @param temperature 采样温度
 * @param maxTokens   补全上限
 */
public record FollowUpRuntimeConfig(String model, double temperature, int maxTokens) {
}
