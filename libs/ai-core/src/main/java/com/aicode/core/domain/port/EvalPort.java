package com.aicode.core.domain.port;

import com.aicode.core.domain.model.EvalCaseResult;
import com.aicode.core.domain.model.EvalCategory;
import com.aicode.core.domain.model.EvalExpectation;
import com.aicode.core.domain.model.EvalReport;

import java.time.Instant;
import java.util.List;

/**
 * Agent/RAG/Prompt 评估评分端口：规则驱动的自动评分与报告聚合。
 */
public interface EvalPort {

    /**
     * 对实际输出文本按期望准则评分。
     */
    EvalCaseResult scoreOutput(String caseId, EvalExpectation expectation, String actualOutput);

    /**
     * 对 Guardrail 回归用例评分（期望触发拦截）。
     */
    EvalCaseResult scoreGuardrailCase(String caseId, boolean violationThrown, String violationMessage);

    /**
     * 聚合多条用例结果为评估报告。
     */
    EvalReport aggregate(
            String runId,
            long userId,
            String datasetKey,
            EvalCategory category,
            List<EvalCaseResult> caseResults,
            Instant startedAt,
            Instant finishedAt
    );
}
