package com.aicode.core.domain.model;

/**
 * 单条评估用例的评分准则类型。
 */
public enum EvalCriterionType {
    EXACT_MATCH,
    CONTAINS_ALL,
    REGEX,
    NOT_CONTAINS,
    GUARDRAIL_BLOCKED
}
