package com.aicode.patient.domain.model;

import java.util.Objects;

/**
 * 检查报告。
 */
public record Report(String id, String patientId, String type, String summary, String issuedAt) {

    public Report {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(patientId, "patientId");
        Objects.requireNonNull(type, "type");
    }
}
