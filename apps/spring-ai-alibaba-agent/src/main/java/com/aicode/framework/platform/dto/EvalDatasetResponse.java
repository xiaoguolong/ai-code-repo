package com.aicode.framework.platform.dto;

import com.aicode.core.domain.model.EvalDataset;

/**
 * 评估数据集 API 响应。
 */
public record EvalDatasetResponse(
        String datasetKey,
        String name,
        String category,
        int caseCount
) {

    public static EvalDatasetResponse from(EvalDataset dataset) {
        return new EvalDatasetResponse(
                dataset.datasetKey(),
                dataset.name(),
                dataset.category().name(),
                dataset.cases().size());
    }
}
