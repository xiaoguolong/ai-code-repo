package com.aicode.framework.platform.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.core.domain.model.TokenUsage;
import com.aicode.framework.platform.domain.exception.PlatformAccessDeniedException;
import com.aicode.framework.platform.domain.exception.PlatformAgentDisabledException;
import com.aicode.framework.platform.domain.exception.PlatformNotFoundException;
import com.aicode.framework.platform.domain.model.ApprovalStatus;
import com.aicode.framework.platform.domain.model.AuditAction;
import com.aicode.framework.platform.domain.model.AuditLogEntry;
import com.aicode.framework.platform.domain.model.AuditResult;
import com.aicode.framework.platform.domain.model.ExecutionRecord;
import com.aicode.framework.platform.domain.model.ExecutionStatus;
import com.aicode.framework.platform.domain.model.PlatformAgentDefinition;
import com.aicode.framework.platform.domain.model.PlatformApproval;
import com.aicode.framework.platform.domain.model.PlatformRunOutput;
import com.aicode.framework.platform.domain.port.AgentRegistryPort;
import com.aicode.framework.platform.domain.port.AuditLogPort;
import com.aicode.framework.platform.domain.port.ExecutionRecordPort;
import com.aicode.framework.platform.domain.service.PlatformAgentRunner;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import com.aicode.framework.infrastructure.logging.TraceIds;
import com.aicode.framework.observability.domain.AgentObservabilityPort;
import com.aicode.framework.observability.domain.MonitoringMetricNames;
import com.aicode.framework.observability.domain.ObservabilityAttributes;
import com.aicode.framework.observability.domain.SpanKind;
import com.aicode.framework.observability.domain.SpanScope;
import com.aicode.framework.observability.domain.TraceDimensions;
import com.aicode.framework.observability.domain.TraceScope;
import com.aicode.framework.platform.infrastructure.security.PlatformSecurityContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 平台执行记录与 Agent 调度用例（含 RBAC 校验与用户隔离）。
 *
 * <p>Week 16：执行终态与越权读取写审计。鉴权在审计之前，越权请求不产生执行记录也不写
 * {@code AGENT_RUN}（避免噪声），只在读他人记录被拒时写 {@code EXECUTION_READ/DENIED}。</p>
 *
 * <p>Week 17：整个执行包一个 {@code agent.run} span，并登记 {@code agent.run.count} /
 * {@code agent.run.duration} 指标（标签含 {@code execution.status}），使「Agent 跑一次多久、
 * 烧多少 token、成功还是失败」可在链路与指标两处查看。</p>
 */
@Service
public class PlatformExecutionUseCase {

    private static final Logger log = LoggerFactory.getLogger(PlatformExecutionUseCase.class);

    /** Agent 执行 span 名。 */
    static final String SPAN_AGENT_RUN = "agent.run";

    private final AgentRegistryPort agentRegistryPort;
    private final ExecutionRecordPort executionRecordPort;
    private final PlatformAgentRunner platformAgentRunner;
    private final PlatformPermissionChecker permissionChecker;
    private final PlatformGuardrailService platformGuardrailService;
    private final ObjectMapper objectMapper;
    private final AuditLogPort auditLogPort;
    private final AgentObservabilityPort observability;
    private final PlatformApprovalUseCase approvalUseCase;

    /**
     * Week 8–22 兼容构造器（不含审批能力，供既有单测直接 new）。
     *
     * @param agentRegistryPort         Agent 注册表
     * @param executionRecordPort       执行记录
     * @param platformAgentRunner       Agent 调度
     * @param permissionChecker         权限校验
     * @param platformGuardrailService  Guardrail
     * @param objectMapper              JSON
     * @param auditLogPort              审计
     * @param observability             观测
     */
    public PlatformExecutionUseCase(
            AgentRegistryPort agentRegistryPort,
            ExecutionRecordPort executionRecordPort,
            PlatformAgentRunner platformAgentRunner,
            PlatformPermissionChecker permissionChecker,
            PlatformGuardrailService platformGuardrailService,
            ObjectMapper objectMapper,
            AuditLogPort auditLogPort,
            AgentObservabilityPort observability
    ) {
        this(agentRegistryPort, executionRecordPort, platformAgentRunner, permissionChecker,
                platformGuardrailService, objectMapper, auditLogPort, observability, null);
    }

