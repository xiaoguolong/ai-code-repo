package com.aicode.patient.infrastructure.data;

import com.aicode.patient.domain.model.HealthMetric;
import com.aicode.patient.domain.model.Order;
import com.aicode.patient.domain.model.Patient;
import com.aicode.patient.domain.model.Report;
import com.aicode.patient.domain.port.PatientDataPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 内存种子患者数据。第 6 周用于演示 Tool Calling 链路，数据硬编码在内存；
 * 后续可替换为数据库实现（第 9/12 周）。两个演示患者：P001 张三、P002 李四。
 */
@Component
public class InMemoryPatientDataAdapter implements PatientDataPort {

    private static final Map<String, Patient> PATIENTS = Map.of(
            "P001", new Patient("P001", "张三", 68, "男", "高血压、2 型糖尿病"),
            "P002", new Patient("P002", "李四", 55, "女", "冠心病")
    );

    private static final Map<String, List<Report>> REPORTS = Map.of(
            "P001", List.of(
                    new Report("R001", "P001", "血常规", "白细胞偏高，提示感染", "2026-09-01"),
                    new Report("R002", "P001", "心电图", "窦性心律，偶发室性早搏", "2026-09-02")
            ),
            "P002", List.of(
                    new Report("R003", "P002", "冠脉造影", "左前降支 70% 狭窄", "2026-08-28")
            )
    );

    private static final Map<String, List<HealthMetric>> METRICS = Map.of(
            "P001", List.of(
                    new HealthMetric("P001", "收缩压", 158, "mmHg", "2026-09-15"),
                    new HealthMetric("P001", "舒张压", 95, "mmHg", "2026-09-15"),
                    new HealthMetric("P001", "空腹血糖", 8.6, "mmol/L", "2026-09-15")
            ),
            "P002", List.of(
                    new HealthMetric("P002", "低密度脂蛋白", 3.8, "mmol/L", "2026-09-14")
            )
    );

    private static final Map<String, List<Order>> ORDERS = Map.of(
            "P001", List.of(
                    new Order("O001", "P001", "硝苯地平缓释片", "执行中", "2026-09-10"),
                    new Order("O002", "P001", "二甲双胍", "执行中", "2026-09-10")
            ),
            "P002", List.of(
                    new Order("O003", "P002", "阿司匹林", "已停用", "2026-09-01")
            )
    );

    @Override
    public Optional<Patient> findPatient(String patientId) {
        return Optional.ofNullable(PATIENTS.get(patientId));
    }

    @Override
    public List<Report> listReports(String patientId) {
        return REPORTS.getOrDefault(patientId, List.of());
    }

    @Override
    public List<HealthMetric> listHealthMetrics(String patientId) {
        return METRICS.getOrDefault(patientId, List.of());
    }

    @Override
    public List<Order> listOrders(String patientId) {
        return ORDERS.getOrDefault(patientId, List.of());
    }
}
