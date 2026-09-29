package com.aicode.framework.workflow.application;

/**
 * 患者风险分析 Workflow 运行时参数（模型名、采样参数），由配置映射，避免魔法数字散落。
 */
public record PatientRiskRuntimeConfig(String model, double temperature, int maxTokens) {
}