    /**
     * Spring 注入构造器（Week 23 增加审批用例）。
     *
     * <p>多个构造器必须显式 {@code @Autowired}（第 19/20 周真机踩过启动失败）。</p>
     *
     * @param agentRegistryPort         Agent 注册表
     * @param executionRecordPort       执行记录
     * @param platformAgentRunner       Agent 调度
     * @param permissionChecker         权限校验
     * @param platformGuardrailService  Guardrail
     * @param objectMapper              JSON
     * @param auditLogPort              审计
     * @param observability             观测
     * @param approvalUseCase           人工审批用例（HIGH 风险患者分析）
     */
    @Autowired
    public PlatformExecutionUseCase(
            AgentRegistryPort agentRegistryPort,
            ExecutionRecordPort executionRecordPort,
            PlatformAgentRunner platformAgentRunner,
            PlatformPermissionChecker permissionChecker,
            PlatformGuardrailService platformGuardrailService,
            ObjectMapper objectMapper,
            AuditLogPort auditLogPort,
            AgentObservabilityPort observability,
            PlatformApprovalUseCase approvalUseCase
    ) {
        this.agentRegistryPort = agentRegistryPort;
        this.executionRecordPort = executionRecordPort;
        this.platformAgentRunner = platformAgentRunner;
        this.permissionChecker = permissionChecker;
        this.platformGuardrailService = platformGuardrailService;
        this.objectMapper = objectMapper;
        this.auditLogPort = auditLogPort;
        this.observability = observability;
        this.approvalUseCase = approvalUseCase;
    }

    /**
     * 经平台调度 Agent 并写入执行记录。
     *
     * @param userId   调用者用户 ID（来自登录态）
     * @param agentKey 注册表中的 Agent 标识
     * @param input    Run 入参，可含 patientId / task
     * @return 终态执行记录（COMPLETED 或抛出异常前落 FAILED）
     * @throws PlatformAccessDeniedException Agent 或数据域越权
     * @throws PlatformNotFoundException     Agent 未注册
     * @throws PlatformAgentDisabledException Agent 已禁用
     */
    public ExecutionRecord runAgent(long userId, String agentKey, Map<String, Object> input) {
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
        ExecutionRecord running = new ExecutionRecord(
                executionId, userId, agent.agentKey(), agent.agentType(), ExecutionStatus.RUNNING,
                toJson(input), "", "", TokenUsage.unknown(), "", startedAt, null, tenantKey);
        executionRecordPort.save(running);
        log.info("[platform] execution started executionId={} userId={} agentKey={} agentType={} input={}",
                executionId, userId, agent.agentKey(), agent.agentType(), running.inputJson());

        Map<String, String> spanAttributes = new LinkedHashMap<>();
        spanAttributes.put(ObservabilityAttributes.AGENT_KEY, agent.agentKey());
        spanAttributes.put(ObservabilityAttributes.AGENT_TYPE, String.valueOf(agent.agentType()));
        spanAttributes.put(ObservabilityAttributes.USER_ID, String.valueOf(userId));
        spanAttributes.put(ObservabilityAttributes.EXECUTION_ID, executionId);

        long runStartedAt = System.nanoTime();
        try (TraceScope trace = observability.beginTrace(traceDimensions(userId, agent, input));
             SpanScope span = observability.openSpan(SpanKind.AGENT_RUN, SPAN_AGENT_RUN, spanAttributes)) {
            try {
                PlatformSecurityContext.beginPlatformRun(userId);
                PlatformRunOutput output = platformAgentRunner.run(agent, input);
                Map<String, Object> sanitizedOutput = platformGuardrailService.sanitizeRunOutput(
                        userId, agent.agentKey(), output.output());
                if (output.pendingApproval()) {
                    return recordPendingApproval(executionId, userId, agent, input, running,
                            output, sanitizedOutput, span, runStartedAt, startedAt);
                }
                ExecutionRecord completed = new ExecutionRecord(
                        executionId, userId, agent.agentKey(), agent.agentType(), ExecutionStatus.COMPLETED,
                        running.inputJson(), toJson(sanitizedOutput), output.model(), output.usage(),
                        "", startedAt, Instant.now(), running.tenantKey());
                executionRecordPort.save(completed);
                log.info("[platform] execution completed executionId={} userId={} agentKey={} model={} tokens={}",
                        executionId, userId, agent.agentKey(), output.model(), output.usage().totalTokens());
                audit(executionId, userId, agent.agentKey(), AuditResult.SUCCESS, null);
                recordRunSuccess(span, agent, output, runStartedAt);
                return completed;

            } catch (RuntimeException ex) {
                ExecutionRecord failed = new ExecutionRecord(
                        executionId, userId, agent.agentKey(), agent.agentType(), ExecutionStatus.FAILED,
                        running.inputJson(), "", "", TokenUsage.unknown(), ex.getMessage(),
                        startedAt, Instant.now(), running.tenantKey());
                executionRecordPort.save(failed);
                log.warn("[platform] execution failed executionId={} userId={} agentKey={} error={}",
                        executionId, userId, agent.agentKey(), ex.getMessage());
                audit(executionId, userId, agent.agentKey(), AuditResult.FAILURE, ex.getMessage());
                recordRunFailure(span, agent, ex, runStartedAt);
                throw ex;
            } finally {
                PlatformSecurityContext.clear();
            }
        }
    }

