package com.aicode.framework.platform.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.core.domain.model.TokenUsage;
import com.aicode.framework.platform.domain.exception.PlatformAccessDeniedException;
import com.aicode.framework.platform.domain.exception.PlatformAgentDisabledException;
import com.aicode.framework.platform.domain.exception.PlatformNotFoundException;
import com.aicode.framework.platform.domain.model.AuditAction;
import com.aicode.framework.platform.domain.model.AuditLogEntry;
import com.aicode.framework.platform.domain.model.AuditResult;
import com.aicode.framework.platform.domain.model.ExecutionRecord;
import com.aicode.framework.platform.domain.model.ExecutionStatus;
import com.aicode.framework.platform.domain.model.PlatformAgentDefinition;
import com.aicode.framework.platform.domain.model.PlatformRunOutput;
import com.aicode.framework.platform.domain.port.AgentRegistryPort;
import com.aicode.framework.platform.domain.port.AuditLogPort;
import com.aicode.framework.platform.domain.port.ExecutionRecordPort;
import com.aicode.framework.platform.domain.service.PlatformAgentRunner;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import com.aicode.framework.infrastructure.logging.TraceIds;
import com.aicode.framework.platform.infrastructure.security.PlatformSecurityContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 平台执行记录与 Agent 调度用例（含 RBAC 校验与用户隔离）。
 *
 * <p>Week 16：执行终态与越权读取写审计。鉴权在审计之前，越权请求不产生执行记录也不写
 * {@code AGENT_RUN}（避免噪声），只在读他人记录被拒时写 {@code EXECUTION_READ/DENIED}。</p>
 */
@Service
public class PlatformExecutionUseCase {

    private static final Logger log = LoggerFactory.getLogger(PlatformExecutionUseCase.class);

    private final AgentRegistryPort agentRegistryPort;
    private final ExecutionRecordPort executionRecordPort;
    private final PlatformAgentRunner platformAgentRunner;
    private final PlatformPermissionChecker permissionChecker;
    private final PlatformGuardrailService platformGuardrailService;
    private final ObjectMapper objectMapper;
    private final AuditLogPort auditLogPort;

    public PlatformExecutionUseCase(
            AgentRegistryPort agentRegistryPort,
            ExecutionRecordPort executionRecordPort,
            PlatformAgentRunner platformAgentRunner,
            PlatformPermissionChecker permissionChecker,
            PlatformGuardrailService platformGuardrailService,
            ObjectMapper objectMapper,
            AuditLogPort auditLogPort
    ) {
        this.agentRegistryPort = agentRegistryPort;
        this.executionRecordPort = executionRecordPort;
        this.platformAgentRunner = platformAgentRunner;
        this.permissionChecker = permissionChecker;
        this.platformGuardrailService = platformGuardrailService;
        this.objectMapper = objectMapper;
        this.auditLogPort = auditLogPort;
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
        Instant startedAt = Instant.now();
        ExecutionRecord running = new ExecutionRecord(
                executionId, userId, agent.agentKey(), agent.agentType(), ExecutionStatus.RUNNING,
                toJson(input), "", "", TokenUsage.unknown(), "", startedAt, null);
        executionRecordPort.save(running);
        log.info("[platform] execution started executionId={} userId={} agentKey={} agentType={} input={}",
                executionId, userId, agent.agentKey(), agent.agentType(), running.inputJson());

        try {
            PlatformSecurityContext.beginPlatformRun(userId);
            PlatformRunOutput output = platformAgentRunner.run(agent, input);
            Map<String, Object> sanitizedOutput = platformGuardrailService.sanitizeRunOutput(
                    userId, agent.agentKey(), output.output());
            ExecutionRecord completed = new ExecutionRecord(
                    executionId, userId, agent.agentKey(), agent.agentType(), ExecutionStatus.COMPLETED,
                    running.inputJson(), toJson(sanitizedOutput), output.model(), output.usage(),
                    "", startedAt, Instant.now());
            executionRecordPort.save(completed);
            log.info("[platform] execution completed executionId={} userId={} agentKey={} model={} tokens={}",
                    executionId, userId, agent.agentKey(), output.model(), output.usage().totalTokens());
            audit(executionId, userId, agent.agentKey(), AuditResult.SUCCESS, null);
            return completed;

        } catch (RuntimeException ex) {
            ExecutionRecord failed = new ExecutionRecord(
                    executionId, userId, agent.agentKey(), agent.agentType(), ExecutionStatus.FAILED,
                    running.inputJson(), "", "", TokenUsage.unknown(), ex.getMessage(),
                    startedAt, Instant.now());
            executionRecordPort.save(failed);
            log.warn("[platform] execution failed executionId={} userId={} agentKey={} error={}",
                    executionId, userId, agent.agentKey(), ex.getMessage());
            audit(executionId, userId, agent.agentKey(), AuditResult.FAILURE, ex.getMessage());
            throw ex;
        } finally {
            PlatformSecurityContext.clear();
        }
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
