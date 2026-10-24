package com.aicode.framework.analysis.domain.model;

/**
 * 患者分析步骤（与 Week 11 多 Agent 的步骤结构同构，前端可复用）。
 *
 * @param stepNo  序号，从 1 开始
 * @param agent   步骤执行者标识（如 {@code patient_lookup}）
 * @param summary 步骤摘要（不含患者隐私正文）
 */
public record PatientAnalysisStep(int stepNo, String agent, String summary) {
}
