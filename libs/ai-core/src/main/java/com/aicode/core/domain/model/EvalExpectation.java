package com.aicode.core.domain.model;

import java.util.List;

/**
 * 评估期望：准则类型及对应参数。
 */
public record EvalExpectation(
        EvalCriterionType criterionType,
        String expected,
        List<String> keywords,
        List<String> forbidden,
        String pattern
) {

    public EvalExpectation {
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        forbidden = forbidden == null ? List.of() : List.copyOf(forbidden);
    }

    /** 精确匹配期望。 */
    public static EvalExpectation exact(String expected) {
        return new EvalExpectation(EvalCriterionType.EXACT_MATCH, expected, List.of(), List.of(), null);
    }

    /** 必须包含全部关键词。 */
    public static EvalExpectation containsAll(String... keywords) {
        return new EvalExpectation(EvalCriterionType.CONTAINS_ALL, null, List.of(keywords), List.of(), null);
    }

    /** 正则匹配期望。 */
    public static EvalExpectation regex(String pattern) {
        return new EvalExpectation(EvalCriterionType.REGEX, null, List.of(), List.of(), pattern);
    }

    /** 不得包含禁用词。 */
    public static EvalExpectation notContains(String... forbidden) {
        return new EvalExpectation(EvalCriterionType.NOT_CONTAINS, null, List.of(), List.of(forbidden), null);
    }

    /** Guardrail 应拦截输入。 */
    public static EvalExpectation guardrailBlocked() {
        return new EvalExpectation(EvalCriterionType.GUARDRAIL_BLOCKED, null, List.of(), List.of(), null);
    }
}
