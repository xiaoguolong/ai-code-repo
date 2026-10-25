package com.aicode.framework.platform.application;

import com.aicode.framework.observability.domain.SpanScope;
import com.aicode.framework.platform.domain.model.ApprovalStatus;
import com.aicode.framework.platform.domain.model.ExecutionRecord;
import com.aicode.framework.platform.domain.model.PlatformAgentDefinition;
import com.aicode.framework.platform.domain.model.PlatformApproval;
import com.aicode.framework.platform.domain.model.PlatformRunOutput;
import com.aicode.framework.platform.domain.port.ExecutionRecordPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HIGH 风险待审落库（Week 28，包内协作类）。
 *
 * <p>创建审批任务 → 落 PENDING_APPROVAL 执行记录 → 记录待审指标；不写 {@code AGENT_RUN}
 * 审计（执行尚未终态），审批任务创建本身写 {@code APPROVAL_SUBMIT}。</p>
 */
final class PlatformExecutionPendingApproval {

    private static final Logger log = LoggerFactory.getLogger(PlatformExecutionPendingApproval.class);

    private final PlatformApprovalUseCase approvalUseCase;
    private final PlatformExecutionRecorder recorder;
    private final PlatformExecutionObservability observability;
    private final ExecutionRecordPort executionRecordPort;

    PlatformExecutionPendingApproval(
            PlatformApprovalUseCase approvalUseCase,
            PlatformExecutionRecorder recorder,
            PlatformExecutionObservability observability,
            ExecutionRecordPort executionRecordPort
    ) {
        this.approvalUseCase = approvalUseCase;
        this.recorder = recorder;
        this.observability = observability;
        this.executionRecordPort = executionRecordPort;
    }

    /**
     * 创建审批任务并落待审执行记录。
     *
     * @param command 待审上下文
     * @return 待审执行记录
     */
    ExecutionRecord create(Command command) {
        String patientId = text(command.input() == null ? null : command.input().get("patientId"));
        String riskLevel = text(command.sanitizedOutput().get("riskLevel"));
        String task = text(command.sanitizedOutput().get("task"));
        PlatformApproval approval = approvalUseCase.create(
                command.executionId(), command.userId(), command.agent().agentKey(), patientId, riskLevel, task);

        Map<String, Object> pendingOutput = new LinkedHashMap<>(command.sanitizedOutput());
        pendingOutput.put("approvalId", approval.approvalId());
        pendingOutput.put("approvalStatus", ApprovalStatus.PENDING.name());
        ExecutionRecord pending = recorder.pending(
                command.running(), recorder.toJson(pendingOutput),
                command.output().model(), command.output().usage());
        executionRecordPort.save(pending);
        log.info("[platform] execution pending approval executionId={} userId={} agentKey={} approvalId={} riskLevel={}",
                command.executionId(), command.userId(), command.agent().agentKey(),
                approval.approvalId(), riskLevel);
        observability.recordPending(command.span(), command.agent(), command.output(), command.runStartedAt());
        return pending;
    }

    private String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    /**
     * 待审上下文（一次方法传参，避免长参数列表）。
     *
     * @param executionId   执行 ID
     * @param userId        用户 ID
     * @param agent         Agent 定义
     * @param input         Run 入参
     * @param running       运行中记录
     * @param output        运行输出
     * @param sanitizedOutput 脱敏后的输出
     * @param span          当前 span
     * @param runStartedAt  开始纳秒
     */
    record Command(
            String executionId,
            long userId,
            PlatformAgentDefinition agent,
            Map<String, Object> input,
            ExecutionRecord running,
            PlatformRunOutput output,
            Map<String, Object> sanitizedOutput,
            SpanScope span,
            long runStartedAt
    ) {
    }
}
