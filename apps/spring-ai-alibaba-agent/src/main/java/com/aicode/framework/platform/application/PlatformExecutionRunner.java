package com.aicode.framework.platform.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.core.domain.exception.StreamCancelledException;
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
 * Agent 执行编排（Week 28，包内协作类；Week 31 拆出预检与流式事件）。
 *
 * <p>把「校验 → 落 RUNNING → span/trace → 调 Agent → 终态/待审 → 审计」留在单一位置；
 * {@link PlatformExecutionUseCase} 作为公共门面，保持构造器与 API 不变。</p>
 *
 * <p>Week 31 起拆成两步：{@link #prepare} 在请求线程同步做鉴权/Guardrail/Agent 校验并抛类型化异常
 * （保证 401/403/404/400 仍是 JSON + 正确状态码）；{@link #execute} 在调用线程（同步端点即请求线程，
 * 流式端点即 worker）执行并写记录。两条路径共用同一个 {@code execute}，避免"流式另写一套状态机"。</p>
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
     * 预检（Week 31）：鉴权 / 数据域 / Guardrail 入参 / Agent 存在且启用。
     *
     * @param userId   调用者
     * @param agentKey Agent 标识
     * @param input    Run 入参
     * @return 预检结果
     * @throws com.aicode.framework.platform.domain.exception.PlatformAccessDeniedException 越权
     * @throws PlatformNotFoundException Agent 未注册
     * @throws PlatformAgentDisabledException Agent 已禁用
     * @throws com.aicode.core.domain.exception.GuardrailViolationException 入参未过 Guardrail
     */
    PreparedExecution prepare(long userId, String agentKey, Map<String, Object> input) {
        permissionChecker.requireAgentRun(userId, agentKey);
        permissionChecker.requireRunInput(userId, input);
        platformGuardrailService.validateRunInput(userId, agentKey, input);

        PlatformAgentDefinition agent = agentRegistryPort.findByKey(requireKey(agentKey))
                .orElseThrow(() -> new PlatformNotFoundException("agent not found: " + agentKey));
        if (!agent.config().enabled()) {
            throw new PlatformAgentDisabledException("agent is disabled: " + agentKey);
        }
        return new PreparedExecution(userId, agentKey, agent, input);
    }

    /**
     * 同步执行：预检 + 执行，供既有 {@code POST /agents/{key}/runs} 使用。
     *
     * @param userId   调用者
     * @param agentKey Agent 标识
     * @param input    Run 入参
     * @return 终态（或待审）执行记录
     */
    ExecutionRecord runAgent(long userId, String agentKey, Map<String, Object> input) {
        return execute(prepare(userId, agentKey, input), RunEventSink.NONE);
    }

    /**
     * 执行已预检的 Run，并写执行记录/审计/指标。
     *
     * @param prepared 预检结果
     * @param sink     事件出口（同步端点用 {@link RunEventSink#NONE}）
     * @return 终态（或待审/取消）执行记录
     */
    ExecutionRecord execute(PreparedExecution prepared, RunEventSink sink) {
        long userId = prepared.userId();
        PlatformAgentDefinition agent = prepared.agent();
        Map<String, Object> input = prepared.input();

        String executionId = UUID.randomUUID().toString();
        String tenantKey = permissionChecker.tenantKeyOf(userId);
        Instant startedAt = Instant.now();
        ExecutionRecord running = recorder.running(
                executionId, userId, agent, recorder.toJson(input), tenantKey, startedAt);
        executionRecordPort.save(running);
        log.info("[platform] execution started executionId={} userId={} agentKey={} agentType={} input={}",
                executionId, userId, agent.agentKey(), agent.agentType(), running.inputJson());
        sink.onStarted(running);

        if (sink.isCancelled()) {
            ExecutionRecord cancelled = recorder.cancelled(
                    running, "client disconnected before execution", Instant.now());
            executionRecordPort.save(cancelled);
            sink.onCancelled(cancelled);
            log.info("[platform] execution cancelled before start executionId={} userId={} agentKey={}",
                    executionId, userId, agent.agentKey());
            return cancelled;
        }

        Map<String, String> spanAttributes = new LinkedHashMap<>();
        spanAttributes.put(ObservabilityAttributes.AGENT_KEY, agent.agentKey());
        spanAttributes.put(ObservabilityAttributes.AGENT_TYPE, String.valueOf(agent.agentType()));
        spanAttributes.put(ObservabilityAttributes.USER_ID, String.valueOf(userId));
        spanAttributes.put(ObservabilityAttributes.EXECUTION_ID, executionId);

        long runStartedAt = System.nanoTime();
        PlatformChatStreamListener streamListener = sink == RunEventSink.NONE ? null
                : new PlatformChatStreamListener(
                        sink, new StreamingTextSanitizer(platformGuardrailService, userId, agent.agentKey()));
        boolean streaming = streamListener != null;
        try (TraceScope trace = observability.beginTrace(
                executionObservability.traceDimensions(userId, agent, input));
             SpanScope span = observability.openSpan(
                     SpanKind.AGENT_RUN, SPAN_AGENT_RUN, spanAttributes)) {
            try {
                PlatformSecurityContext.beginPlatformRun(userId);
                PlatformRunOutput output = streamListener == null
                        ? platformAgentRunner.run(agent, input)
                        : platformAgentRunner.run(agent, input, streamListener);
                if (streamListener != null) {
                    streamListener.finish();
                    recordFirstDelta(agent, streamListener.firstDeltaAtNanos(), runStartedAt);
                }
                Map<String, Object> sanitizedOutput = platformGuardrailService.sanitizeRunOutput(
                        userId, agent.agentKey(), output.output());
                if (output.pendingApproval()) {
                    ExecutionRecord pending = pendingApprovalOrThrow().create(
                            new PlatformExecutionPendingApproval.Command(
                                    executionId, userId, agent, input, running, output, sanitizedOutput,
                                    span, runStartedAt));
                    if (streaming) {
                        executionObservability.recordStreamOutcome(agent.agentKey(), "completed");
                    }
                    sink.onCompleted(pending);
                    return pending;
                }
                ExecutionRecord completed = recorder.completed(
                        running, recorder.toJson(sanitizedOutput), output.model(), output.usage(), Instant.now());
                executionRecordPort.save(completed);
                log.info("[platform] execution completed executionId={} userId={} agentKey={} model={} tokens={}",
                        executionId, userId, agent.agentKey(), output.model(), output.usage().totalTokens());
                audit(executionId, userId, agent.agentKey(), AuditResult.SUCCESS, null);
                executionObservability.recordSuccess(span, agent, output, runStartedAt);
                executionObservability.recordStreamOutcome(agent.agentKey(), "completed");
                sink.onCompleted(completed);
                return completed;

            } catch (StreamCancelledException ex) {
                ExecutionRecord cancelled = recorder.cancelled(
                        running, "cancelled by client", Instant.now());
                executionRecordPort.save(cancelled);
                log.info("[platform] execution cancelled executionId={} userId={} agentKey={}",
                        executionId, userId, agent.agentKey());
                audit(executionId, userId, agent.agentKey(), AuditResult.FAILURE, "cancelled by client");
                executionObservability.recordCancelled(span, agent, runStartedAt);
                sink.onCancelled(cancelled);
                return cancelled;

            } catch (RuntimeException ex) {
                ExecutionRecord failed = recorder.failed(running, ex.getMessage(), Instant.now());
                executionRecordPort.save(failed);
                log.warn("[platform] execution failed executionId={} userId={} agentKey={} error={}",
                        executionId, userId, agent.agentKey(), ex.getMessage());
                audit(executionId, userId, agent.agentKey(), AuditResult.FAILURE, ex.getMessage());
                executionObservability.recordFailure(span, agent, ex, runStartedAt);
                if (streaming) {
                    executionObservability.recordStreamOutcome(agent.agentKey(), "failed");
                }
                sink.onFailed(failed, ex);
                throw ex;
            } finally {
                PlatformSecurityContext.clear();
            }
        }
    }

    private void recordFirstDelta(PlatformAgentDefinition agent, long firstDeltaAtNanos, long runStartedAt) {
        if (firstDeltaAtNanos <= 0) {
            return;
        }
        executionObservability.recordFirstDelta(
                agent, (firstDeltaAtNanos - runStartedAt) / 1_000_000L);
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
