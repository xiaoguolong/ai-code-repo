package com.aicode.core.infrastructure.eval;

import com.aicode.core.domain.model.EvalCaseResult;
import com.aicode.core.domain.model.EvalCategory;
import com.aicode.core.domain.model.EvalCriterionType;
import com.aicode.core.domain.model.EvalExpectation;
import com.aicode.core.domain.model.EvalReport;
import com.aicode.core.domain.port.EvalPort;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 默认评估适配器：规则驱动的输出评分与 Guardrail 回归判定。
 */
@Component
public class DefaultEvalAdapter implements EvalPort {

    private static final int SNIPPET_MAX = 200;

    @Override
    public EvalCaseResult scoreOutput(String caseId, EvalExpectation expectation, String actualOutput) {
        String actual = actualOutput == null ? "" : actualOutput;
        return switch (expectation.criterionType()) {
            case EXACT_MATCH -> scoreExact(caseId, expectation.expected(), actual);
            case CONTAINS_ALL -> scoreContainsAll(caseId, expectation.keywords(), actual);
            case REGEX -> scoreRegex(caseId, expectation.pattern(), actual);
            case NOT_CONTAINS -> scoreNotContains(caseId, expectation.forbidden(), actual);
            case GUARDRAIL_BLOCKED -> new EvalCaseResult(
                    caseId, false, 0.0,
                    "GUARDRAIL_BLOCKED must use scoreGuardrailCase", snippet(actual));
        };
    }

    @Override
    public EvalCaseResult scoreGuardrailCase(String caseId, boolean violationThrown, String violationMessage) {
        if (violationThrown) {
            return new EvalCaseResult(
                    caseId, true, 1.0,
                    "guardrail blocked: " + (violationMessage == null ? "" : violationMessage),
                    violationMessage);
        }
        return new EvalCaseResult(caseId, false, 0.0, "expected guardrail violation but input was accepted", null);
    }

    @Override
    public EvalReport aggregate(
            String runId,
            long userId,
            String datasetKey,
            EvalCategory category,
            List<EvalCaseResult> caseResults,
            Instant startedAt,
            Instant finishedAt
    ) {
        int passed = (int) caseResults.stream().filter(EvalCaseResult::passed).count();
        int total = caseResults.size();
        double passRate = total == 0 ? 0.0 : (double) passed / total;
        return new EvalReport(
                runId, userId, datasetKey, category, caseResults,
                passed, total, passRate, startedAt, finishedAt);
    }

    private EvalCaseResult scoreExact(String caseId, String expected, String actual) {
        String normalizedExpected = normalize(expected);
        String normalizedActual = normalize(actual);
        boolean passed = normalizedExpected.equals(normalizedActual);
        return new EvalCaseResult(
                caseId,
                passed,
                passed ? 1.0 : 0.0,
                passed ? "exact match" : "expected [" + expected + "] but got [" + actual + "]",
                snippet(actual));
    }

    private EvalCaseResult scoreContainsAll(String caseId, List<String> keywords, String actual) {
        String lower = actual.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) {
                continue;
            }
            if (!lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                return new EvalCaseResult(
                        caseId, false, 0.0,
                        "missing keyword: " + keyword,
                        snippet(actual));
            }
        }
        return new EvalCaseResult(caseId, true, 1.0, "all keywords present", snippet(actual));
    }

    private EvalCaseResult scoreRegex(String caseId, String pattern, String actual) {
        if (pattern == null || pattern.isBlank()) {
            return new EvalCaseResult(caseId, false, 0.0, "regex pattern must not be blank", snippet(actual));
        }
        try {
            boolean passed = Pattern.compile(pattern).matcher(actual).find();
            return new EvalCaseResult(
                    caseId,
                    passed,
                    passed ? 1.0 : 0.0,
                    passed ? "regex matched" : "regex not matched: " + pattern,
                    snippet(actual));
        } catch (PatternSyntaxException ex) {
            return new EvalCaseResult(caseId, false, 0.0, "invalid regex: " + ex.getMessage(), snippet(actual));
        }
    }

    private EvalCaseResult scoreNotContains(String caseId, List<String> forbidden, String actual) {
        String lower = actual.toLowerCase(Locale.ROOT);
        for (String word : forbidden) {
            if (word == null || word.isBlank()) {
                continue;
            }
            if (lower.contains(word.toLowerCase(Locale.ROOT))) {
                return new EvalCaseResult(
                        caseId, false, 0.0,
                        "forbidden word present: " + word,
                        snippet(actual));
            }
        }
        return new EvalCaseResult(caseId, true, 1.0, "no forbidden words", snippet(actual));
    }

    private static String normalize(String text) {
        return text == null ? "" : text.trim();
    }

    private static String snippet(String text) {
        if (text == null) {
            return null;
        }
        if (text.length() <= SNIPPET_MAX) {
            return text;
        }
        return text.substring(0, SNIPPET_MAX) + "...";
    }
}
