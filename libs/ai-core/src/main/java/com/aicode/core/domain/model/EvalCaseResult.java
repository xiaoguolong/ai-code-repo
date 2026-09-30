package com.aicode.core.domain.model;

/**
 * 单条用例评分结果。
 */
public record EvalCaseResult(
        String caseId,
        boolean passed,
        double score,
        String message,
        String actualSnippet
) {
}
