package com.aicode.framework.platform.infrastructure.eval;

import com.aicode.core.domain.model.EvalCase;
import com.aicode.core.domain.model.EvalCategory;
import com.aicode.core.domain.model.EvalCriterionType;
import com.aicode.core.domain.model.EvalDataset;
import com.aicode.core.domain.model.EvalExpectation;

import java.util.List;
import java.util.Map;

/**
 * classpath JSON 数据集反序列化 DTO。
 */
record EvalDatasetJson(
        String datasetKey,
        String name,
        String category,
        List<EvalCaseJson> cases
) {

    EvalDataset toDomain() {
        EvalCategory evalCategory = EvalCategory.valueOf(category);
        List<EvalCase> domainCases = cases == null
                ? List.of()
                : cases.stream().map(EvalCaseJson::toDomain).toList();
        return new EvalDataset(datasetKey, name, evalCategory, domainCases);
    }

    record EvalCaseJson(
            String caseId,
            String agentKey,
            Map<String, Object> input,
            String outputField,
            String fixtureOutput,
            EvalExpectationJson expectation,
            String description
    ) {
        EvalCase toDomain() {
            return new EvalCase(
                    caseId,
                    agentKey,
                    input,
                    outputField,
                    fixtureOutput,
                    expectation == null ? EvalExpectation.exact("") : expectation.toDomain(),
                    description
            );
        }
    }

    record EvalExpectationJson(
            String criterionType,
            String expected,
            List<String> keywords,
            List<String> forbidden,
            String pattern
    ) {
        EvalExpectation toDomain() {
            EvalCriterionType type = EvalCriterionType.valueOf(criterionType);
            return new EvalExpectation(type, expected, keywords, forbidden, pattern);
        }
    }
}
