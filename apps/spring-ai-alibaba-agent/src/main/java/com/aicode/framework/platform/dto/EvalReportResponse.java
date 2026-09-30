package com.aicode.framework.platform.dto;

import com.aicode.core.domain.model.EvalCaseResult;
import com.aicode.core.domain.model.EvalReport;

import java.time.Instant;
import java.util.List;

/**
 * 评估报告 API 响应。
 */
public record EvalReportResponse(
        String runId,
        long userId,
        String datasetKey,
        String category,
        int passedCount,
        int totalCount,
        double passRate,
        Instant startedAt,
        Instant finishedAt,
        List<EvalCaseResultResponse> caseResults
) {

    public static EvalReportResponse from(EvalReport report) {
        List<EvalCaseResultResponse> cases = report.caseResults().stream()
                .map(EvalCaseResultResponse::from)
                .toList();
        return new EvalReportResponse(
                report.runId(),
                report.userId(),
                report.datasetKey(),
                report.category().name(),
                report.passedCount(),
                report.totalCount(),
                report.passRate(),
                report.startedAt(),
                report.finishedAt(),
                cases);
    }

    public record EvalCaseResultResponse(
            String caseId,
            boolean passed,
            double score,
            String message,
            String actualSnippet
    ) {
        static EvalCaseResultResponse from(EvalCaseResult result) {
            return new EvalCaseResultResponse(
                    result.caseId(),
                    result.passed(),
                    result.score(),
                    result.message(),
                    result.actualSnippet());
        }
    }
}
