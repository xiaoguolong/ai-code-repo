package com.aicode.framework.platform.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.framework.infrastructure.logging.TraceIds;
import com.aicode.framework.observability.domain.AgentObservabilityPort;
import com.aicode.framework.observability.domain.ObservabilityAttributes;
import com.aicode.framework.observability.domain.SpanKind;
import com.aicode.framework.observability.domain.SpanScope;
import com.aicode.framework.observability.domain.TraceScope;
import com.aicode.framework.platform.domain.exception.PlatformAgentDisabledException;
import com.aicode.framework.platform.domain.exception.PlatformNotFoundException;
import com.aicode.framework.platform.domain.model.AuditAction;
import com.aicode.framework.platform.domain.model.AuditLogEntry;
import com.aicode.framework.platform.domain.model.AuditResult;
import com.aicode.framework.platform.domain.model.ExecutionRecord;
import com.aicode.framework.platform.domain.model.PlatformAgentDefinition;
import com.aicode.framework.platform.domain.model.PlatformRunOutput;
import com.aicode.framework.platform.domain.port.AgentRegistryPort;
import com.aicode.framework.platform.domain.port.AuditLogPort;
import com.aicode.framework.platform.domain.port.ExecutionRecordPort;
import com.aicode.framework.platform.domain.service.PlatformAgentRunner;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import com.aicode.framework.platform.infrastructure.security.PlatformSecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 执行编排（Week 28，包内协作类）。
 *
 * <p>把「校验 → 落 RUNNING → span/trace → 调 Agent → 终态/待审 → 审计」留在单一位置；
 * {@link PlatformExecutionUseCase} 作为公共门面，保持构造器与 API 不变。</p>
 */
