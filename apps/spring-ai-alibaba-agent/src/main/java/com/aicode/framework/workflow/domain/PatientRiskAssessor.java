package com.aicode.framework.workflow.domain;

import com.aicode.framework.workflow.domain.model.HealthMetrics;
import com.aicode.framework.workflow.domain.model.RiskAssessment;
import com.aicode.framework.workflow.domain.model.RiskLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * 患者风险判断纯领域服务：依据血压、空腹血糖、糖化血红蛋白阈值分低/中/高三档。
 * 阈值内聚为常量，便于单测与后续外部化；业务规则不得散落到基础设施或工具。
 */
public class PatientRiskAssessor {

    private static final double SYSTOLIC_HIGH = 160.0;
    private static final double DIASTOLIC_HIGH = 100.0;
    private static final double FASTING_GLUCOSE_HIGH = 11.1;
    private static final double HBA1C_HIGH = 9.0;

    private static final double SYSTOLIC_MEDIUM = 140.0;
    private static final double DIASTOLIC_MEDIUM = 90.0;
    private static final double FASTING_GLUCOSE_MEDIUM = 7.0;
    private static final double HBA1C_MEDIUM = 7.0;

    /**
     * @param metrics 健康指标
     * @return 风险等级与判断依据，永不返回 null
     */
    public RiskAssessment assess(HealthMetrics metrics) {
        List<String> highHits = new ArrayList<>();
        addIf(highHits, "收缩压", metrics.systolic(), SYSTOLIC_HIGH);
        addIf(highHits, "舒张压", metrics.diastolic(), DIASTOLIC_HIGH);
        addIf(highHits, "空腹血糖", metrics.fastingGlucose(), FASTING_GLUCOSE_HIGH);
        addIf(highHits, "糖化血红蛋白", metrics.hba1c(), HBA1C_HIGH);
        if (!highHits.isEmpty()) {
            return new RiskAssessment(RiskLevel.HIGH, String.join("；", highHits));
        }

        List<String> mediumHits = new ArrayList<>();
        addIf(mediumHits, "收缩压", metrics.systolic(), SYSTOLIC_MEDIUM);
        addIf(mediumHits, "舒张压", metrics.diastolic(), DIASTOLIC_MEDIUM);
        addIf(mediumHits, "空腹血糖", metrics.fastingGlucose(), FASTING_GLUCOSE_MEDIUM);
        addIf(mediumHits, "糖化血红蛋白", metrics.hba1c(), HBA1C_MEDIUM);
        if (!mediumHits.isEmpty()) {
            return new RiskAssessment(RiskLevel.MEDIUM, String.join("；", mediumHits));
        }

        return new RiskAssessment(RiskLevel.LOW, "血压、空腹血糖、糖化血红蛋白均在正常范围");
    }

    private static void addIf(List<String> hits, String label, double value, double threshold) {
        if (value >= threshold) {
            hits.add(label + " " + format(value) + " 达到 " + format(threshold) + " 阈值");
        }
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
