package com.aicode.framework.workflow.domain;

import com.aicode.framework.workflow.domain.model.HealthMetrics;
import com.aicode.framework.workflow.domain.model.RiskAssessment;
import com.aicode.framework.workflow.domain.model.RiskLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 风险判断纯规则测试：覆盖低/中/高三档分界（含边界值），并确保 justification 永不空白。
 */
class PatientRiskAssessorTest {

    private final PatientRiskAssessor assessor = new PatientRiskAssessor();

    @Test
    void classifiesHighRiskWhenSystolicExceedsHighThreshold() {
        RiskAssessment result = assessor.assess(new HealthMetrics(160, 80, 5.0, 5.0));

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(result.justification()).contains("收缩压");
    }

    @Test
    void classifiesHighRiskWhenHba1cExceedsHighThreshold() {
        RiskAssessment result = assessor.assess(new HealthMetrics(120, 80, 6.0, 9.0));

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void classifiesMediumRiskForDemoPatient() {
        RiskAssessment result = assessor.assess(new HealthMetrics(148, 92, 8.6, 7.9));

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(result.justification()).isNotBlank();
    }

    @Test
    void classifiesLowRiskWhenAllMetricsNormal() {
        RiskAssessment result = assessor.assess(new HealthMetrics(118, 78, 5.2, 5.4));

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    void mediumThresholdIsInclusive() {
        RiskAssessment result = assessor.assess(new HealthMetrics(140, 90, 7.0, 7.0));

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    void justificationIsNeverBlankForAllLevels() {
        HealthMetrics[] samples = {
                new HealthMetrics(160, 80, 5.0, 5.0),
                new HealthMetrics(148, 92, 8.6, 7.9),
                new HealthMetrics(118, 78, 5.2, 5.4)
        };

        for (HealthMetrics metrics : samples) {
            assertThat(assessor.assess(metrics).justification()).isNotBlank();
        }
    }
}
