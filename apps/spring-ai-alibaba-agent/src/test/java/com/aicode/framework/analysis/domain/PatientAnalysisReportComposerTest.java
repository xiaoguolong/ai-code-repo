package com.aicode.framework.analysis.domain;

import com.aicode.framework.analysis.domain.model.ReportSummary;
import com.aicode.framework.workflow.domain.model.HealthMetrics;
import com.aicode.framework.workflow.domain.model.PatientProfile;
import com.aicode.framework.workflow.domain.model.RiskAssessment;
import com.aicode.framework.workflow.domain.model.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 降级报告模板（纯函数）：必须自证是规则生成，不能伪装成模型输出。 */
class PatientAnalysisReportComposerTest {

    private final PatientAnalysisReportComposer composer = new PatientAnalysisReportComposer();

    @Test
    void composesReportWithPatientMetricsReportsAndRisk() {
        String report = composer.compose(patient(), metrics(), reports(),
                new RiskAssessment(RiskLevel.MEDIUM, "收缩压 148 达到 140 阈值"));

        assertThat(report)
                .contains("张三")
                .contains("P001")
                .contains("中风险")
                .contains("收缩压 148 达到 140 阈值")
                .contains("HbA1c 7.9")
                .contains("糖化血红蛋白")
                .contains("HbA1c 8.1%")
                .contains("模型不可用");
    }

    @Test
    void toleratesMissingPatientAndEmptyReports() {
        String report = composer.compose(null, metrics(), List.of(),
                new RiskAssessment(RiskLevel.LOW, "均正常"));

        assertThat(report).contains("未知患者").contains("低风险").contains("检查报告（0 条）");
    }

    private PatientProfile patient() {
        return new PatientProfile("P001", "张三", 62, "male", "2 型糖尿病");
    }

    private HealthMetrics metrics() {
        return new HealthMetrics(148, 92, 8.6, 7.9);
    }

    private List<ReportSummary> reports() {
        return List.of(
                new ReportSummary("R001", "糖化血红蛋白", "2026-09-12", "HbA1c 8.1%，控制不佳", true),
                new ReportSummary("R002", "肾功能", "2026-09-20", "eGFR 78", false));
    }
}
