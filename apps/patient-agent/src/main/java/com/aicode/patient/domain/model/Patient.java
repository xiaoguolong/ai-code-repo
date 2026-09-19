package com.aicode.patient.domain.model;

import java.util.Objects;

/**
 * 患者基础信息。
 */
public record Patient(String id, String name, int age, String gender, String diagnosis) {

    public Patient {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
    }
}
