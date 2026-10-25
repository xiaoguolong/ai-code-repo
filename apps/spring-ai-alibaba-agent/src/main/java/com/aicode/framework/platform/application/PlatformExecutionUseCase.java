package com.aicode.framework.platform.application;

import com.aicode.framework.observability.domain.AgentObservabilityPort;
import com.aicode.framework.platform.domain.exception.PlatformAccessDeniedException;
import com.aicode.framework.platform.domain.exception.PlatformAgentDisabledException;
import com.aicode.framework.platform.domain.exception.PlatformNotFoundException;
import com.aicode.framework.platform.domain.model.ExecutionRecord;
import com.aicode.framework.platform.domain.port.AgentRegistryPort;
import com.aicode.framework.platform.domain.port.AuditLogPort;
import com.aicode.framework.platform.domain.port.ExecutionRecordPort;
import com.aicode.framework.platform.domain.service.PlatformAgentRunner;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 平台执行记录与 Agent 调度用例（公共门面）。
 *
 * <p>Week 28 起内部拆为 {@link PlatformExecutionRunner}（调度编排）、
 * {@link PlatformExecutionRecorder}（记录工厂）、{@link PlatformExecutionObservability}（埋点）、
 * {@link PlatformExecutionPendingApproval}（待审落库）、{@link PlatformExecutionQueryUseCase}（查询）；
 * 本类只保留稳定构造器与公共 API，控制器/既有测试无需改动。</p>
 */
@Service
public class PlatformExecutionUseCase {

    private final PlatformExecutionRunner runner;
    private final PlatformExecutionQueryUseCase queryUseCase;

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
        PlatformExecutionRecorder recorder = new PlatformExecutionRecorder(objectMapper);
        PlatformExecutionObservability executionObservability =
                new PlatformExecutionObservability(observability);
        PlatformExecutionPendingApproval pendingApproval = approvalUseCase == null ? null
                : new PlatformExecutionPendingApproval(
                        approvalUseCase, recorder, executionObservability, executionRecordPort);
        this.runner = new PlatformExecutionRunner(
                agentRegistryPort, executionRecordPort, platformAgentRunner, permissionChecker,
                platformGuardrailService, auditLogPort, observability, recorder,
                executionObservability, pendingApproval);
        this.queryUseCase = new PlatformExecutionQueryUseCase(
                executionRecordPort, permissionChecker, auditLogPort);
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
        return runner.runAgent(userId, agentKey, input);
    }

    /**
     * 列出当前用户可见的执行记录（admin 全部，其余仅本人）。
     *
     * @param userId 调用者
     * @return 按开始时间倒序的记录
     */
    public List<ExecutionRecord> listExecutions(long userId) {
        return queryUseCase.listExecutions(userId);
    }

    /**
     * 查询执行记录（校验归属），越权时写 {@code EXECUTION_READ/DENIED} 审计。
     *
     * @param userId      调用者
     * @param executionId 执行记录 ID
     * @return 执行记录
     * @throws PlatformNotFoundException     记录不存在
     * @throws PlatformAccessDeniedException 越权读取他人记录
     */
    public ExecutionRecord getExecution(long userId, String executionId) {
        return queryUseCase.getExecution(userId, executionId);
    }
}
