package com.aicode.framework.workflow.domain.model;

/**
 * 风险评估结果。
 *
 * @param riskLevel     风险等级
 * @param justification 判断依据（命中项说明，非空）
 */
public record RiskAssessment(RiskLevel riskLevel, String justification) {
}
