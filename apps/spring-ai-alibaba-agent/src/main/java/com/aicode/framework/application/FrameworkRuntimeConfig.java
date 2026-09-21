package com.aicode.framework.application;

/**
 * Graph Agent 运行时参数（模型名、采样参数、循环上限），由配置映射，避免魔法数字散落。
 */
public record FrameworkRuntimeConfig(String model, double temperature, int maxTokens, int maxIterations) {
}
