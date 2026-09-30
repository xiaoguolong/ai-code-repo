package com.aicode.core.infrastructure.eval;

import com.aicode.core.domain.model.EvalCategory;
import com.aicode.core.domain.model.EvalCaseResult;
import com.aicode.core.domain.model.EvalExpectation;
import com.aicode.core.domain.model.EvalReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** DefaultEvalAdapter 单元测试。 */
class DefaultEvalAdapterTest {

    private DefaultEvalAdapter evalAdapter;

    @BeforeEach
    void setUp() {
        evalAdapter = new DefaultEvalAdapter();
    }

    @Test
    void exactMatchPassesWhenEqualIgnoringWhitespace() {
        EvalCaseResult result = evalAdapter.scoreOutput(
                "c1", EvalExpectation.exact("LOW"), "  LOW  ");

        assertThat(result.passed()).isTrue();
        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void exactMatchFailsWhenDifferent() {
        EvalCaseResult result = evalAdapter.scoreOutput(
                "c1", EvalExpectation.exact("LOW"), "HIGH");

        assertThat(result.passed()).isFalse();
        assertThat(result.score()).isZero();
    }

    @Test
    void containsAllPassesWhenAllKeywordsPresent() {
        EvalCaseResult result = evalAdapter.scoreOutput(
                "c1",
                EvalExpectation.containsAll("糖尿病", "胰岛素"),
                "2型糖尿病需定期注射胰岛素");

        assertThat(result.passed()).isTrue();
    }

    @Test
    void containsAllFailsWhenKeywordMissing() {
        EvalCaseResult result = evalAdapter.scoreOutput(
                "c1",
                EvalExpectation.containsAll("糖尿病", "胰岛素"),
                "仅提及糖尿病");

        assertThat(result.passed()).isFalse();
    }

    @Test
    void regexPassesWhenPatternMatches() {
        EvalCaseResult result = evalAdapter.scoreOutput(
                "c1", EvalExpectation.regex("风险等级[:：]\\s*(高|中|低)"), "风险等级：高");

        assertThat(result.passed()).isTrue();
    }

    @Test
    void notContainsPassesWhenForbiddenAbsent() {
        EvalCaseResult result = evalAdapter.scoreOutput(
                "c1", EvalExpectation.notContains("密码", "密钥"), "正常医疗建议");

        assertThat(result.passed()).isTrue();
    }

    @Test
    void notContainsFailsWhenForbiddenPresent() {
        EvalCaseResult result = evalAdapter.scoreOutput(
                "c1", EvalExpectation.notContains("密码"), "请勿泄露密码");

        assertThat(result.passed()).isFalse();
    }

    @Test
    void guardrailCasePassesWhenViolationThrown() {
        EvalCaseResult result = evalAdapter.scoreGuardrailCase(
                "g1", true, "prompt injection detected");

        assertThat(result.passed()).isTrue();
        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void guardrailCaseFailsWhenNoViolation() {
        EvalCaseResult result = evalAdapter.scoreGuardrailCase("g1", false, null);

        assertThat(result.passed()).isFalse();
    }

    @Test
    void aggregateComputesPassRate() {
        List<EvalCaseResult> results = List.of(
                new EvalCaseResult("a", true, 1.0, "ok", "x"),
                new EvalCaseResult("b", false, 0.0, "fail", "y")
        );
        Instant started = Instant.parse("2026-09-30T06:00:00Z");
        Instant finished = Instant.parse("2026-09-30T06:01:00Z");

        EvalReport report = evalAdapter.aggregate(
                "run-1", 1L, "guardrail-regression", EvalCategory.GUARDRAIL,
                results, started, finished);

        assertThat(report.passedCount()).isEqualTo(1);
        assertThat(report.totalCount()).isEqualTo(2);
        assertThat(report.passRate()).isEqualTo(0.5);
    }
}
