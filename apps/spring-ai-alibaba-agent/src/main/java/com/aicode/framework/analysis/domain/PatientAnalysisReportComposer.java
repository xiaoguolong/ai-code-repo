package com.aicode.framework.analysis.domain;

import com.aicode.framework.analysis.domain.model.ReportSummary;
import com.aicode.framework.workflow.domain.model.HealthMetrics;
import com.aicode.framework.workflow.domain.model.PatientProfile;
import com.aicode.framework.workflow.domain.model.RiskAssessment;

import java.util.List;

/**
 * 患者分析降级报告模板（Week 21，纯函数）。
 *
 * <p>仅在模型不可用（超时 / 密钥无效 / 上游 5xx）时使用。**必须显式标注「模型不可用」**，
 * 避免把规则拼装文本冒充成模型输出——报告会进执行记录、审计与长期记忆，
 * 一旦来源被混淆，后续复盘无法判断结论是谁产生的。</p>
 */
public class PatientAnalysisReportComposer {

    private static final String DEGRADED_MARKER = "提示：模型不可用，以上为规则模板生成，仅含已知数据，不构成诊疗建议。";

    /**
     * 组装规则报告。
     *
     * @param patient 患者信息，可为 null（工具未返回时）
     * @param metrics 健康指标，可为 null
     * @param reports 检查报告列表，可为 null
     * @param risk    风险评估结果，可为 null
     * @return 非空报告文本
     */
    public String compose(
            PatientProfile patient,
            HealthMetrics metrics,
            List<ReportSummary> reports,
            RiskAssessment risk
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("【患者分析报告（规则模板）】\n");
        builder.append("患者：").append(describePatient(patient)).append('\n');
        builder.append("风险：").append(risk == null ? "未知" : risk.riskLevel().label()).append('\n');
        builder.append("依据：").append(risk == null ? "无" : risk.justification()).append('\n');
        builder.append("健康指标：").append(describeMetrics(metrics)).append('\n');
        appendReports(builder, reports);
        builder.append(DEGRADED_MARKER);
        return builder.toString();
    }

    private String describePatient(PatientProfile patient) {
        if (patient == null) {
            return "未知患者";
        }
        return "%s（%s，%d 岁，%s）".formatted(
                patient.name(), patient.patientId(), patient.age(), patient.diagnosis());
    }

    private String describeMetrics(HealthMetrics metrics) {
        if (metrics == null) {
            return "未知";
        }
        return "收缩压 %s mmHg，舒张压 %s mmHg，空腹血糖 %s mmol/L，HbA1c %s%%".formatted(
                format(metrics.systolic()), format(metrics.diastolic()),
                format(metrics.fastingGlucose()), format(metrics.hba1c()));
    }

    private void appendReports(StringBuilder builder, List<ReportSummary> reports) {
        List<ReportSummary> safeReports = reports == null ? List.of() : reports;
        builder.append("检查报告（").append(safeReports.size()).append(" 条）：\n");
        if (safeReports.isEmpty()) {
            builder.append("- 无\n");
            return;
        }
        for (ReportSummary report : safeReports) {
            builder.append("- ").append(report.date()).append(' ').append(report.type())
                    .append("：").append(report.conclusion())
                    .append(report.abnormal() ? "（异常）" : "").append('\n');
        }
    }

    private String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
