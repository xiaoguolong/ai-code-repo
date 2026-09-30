package com.aicode.core.domain.model;

import java.util.List;

/**
 * 评估数据集：一组相关测试用例的集合。
 */
public record EvalDataset(
        String datasetKey,
        String name,
        EvalCategory category,
        List<EvalCase> cases
) {

    public EvalDataset {
        cases = cases == null ? List.of() : List.copyOf(cases);
    }
}
