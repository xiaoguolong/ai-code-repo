package com.aicode.framework.workflow.dto;

import com.aicode.framework.workflow.domain.model.HealthMetrics;

/**
 * 健康指标响应体。
 */
public record HealthMetricsDto(double systolic, double diastolic, double fastingGlucose, double hba1c) {

    /**
     * 从领域模型转换。
     */
    public static HealthMetricsDto from(HealthMetrics metrics) {
        return new HealthMetricsDto(
                metrics.systolic(), metrics.diastolic(), metrics.fastingGlucose(), metrics.hba1c());
    }
}
