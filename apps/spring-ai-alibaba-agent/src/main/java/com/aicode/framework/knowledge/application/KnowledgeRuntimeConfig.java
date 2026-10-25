package com.aicode.framework.knowledge.application;

/**
 * 知识库问答运行时参数（Week 25）。
 *
 * @param model       模型名
 * @param temperature 采样温度
 * @param maxTokens   补全上限
 */
public record KnowledgeRuntimeConfig(String model, double temperature, int maxTokens) {
}