    /**
     * HIGH 风险患者分析：落待审执行记录 + 创建审批任务（Week 23）。
     *
     * <p>不写 {@code AGENT_RUN} 审计（执行尚未终态）；审批任务创建本身写
     * {@code APPROVAL_SUBMIT}。决策后的终态与审计由 {@link PlatformApprovalUseCase} 负责。</p>
     */
    private ExecutionRecord recordPendingApproval(
            String executionId,
            long userId,
            PlatformAgentDefinition agent,
            Map<String, Object> input,
            ExecutionRecord running,
            PlatformRunOutput output,
            Map<String, Object> sanitizedOutput,
            SpanScope span,
            long runStartedAt,
            Instant startedAt
    ) {
        if (approvalUseCase == null) {
            throw new IllegalStateException("approval use case is not configured");
        }
        String patientId = text(input == null ? null : input.get("patientId"));
        String riskLevel = text(sanitizedOutput.get("riskLevel"));
        String task = text(sanitizedOutput.get("task"));
        PlatformApproval approval = approvalUseCase.create(
                executionId, userId, agent.agentKey(), patientId, riskLevel, task);

        Map<String, Object> pendingOutput = new LinkedHashMap<>(sanitizedOutput);
        pendingOutput.put("approvalId", approval.approvalId());
        pendingOutput.put("approvalStatus", ApprovalStatus.PENDING.name());
        ExecutionRecord pending = new ExecutionRecord(
                executionId, userId, agent.agentKey(), agent.agentType(),
                ExecutionStatus.PENDING_APPROVAL, running.inputJson(), toJson(pendingOutput),
                output.model(), output.usage(), "", startedAt, null, running.tenantKey());
        executionRecordPort.save(pending);
        log.info("[platform] execution pending approval executionId={} userId={} agentKey={} approvalId={} riskLevel={}",
                executionId, userId, agent.agentKey(), approval.approvalId(), riskLevel);
        recordPendingApprovalMetrics(span, agent, output, runStartedAt);
        return pending;
    }

