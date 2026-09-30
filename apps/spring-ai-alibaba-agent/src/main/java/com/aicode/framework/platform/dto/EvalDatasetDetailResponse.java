package com.aicode.framework.platform.dto;

import com.aicode.core.domain.model.EvalCase;
import com.aicode.core.domain.model.EvalDataset;

import java.util.List;

/**
 * 评估数据集详情 API 响应（含用例摘要，不含 fixture 全文）。
 */
public record EvalDatasetDetailResponse(
        String datasetKey,
        String name,
        String category,
        List<EvalCaseSummary> cases
) {

    public static EvalDatasetDetailResponse from(EvalDataset dataset) {
        List<EvalCaseSummary> summaries = dataset.cases().stream()
                .map(EvalCaseSummary::from)
                .toList();
        return new EvalDatasetDetailResponse(
                dataset.datasetKey(), dataset.name(), dataset.category().name(), summaries);
    }

    public record EvalCaseSummary(
            String caseId,
            String agentKey,
            String criterionType,
            String description
    ) {
        static EvalCaseSummary from(EvalCase evalCase) {
            return new EvalCaseSummary(
                    evalCase.caseId(),
                    evalCase.agentKey(),
                    evalCase.expectation().criterionType().name(),
                    evalCase.description());
        }
    }
}
