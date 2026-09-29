package com.aicode.framework.multiagent.application;

/**
 * 医疗助手多 Agent 运行时参数。
 */
public record MedicalAssistantRuntimeConfig(String model, double temperature, int maxTokens, int maxSupervisorLoops) {
}
