package com.aicode.patient.domain.port;

import com.aicode.patient.domain.model.HealthMetric;
import com.aicode.patient.domain.model.Order;
import com.aicode.patient.domain.model.Patient;
import com.aicode.patient.domain.model.Report;

import java.util.List;
import java.util.Optional;

/**
 * 出站端口：患者业务数据。第 6 周用内存种子实现跑通链路，后续可替换为数据库/业务服务。
 */
public interface PatientDataPort {

    /**
     * 按患者编号查询患者基础信息。
     */
    Optional<Patient> findPatient(String patientId);

    /**
     * 查询患者的检查报告列表。
     */
    List<Report> listReports(String patientId);

    /**
     * 查询患者的健康指标列表。
     */
    List<HealthMetric> listHealthMetrics(String patientId);

    /**
     * 查询患者的医嘱列表。
     */
    List<Order> listOrders(String patientId);
}
