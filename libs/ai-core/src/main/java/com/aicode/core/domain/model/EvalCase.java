package com.aicode.core.domain.model;

import java.util.Map;

/**
 * 单条评估用例：输入、期望与可选 fixture 输出。
 */
public record EvalCase(
        String caseId,
        String agentKey,
        Map<String, Object> input,
        String outputField,
        String fixtureOutput,
        EvalExpectation expectation,
        String description
) {

    public EvalCase {
        input = input == null ? Map.of() : Map.copyOf(input);
    }

    /** 是否使用 fixture 输出直接评分（不调用 Agent）。 */
    public boolean usesFixture() {
        return fixtureOutput != null && !fixtureOutput.isBlank();
    }
}
