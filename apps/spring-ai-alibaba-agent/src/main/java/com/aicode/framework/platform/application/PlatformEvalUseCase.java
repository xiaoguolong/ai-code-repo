package com.aicode.framework.platform.application;

import com.aicode.core.domain.exception.GuardrailViolationException;
import com.aicode.core.domain.model.EvalCase;
import com.aicode.core.domain.model.EvalCaseResult;
import com.aicode.core.domain.model.EvalCategory;
import com.aicode.core.domain.model.EvalDataset;
import com.aicode.core.domain.model.EvalReport;
import com.aicode.core.domain.port.EvalPort;
import com.aicode.framework.platform.domain.exception.PlatformNotFoundException;
import com.aicode.framework.platform.domain.model.PlatformAgentDefinition;
import com.aicode.framework.platform.domain.model.PlatformRunOutput;
import com.aicode.framework.platform.domain.port.AgentRegistryPort;
import com.aicode.framework.platform.domain.port.EvalDatasetPort;
import com.aicode.framework.platform.domain.port.EvalRunPort;
import com.aicode.framework.platform.domain.service.PlatformAgentRunner;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 平台评估用例：加载测试集、批量执行、自动评分并持久化报告。
 */
@Service
public class PlatformEvalUseCase {

    private static final Logger log = LoggerFactory.getLogger(PlatformEvalUseCase.class);

    private final EvalDatasetPort evalDatasetPort;
    private final EvalRunPort evalRunPort;
    private final EvalPort evalPort;
    private final AgentRegistryPort agentRegistryPort;
    private final PlatformAgentRunner platformAgentRunner;
    private final PlatformGuardrailService platformGuardrailService;
    private final PlatformPermissionChecker permissionChecker;

    public PlatformEvalUseCase(
            EvalDatasetPort evalDatasetPort,
            EvalRunPort evalRunPort,
            EvalPort evalPort,
            AgentRegistryPort agentRegistryPort,
            PlatformAgentRunner platformAgentRunner,
            PlatformGuardrailService platformGuardrailService,
            PlatformPermissionChecker permissionChecker
    ) {
        this.evalDatasetPort = evalDatasetPort;
        this.evalRunPort = evalRunPort;
        this.evalPort = evalPort;
        this.agentRegistryPort = agentRegistryPort;
        this.platformAgentRunner = platformAgentRunner;
        this.platformGuardrailService = platformGuardrailService;
        this.permissionChecker = permissionChecker;
    }

    /** 列出全部评估数据集。 */
    public List<EvalDataset> listDatasets() {
        return evalDatasetPort.listAll();
    }

    /** 获取单个评估数据集。 */
    public EvalDataset getDataset(String datasetKey) {
        return evalDatasetPort.findByKey(requireKey(datasetKey))
                .orElseThrow(() -> new PlatformNotFoundException("eval dataset not found: " + datasetKey));
    }

    /** 对指定数据集批量运行评估并返回报告。 */
    public EvalReport runDataset(long userId, String datasetKey) {
        EvalDataset dataset = getDataset(datasetKey);
        requireDatasetAccess(userId, dataset);
        Instant startedAt = Instant.now();
        String runId = UUID.randomUUID().toString();
        log.info("[eval] run started runId={} userId={} datasetKey={} cases={}",
                runId, userId, datasetKey, dataset.cases().size());

        List<EvalCaseResult> results = new ArrayList<>();
        for (EvalCase evalCase : dataset.cases()) {
            results.add(runCase(userId, dataset.category(), evalCase));
        }

        Instant finishedAt = Instant.now();
        EvalReport report = evalPort.aggregate(
                runId, userId, dataset.datasetKey(), dataset.category(), results, startedAt, finishedAt);
        evalRunPort.save(report);
        log.info("[eval] run finished runId={} passRate={} passed={}/{}",
                runId, report.passRate(), report.passedCount(), report.totalCount());
        return report;
    }

    /** 获取评估报告（本人或 admin）。 */
    public EvalReport getRun(long userId, String runId) {
        EvalReport report = evalRunPort.findById(runId)
                .orElseThrow(() -> new PlatformNotFoundException("eval run not found: " + runId));
        if (!permissionChecker.isAdmin(userId) && report.userId() != userId) {
            throw new PlatformNotFoundException("eval run not found: " + runId);
        }
        return report;
    }

    private EvalCaseResult runCase(long userId, EvalCategory category, EvalCase evalCase) {
        if (category == EvalCategory.GUARDRAIL
                || evalCase.expectation().criterionType() == com.aicode.core.domain.model.EvalCriterionType.GUARDRAIL_BLOCKED) {
            return runGuardrailCase(userId, evalCase);
        }
        if (evalCase.usesFixture()) {
            return evalPort.scoreOutput(evalCase.caseId(), evalCase.expectation(), evalCase.fixtureOutput());
        }
        return runAgentCase(userId, evalCase);
    }

    private EvalCaseResult runGuardrailCase(long userId, EvalCase evalCase) {
        String agentKey = evalCase.agentKey() == null ? "eval" : evalCase.agentKey();
        try {
            platformGuardrailService.validateRunInput(userId, agentKey, evalCase.input());
            return evalPort.scoreGuardrailCase(evalCase.caseId(), false, null);
        } catch (GuardrailViolationException ex) {
            return evalPort.scoreGuardrailCase(evalCase.caseId(), true, ex.getMessage());
        }
    }

    private EvalCaseResult runAgentCase(long userId, EvalCase evalCase) {
        String agentKey = requireKey(evalCase.agentKey());
        PlatformAgentDefinition agent = agentRegistryPort.findByKey(agentKey)
                .orElseThrow(() -> new PlatformNotFoundException("agent not found: " + agentKey));
        try {
            PlatformRunOutput output = platformAgentRunner.run(agent, evalCase.input());
            String actual = extractOutputField(output.output(), evalCase.outputField());
            return evalPort.scoreOutput(evalCase.caseId(), evalCase.expectation(), actual);
        } catch (Exception ex) {
            return new EvalCaseResult(
                    evalCase.caseId(), false, 0.0,
                    "agent run failed: " + ex.getMessage(), null);
        }
    }

    private void requireDatasetAccess(long userId, EvalDataset dataset) {
        Set<String> agentKeys = new LinkedHashSet<>();
        for (EvalCase evalCase : dataset.cases()) {
            if (evalCase.agentKey() != null && !evalCase.agentKey().isBlank() && !evalCase.usesFixture()) {
                agentKeys.add(evalCase.agentKey());
            }
        }
        for (String agentKey : agentKeys) {
            permissionChecker.requireAgentRun(userId, agentKey);
        }
    }

    static String extractOutputField(Map<String, Object> output, String outputField) {
        if (output == null || output.isEmpty()) {
            return "";
        }
        if (outputField == null || outputField.isBlank()) {
            return output.toString();
        }
        Object value = output.get(outputField);
        return value == null ? "" : value.toString();
    }

    private static String requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new PlatformNotFoundException("key must not be blank");
        }
        return key.trim();
    }
}
