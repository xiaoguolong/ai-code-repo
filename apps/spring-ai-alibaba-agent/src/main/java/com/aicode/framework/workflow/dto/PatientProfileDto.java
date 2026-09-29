package com.aicode.framework.workflow.dto;

import com.aicode.framework.workflow.domain.model.PatientProfile;

/**
 * 患者基础信息响应体。
 */
public record PatientProfileDto(String patientId, String name, int age, String gender, String diagnosis) {

    /**
     * 从领域模型转换。
     */
    public static PatientProfileDto from(PatientProfile patient) {
        return new PatientProfileDto(
                patient.patientId(), patient.name(), patient.age(), patient.gender(), patient.diagnosis());
    }
}
