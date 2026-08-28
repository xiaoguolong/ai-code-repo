package com.aicode.demo.application;

/**
 * 用例运行时配置。由基础设施把配置文件映射进来，领域不读 Environment。
 */
public record ChatRuntimeConfig(String model, double temperature, int maxTokens) {
}