final class PlatformExecutionRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformExecutionRunner.class);

    /** Agent 执行 span 名（与链路后端检索口径一致）。 */
    static final String SPAN_AGENT_RUN = "agent.run";

    private final AgentRegistryPort agentRegistryPort;
    private final ExecutionRecordPort executionRecordPort;
    private final PlatformAgentRunner platformAgentRunner;
    private final PlatformPermissionChecker permissionChecker;
    private final PlatformGuardrailService platformGuardrailService;
    private final AuditLogPort auditLogPort;
    private final AgentObservabilityPort observability;
    private final PlatformExecutionRecorder recorder;
    private final PlatformExecutionObservability executionObservability;
    private final PlatformExecutionPendingApproval pendingApproval;

    PlatformExecutionRunner(
            AgentRegistryPort agentRegistryPort,
            ExecutionRecordPort executionRecordPort,
            PlatformAgentRunner platformAgentRunner,
            PlatformPermissionChecker permissionChecker,
            PlatformGuardrailService platformGuardrailService,
            AuditLogPort auditLogPort,
            AgentObservabilityPort observability,
            PlatformExecutionRecorder recorder,
            PlatformExecutionObservability executionObservability,
            PlatformExecutionPendingApproval pendingApproval
    ) {
        this.agentRegistryPort = agentRegistryPort;
        this.executionRecordPort = executionRecordPort;
        this.platformAgentRunner = platformAgentRunner;
        this.permissionChecker = permissionChecker;
        this.platformGuardrailService = platformGuardrailService;
        this.auditLogPort = auditLogPort;
        this.observability = observability;
        this.recorder = recorder;
        this.executionObservability = executionObservability;
        this.pendingApproval = pendingApproval;
    }

    /**
     * 调度 Agent 并写执行记录。
     *
     * @param userId   调用者
     * @param agentKey Agent 标识
     * @param input    Run 入参
     * @return 终态（或待审）执行记录
     */
    ExecutionRecord runAgent(long userId, String agentKey, Map<String, Object> input) {
        permissionChecker.requireAgentRun(userId, agentKey);
        permissionChecker.requireRunInput(userId, input);
        platformGuardrailService.validateRunInput(userId, agentKey, input);

        PlatformAgentDefinition agent = agentRegistryPort.findByKey(requireKey(agentKey))
                .orElseThrow(() -> new PlatformNotFoundException("agent not found: " + agentKey));
        if (!agent.config().enabled()) {
            throw new PlatformAgentDisabledException("agent is disabled: " + agentKey);
        }

        String executionId = UUID.randomUUID().toString();
        String tenantKey = permissionChecker.tenantKeyOf(userId);
        Instant startedAt = Instant.now();
        ExecutionRecord running = recorder.running(
                executionId, userId, agent, recorder.toJson(input), tenantKey, startedAt);
        executionRecordPort.save(running);
        log.info("[platform] execution started executionId={} userId={} agentKey={} agentType={} input={}",
                executionId, userId, agent.agentKey(), agent.agentType(), running.inputJson());

        Map<String, String> spanAttributes = new LinkedHashMap<>();
        spanAttributes.put(ObservabilityAttributes.AGENT_KEY, agent.agentKey());
        spanAttributes.put(ObservabilityAttributes.AGENT_TYPE, String.valueOf(agent.agentType()));
        spanAttributes.put(ObservabilityAttributes.USER_ID, String.valueOf(userId));
        spanAttributes.put(ObservabilityAttributes.EXECUTION_ID, executionId);

        long runStartedAt = System.nanoTime();
        try (TraceScope trace = observability.beginTrace(
                executionObservability.traceDimensions(userId, agent, input));
             SpanScope span = observability.openSpan(
                     SpanKind.AGENT_RUN, SPAN_AGENT_RUN, spanAttributes)) {
            try {
                PlatformSecurityContext.beginPlatformRun(userId);
                PlatformRunOutput output = platformAgentRunner.run(agent, input);
                Map<String, Object> sanitizedOutput = platformGuardrailService.sanitizeRunOutput(
                        userId, agent.agentKey(), output.output());
                if (output.pendingApproval()) {
                    return pendingApprovalOrThrow().create(new PlatformExecutionPendingApproval.Command(
                            executionId, userId, agent, input, running, output, sanitizedOutput,
                            span, runStartedAt));
                }
                ExecutionRecord completed = recorder.completed(
                        running, recorder.toJson(sanitizedOutput), output.model(), output.usage(), Instant.now());
                executionRecordPort.save(completed);
                log.info("[platform] execution completed executionId={} userId={} agentKey={} model={} tokens={}",
                        executionId, userId, agent.agentKey(), output.model(), output.usage().totalTokens());
                audit(executionId, userId, agent.agentKey(), AuditResult.SUCCESS, null);
                executionObservability.recordSuccess(span, agent, output, runStartedAt);
                return completed;

            } catch (RuntimeException ex) {
                ExecutionRecord failed = recorder.failed(running, ex.getMessage(), Instant.now());
                executionRecordPort.save(failed);
                log.warn("[platform] execution failed executionId={} userId={} agentKey={} error={}",
                        executionId, userId, agent.agentKey(), ex.getMessage());
                audit(executionId, userId, agent.agentKey(), AuditResult.FAILURE, ex.getMessage());
                executionObservability.recordFailure(span, agent, ex, runStartedAt);
                throw ex;
            } finally {
                PlatformSecurityContext.clear();
            }
        }
    }

    private PlatformExecutionPendingApproval pendingApprovalOrThrow() {
        if (pendingApproval == null) {
            throw new IllegalStateException("approval use case is not configured");
        }
        return pendingApproval;
    }

    private void audit(String executionId, long userId, String agentKey, AuditResult result, String detail) {
        try {
            auditLogPort.record(AuditLogEntry.of(
                    userId,
                    AuditAction.AGENT_RUN,
                    "agent:" + agentKey,
                    result,
                    TraceIds.current(),
                    "executionId=" + executionId
                            + ((detail == null || detail.isBlank()) ? "" : " error=" + detail)));
        } catch (RuntimeException ex) {
            log.warn("[platform] audit record failed action=AGENT_RUN userId={} error={}",
                    userId, ex.getMessage());
        }
    }

    private String requireKey(String value) {
        String key = value == null ? "" : value.trim();
        if (key.isEmpty()) {
            throw new InvalidChatRequestException("id must not be blank");
        }
        return key;
    }
}
