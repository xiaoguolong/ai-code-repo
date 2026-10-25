package com.aicode.framework.platform.infrastructure.scheduling;

import com.aicode.framework.platform.application.ApprovalReconciliationUseCase;
import com.aicode.framework.platform.infrastructure.config.PlatformApprovalProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 审批/执行一致性对账调度（Week 24，TD-02）。
 *
 * <p>默认每 10 分钟跑一次、每次每种状态最多 200 条；`platform.approval.reconciliation.enabled=false`
 * 可关。单实例假设：多实例会重复执行，但对账本身幂等（执行记录不是 PENDING_APPROVAL 就跳过）。</p>
 */
@Component
@ConditionalOnProperty(name = "platform.approval.reconciliation.enabled", havingValue = "true",
        matchIfMissing = true)
public class ApprovalReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ApprovalReconciliationScheduler.class);

    private final ApprovalReconciliationUseCase reconciliationUseCase;
    private final PlatformApprovalProperties properties;

    public ApprovalReconciliationScheduler(
            ApprovalReconciliationUseCase reconciliationUseCase,
            PlatformApprovalProperties properties
    ) {
        this.reconciliationUseCase = reconciliationUseCase;
        this.properties = properties;
    }

    /**
     * 周期对账。失败只记 WARN：不能因为对账任务把应用拖垮。
     */
    @Scheduled(cron = "${platform.approval.reconciliation.cron:0 */10 * * * *}")
    public void reconcile() {
        try {
            int limit = properties.getReconciliation().getLimit();
            int fixed = reconciliationUseCase.reconcile(limit <= 0 ? 200 : limit);
            if (fixed > 0) {
                log.warn("[platform] approval reconciliation fixed={}", fixed);
            }
        } catch (RuntimeException ex) {
            log.warn("[platform] approval reconciliation failed error={}", ex.getMessage());
        }
    }
}
