package com.aicode.framework.platform.infrastructure.scheduling;

import com.aicode.framework.platform.application.KnowledgeBindingReconcileUseCase;
import com.aicode.framework.platform.application.KnowledgeReconcileResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 知识库绑定定时对账（Week 27）。
 *
 * <p>默认每天 02:15 跑一次；`platform.knowledge.reconcile.enabled=false` 可关。
 * 单实例假设：多实例会重复对账（对账本身幂等，但企业清单调用会翻倍）。</p>
 *
 * <p>失败只记 WARN：对账是运维任务，不能因为企业侧不可用把平台拖垮。</p>
 */
@Component
@ConditionalOnProperty(name = "platform.knowledge.reconcile.enabled", havingValue = "true",
        matchIfMissing = true)
public class KnowledgeBindingReconcileScheduler {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBindingReconcileScheduler.class);

    private final KnowledgeBindingReconcileUseCase reconcileUseCase;

    public KnowledgeBindingReconcileScheduler(KnowledgeBindingReconcileUseCase reconcileUseCase) {
        this.reconcileUseCase = reconcileUseCase;
    }

    /**
     * 周期对账（全部租户）。
     */
    @Scheduled(cron = "${platform.knowledge.reconcile.cron:0 15 2 * * *}")
    public void reconcile() {
        try {
            KnowledgeReconcileResult result = reconcileUseCase.reconcileAll();
            if (result.missing() > 0 || result.failed() > 0) {
                log.warn("[knowledge] reconcile needs attention missing={} failed={} groups={}",
                        result.missing(), result.failed(), result.groups());
            }
        } catch (RuntimeException ex) {
            log.warn("[knowledge] scheduled reconcile failed error={}", ex.getMessage());
        }
    }
}
