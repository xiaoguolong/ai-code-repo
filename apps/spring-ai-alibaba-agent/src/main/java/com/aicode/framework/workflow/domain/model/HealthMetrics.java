package com.aicode.framework.workflow.domain.model;

/**
 * 健康指标值对象，来自 {@code HealthMetricTool} 的查询结果。
 *
 * @param systolic       收缩压（mmHg）
 * @param diastolic      舒张压（mmHg）
 * @param fastingGlucose 空腹血糖（mmol/L）
 * @param hba1c          糖化血红蛋白（%）
 */
public record HealthMetrics(double systolic, double diastolic, double fastingGlucose, double hba1c) {
}