    /**
     * 待审终态的观测：span 属性 + {@code execution.status=PENDING_APPROVAL} 指标。
     */
    private void recordPendingApprovalMetrics(
            SpanScope span,
            PlatformAgentDefinition agent,
            PlatformRunOutput output,
            long runStartedAt
    ) {
        span.attribute(ObservabilityAttributes.EXECUTION_STATUS, ExecutionStatus.PENDING_APPROVAL.name());
        span.attribute(ObservabilityAttributes.DURATION_MS, String.valueOf(elapsedMs(runStartedAt)));
        if (output.model() != null && !output.model().isBlank()) {
            span.attribute(ObservabilityAttributes.GEN_AI_REQUEST_MODEL, output.model());
        }
        if (output.usage() != null) {
            span.attribute(ObservabilityAttributes.GEN_AI_USAGE_TOTAL_TOKENS,
                    String.valueOf(output.usage().totalTokens()));
        }
        observability.recordCounter(MonitoringMetricNames.AGENT_RUN_COUNT, 1.0,
                ObservabilityAttributes.AGENT_KEY, agent.agentKey(),
                ObservabilityAttributes.EXECUTION_STATUS, ExecutionStatus.PENDING_APPROVAL.name());
        observability.recordDuration(MonitoringMetricNames.AGENT_RUN_DURATION, elapsedMs(runStartedAt),
                ObservabilityAttributes.AGENT_KEY, agent.agentKey(),
                ObservabilityAttributes.EXECUTION_STATUS, ExecutionStatus.PENDING_APPROVAL.name());
    }

    private String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    /**
     * 构造本次执行的 Langfuse trace 维度（Week 18）。
     *
     * <p>会话语义：用 Run 入参里的 {@code patientId} 而不是 executionId —— 同一患者的多次分析归为一个会话，
     * 在 Langfuse 上按会话回看随访全流程才有意义；缺 patientId 时不写会话维度。</p>
     */
    private TraceDimensions traceDimensions(long userId, PlatformAgentDefinition agent, Map<String, Object> input) {
        Object patientId = input == null ? null : input.get("patientId");
        String sessionId = patientId == null ? null : String.valueOf(patientId);
        return TraceDimensions.agentRun(
                userId, agent.agentKey(), String.valueOf(agent.agentType()), sessionId);
    }

    /**
     * 成功终态的观测记录：span 属性（模型、token 数）+ 计数与耗时指标（状态标签 COMPLETED）。
     */
    private void recordRunSuccess(
            SpanScope span,
            PlatformAgentDefinition agent,
            PlatformRunOutput output,
            long runStartedAt
    ) {
        span.attribute(ObservabilityAttributes.EXECUTION_STATUS, ExecutionStatus.COMPLETED.name());
        span.attribute(ObservabilityAttributes.DURATION_MS, String.valueOf(elapsedMs(runStartedAt)));
        if (output.model() != null && !output.model().isBlank()) {
            span.attribute(ObservabilityAttributes.GEN_AI_REQUEST_MODEL, output.model());
        }
        if (output.usage() != null) {
            span.attribute(ObservabilityAttributes.GEN_AI_USAGE_TOTAL_TOKENS,
                    String.valueOf(output.usage().totalTokens()));
        }
        long durationMs = elapsedMs(runStartedAt);
        observability.recordCounter(MonitoringMetricNames.AGENT_RUN_COUNT, 1.0,
                ObservabilityAttributes.AGENT_KEY, agent.agentKey(),
                ObservabilityAttributes.EXECUTION_STATUS, ExecutionStatus.COMPLETED.name());
        observability.recordDuration(MonitoringMetricNames.AGENT_RUN_DURATION, durationMs,
                ObservabilityAttributes.AGENT_KEY, agent.agentKey(),
                ObservabilityAttributes.EXECUTION_STATUS, ExecutionStatus.COMPLETED.name());
    }

