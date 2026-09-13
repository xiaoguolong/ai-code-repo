package com.aicode.patient.domain.model;

/**
 * 一次调用的采样参数。temperature / maxTokens 由配置注入，禁止散落魔法数。
 */
public record ChatOptions(String model, double temperature, int maxTokens) {
}
