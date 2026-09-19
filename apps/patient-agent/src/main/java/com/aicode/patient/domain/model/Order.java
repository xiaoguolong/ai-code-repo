package com.aicode.patient.domain.model;

import java.util.Objects;

/**
 * 医嘱记录。
 */
public record Order(String id, String patientId, String item, String status, String createdAt) {

    public Order {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(patientId, "patientId");
        Objects.requireNonNull(item, "item");
    }
}
