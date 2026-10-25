package com.aicode.framework.platform.application;

import com.aicode.framework.platform.domain.model.ApprovalStatus;
import com.aicode.framework.platform.domain.model.ExecutionRecord;
import com.aicode.framework.platform.domain.model.ExecutionStatus;
import com.aicode.framework.platform.domain.model.PlatformApproval;
import com.aicode.framework.platform.domain.port.ExecutionRecordPort;
import com.aicode.framework.platform.domain.port.PlatformApprovalPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 审批/执行一致性对账（Week 24，TD-02 的兜底）。
 *
 * <p>解决的问题：第 23 周「markDecided → 写记忆 → 改执行状态」不是单事务，
 * 极端情况下会留下 <b>审批已 APPROVED/REJECTED、执行仍 PENDING_APPROVAL</b> 的脏数据。
 * 第 24 周主路径已加事务，但历史数据与事务边界外的失败仍需要补偿。</p>
 *
 * <p>对账只做「按审批终态补执行终态」，<b>不重新决策、不写记忆、不生成任务</b>：
 * 人的决定已经做出，补的是执行记录的状态字段。幂等：执行记录不是 PENDING_APPROVAL 就跳过。</p>
 */
@Service
public class ApprovalReconciliationUseCase {

    private static final Logger log = LoggerFactory.getLogger(ApprovalReconciliationUseCase.class);

    private final PlatformApprovalPort approvalPort;
    private final ExecutionRecordPort executionRecordPort;
    private final PlatformExecutionRecordWriter executionRecordWriter;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ApprovalReconciliationUseCase(
            PlatformApprovalPort approvalPort,
            ExecutionRecordPort executionRecordPort,
            PlatformExecutionRecordWriter executionRecordWriter,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.approvalPort = approvalPort;
        this.executionRecordPort = executionRecordPort;
        this.executionRecordWriter = executionRecordWriter;
        this.objectMapper = objectMapper;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /**
     * 扫描并修复不一致的审批/执行对。
     *
     * @param limit 每种审批状态最多处理条数
     * @return 修复条数
     */
    public int reconcile(int limit) {
        int fixed = 0;
        fixed += reconcileStatus(ApprovalStatus.APPROVED, ExecutionStatus.COMPLETED, limit);
        fixed += reconcileStatus(ApprovalStatus.REJECTED, ExecutionStatus.REJECTED, limit);
        if (fixed > 0) {
            log.warn("[platform] approval reconciliation fixed={} (审批终态与执行记录不一致，已按审批终态补齐执行状态)", fixed);
        }
        return fixed;
    }

    private int reconcileStatus(ApprovalStatus approvalStatus, ExecutionStatus target, int limit) {
        int fixed = 0;
        List<PlatformApproval> approvals = approvalPort.listAll(approvalStatus, Math.max(limit, 1));
        for (PlatformApproval approval : approvals) {
            ExecutionRecord record = executionRecordPort.findById(approval.executionId()).orElse(null);
            if (record == null || record.status() != ExecutionStatus.PENDING_APPROVAL) {
                continue;
            }
            Map<String, Object> output = PlatformRunOutputJson.parse(objectMapper, record.outputJson());
            output.put("approvalStatus", approvalStatus.name());
            output.put("reconciled", true);
            Instant finishedAt = approval.decidedAt() == null ? clock.instant() : approval.decidedAt();
            executionRecordWriter.saveTerminal(record, target, output, finishedAt);
            log.warn("[platform] approval reconciled approvalId={} executionId={} target={}",
                    approval.approvalId(), approval.executionId(), target);
            fixed++;
        }
        return fixed;
    }
}
