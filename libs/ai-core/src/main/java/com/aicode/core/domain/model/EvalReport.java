package com.aicode.core.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * 一次批量评估运行的汇总报告。
 */
public record EvalReport(
        String runId,
        long userId,
        String datasetKey,
        EvalCategory category,
        List<EvalCaseResult> caseResults,
        int passedCount,
        int totalCount,
        double passRate,
        Instant startedAt,
        Instant finishedAt
) {

    public EvalReport {
        caseResults = caseResults == null ? List.of() : List.copyOf(caseResults);
    }
}
