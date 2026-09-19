package com.aicode.patient.domain.model;

import java.util.Objects;

/**
 * 健康指标单条记录。
 */
public record HealthMetric(String patientId, String metric, double value, String unit, String measuredAt) {

    public HealthMetric {
        Objects.requireNonNull(patientId, "patientId");
        Objects.requireNonNull(metric, "metric");
        Objects.requireNonNull(unit, "unit");
    }
}
