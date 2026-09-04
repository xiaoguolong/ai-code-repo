package com.aicode.enterprise.application;

/**
 * 聊天模型运行时配置。由基础设施映射配置文件，用例不读 Environment。
 *
 * @param model       模型名
 * @param temperature 采样温度
 * @param maxTokens   生成 Token 上限
 */
public record ChatRuntimeConfig(String model, double temperature, int maxTokens) {
}
