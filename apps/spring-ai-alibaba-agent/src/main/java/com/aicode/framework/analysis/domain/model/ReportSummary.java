package com.aicode.framework.analysis.domain.model;

/**
 * 检查报告摘要（来自 {@code ReportLookupTool}）。
 *
 * @param reportId   报告编号
 * @param type       报告类型（如「糖化血红蛋白」）
 * @param date       报告日期（ISO 字符串，演示数据）
 * @param conclusion 结论
 * @param abnormal   是否有异常项
 */
public record ReportSummary(String reportId, String type, String date, String conclusion, boolean abnormal) {
}
