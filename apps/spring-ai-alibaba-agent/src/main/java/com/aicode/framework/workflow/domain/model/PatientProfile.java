package com.aicode.framework.workflow.domain.model;

/**
 * 患者基础信息值对象，来自 {@code PatientLookupTool} 的查询结果。
 *
 * @param patientId 患者编号
 * @param name      姓名
 * @param age       年龄
 * @param gender    性别
 * @param diagnosis 诊断
 */
public record PatientProfile(String patientId, String name, int age, String gender, String diagnosis) {
}