    /**
     * 失败终态的观测记录：span 标错误 + 计数与耗时指标（状态标签 FAILED）。
     */
    private void recordRunFailure(SpanScope span, PlatformAgentDefinition agent, RuntimeException ex, long runStartedAt) {
        span.recordError(ex);
        span.attribute(ObservabilityAttributes.EXECUTION_STATUS, ExecutionStatus.FAILED.name());
        span.attribute(ObservabilityAttributes.DURATION_MS, String.valueOf(elapsedMs(runStartedAt)));
        observability.recordCounter(MonitoringMetricNames.AGENT_RUN_COUNT, 1.0,
                ObservabilityAttributes.AGENT_KEY, agent.agentKey(),
                ObservabilityAttributes.EXECUTION_STATUS, ExecutionStatus.FAILED.name());
        observability.recordDuration(MonitoringMetricNames.AGENT_RUN_DURATION, elapsedMs(runStartedAt),
                ObservabilityAttributes.AGENT_KEY, agent.agentKey(),
                ObservabilityAttributes.EXECUTION_STATUS, ExecutionStatus.FAILED.name());
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    /**
     * 列出当前用户可见的执行记录。admin 可见全部，其余仅本人。
     *
     * @param userId 调用者用户 ID
     * @return 按开始时间倒序的执行记录，无数据返回空列表
     */
    public List<ExecutionRecord> listExecutions(long userId) {
        if (permissionChecker.isAdmin(userId)) {
            return executionRecordPort.listAll();
        }
        return executionRecordPort.listByUserId(userId);
    }

    /**
     * 查询执行记录（校验归属），越权时写 {@code EXECUTION_READ/DENIED} 审计。
     *
     * @param userId      调用者用户 ID
     * @param executionId 执行记录 ID
     * @return 执行记录
     * @throws PlatformNotFoundException     记录不存在
     * @throws PlatformAccessDeniedException 越权读取他人记录
     */
    public ExecutionRecord getExecution(long userId, String executionId) {
        ExecutionRecord record = executionRecordPort.findById(requireKey(executionId))
                .orElseThrow(() -> new PlatformNotFoundException("execution not found: " + executionId));
        try {
            permissionChecker.requireExecutionAccess(userId, record.userId());
        } catch (PlatformAccessDeniedException ex) {
            auditDeniedRead(executionId, userId, ex.getMessage());
            throw ex;
        }
        return record;
    }

    /** 写 {@code AGENT_RUN} 审计，失败只告警不影响主流程。traceId 取自当前请求 MDC。 */
    private void audit(String executionId, long userId, String agentKey, AuditResult result, String detail) {
        try {
            auditLogPort.record(AuditLogEntry.of(
                    userId,
                    AuditAction.AGENT_RUN,
                    "agent:" + agentKey,
                    result,
                    TraceIds.current(),
                    auditDetail(executionId, detail)));
        } catch (RuntimeException ex) {
            log.warn("[platform] audit record failed action=AGENT_RUN userId={} error={}",
                    userId, ex.getMessage());
        }
    }

    /** 写 {@code EXECUTION_READ/DENIED} 审计，失败只告警不影响主流程。traceId 取自当前请求 MDC。 */
    private void auditDeniedRead(String executionId, long userId, String detail) {
        try {
            auditLogPort.record(AuditLogEntry.of(
                    userId,
                    AuditAction.EXECUTION_READ,
                    "execution:" + executionId,
                    AuditResult.DENIED,
                    TraceIds.current(),
                    auditDetail(executionId, detail)));
        } catch (RuntimeException ex) {
            log.warn("[platform] audit record failed action=EXECUTION_READ userId={} error={}",
                    userId, ex.getMessage());
        }
    }

    /** 审计 detail 只带标识与错误摘要，不带入参与输出正文（可能含患者隐私）。 */
    private String auditDetail(String executionId, String detail) {
        String suffix = (detail == null || detail.isBlank()) ? "" : " error=" + detail;
        return "executionId=" + executionId + suffix;
    }

    private String requireKey(String value) {
        String key = value == null ? "" : value.trim();
        if (key.isEmpty()) {
            throw new InvalidChatRequestException("id must not be blank");
        }
        return key;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize json", ex);
        }
    }
}
