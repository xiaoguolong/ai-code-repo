package com.aicode.framework.platform.application;

import com.aicode.framework.followup.application.FollowUpTaskUseCase;
import com.aicode.framework.platform.domain.model.PlatformApproval;
import com.aicode.framework.platform.domain.model.PlatformMemoryRecord;
import com.aicode.framework.platform.domain.model.PlatformMemoryScope;
import com.aicode.framework.platform.domain.model.PlatformUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * 审批通过后的副作用（Week 23 写记忆 + Week 24 生成随访任务）。
 *
 * <p>从 {@link PlatformApprovalDecisionUseCase} 抽出来的理由：决策用例只负责「权限 + 状态机 +
 * 事务边界」，副作用（记忆、随访任务）各自失败只降级、不回滚审批，需要独立的 try/catch 与日志。</p>
 *
 * <p><b>失败策略</b>：记忆与随访任务都不是审批的单点——写失败只 WARN 并返回 null/空列表，
 * 审批决定已经生效；随访任务可由 {@code POST /agents/followup/runs} 重跑补齐。</p>
 */
@Component
public class PlatformApprovalSideEffects {

    private static final Logger log = LoggerFactory.getLogger(PlatformApprovalSideEffects.class);

    private final PlatformMemoryUseCase memoryUseCase;
    private final FollowUpTaskUseCase followUpTaskUseCase;

    public PlatformApprovalSideEffects(
            PlatformMemoryUseCase memoryUseCase,
            FollowUpTaskUseCase followUpTaskUseCase
    ) {
        this.memoryUseCase = memoryUseCase;
        this.followUpTaskUseCase = followUpTaskUseCase;
    }

    /**
     * 执行审批通过后的全部副作用。
     *
     * @param approval  已批准的审批任务
     * @param owner     审批发起人（写记忆/任务都要按他归属）
     * @param report    报告正文（来自执行记录 output）
     * @param decidedAt 决策时间（随访计划的基准时间）
     * @return 记忆 ID + 随访任务 ID
     */
    public Result apply(PlatformApproval approval, PlatformUser owner, String report, Instant decidedAt) {
        String memoryId = writeMemory(owner, approval, report);
        List<String> taskIds = createFollowUpTasks(approval, decidedAt);
        return new Result(memoryId, taskIds);
    }

    private String writeMemory(PlatformUser owner, PlatformApproval approval, String report) {
        if (memoryUseCase == null) {
            return null;
        }
        try {
            PlatformMemoryRecord saved = memoryUseCase.save(
                    PlatformMemoryScope.of(owner), approval.patientId(), approval.task(), report);
            return saved.memoryId();
        } catch (RuntimeException ex) {
            // 记忆不是主链路单点：审批决定已经生效，记忆写失败只告警（与第 21 周口径一致）。
            log.warn("[platform] approval memory save failed approvalId={} patientId={} error={}",
                    approval.approvalId(), approval.patientId(), ex.getMessage());
            return null;
        }
    }

    private List<String> createFollowUpTasks(PlatformApproval approval, Instant decidedAt) {
        if (followUpTaskUseCase == null) {
            return List.of();
        }
        try {
            return followUpTaskUseCase.createFromApproval(approval, decidedAt);
        } catch (RuntimeException ex) {
            // 随访任务可重跑（POST /agents/followup/runs），不能因为任务失败让审批卡住。
            log.warn("[platform] approval follow-up task create failed approvalId={} error={}",
                    approval.approvalId(), ex.getMessage());
            return List.of();
        }
    }

    /**
     * 副作用结果。
     *
     * @param memoryId        写入的记忆 ID；失败或未配置为 null
     * @param followUpTaskIds 生成的随访任务 ID；失败或无任务为空列表
     */
    public record Result(String memoryId, List<String> followUpTaskIds) {

        public Result {
            followUpTaskIds = followUpTaskIds == null ? List.of() : List.copyOf(followUpTaskIds);
        }
    }
}
