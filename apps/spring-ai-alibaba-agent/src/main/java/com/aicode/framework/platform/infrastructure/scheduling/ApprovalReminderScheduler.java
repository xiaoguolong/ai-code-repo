package com.aicode.framework.platform.infrastructure.scheduling;

import com.aicode.framework.platform.application.ApprovalReminderUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 审批待办超时提醒调度（Week 27）。
 *
 * <p>默认每小时第 5 分钟跑一次；`platform.approval.reminder.enabled=false` 可关。
 * 只调提醒用例，<b>不</b>触碰审批决策：超时不会被自动批准/拒绝。</p>
 *
 * <p>失败只记 WARN：提醒是运维任务，不能因为提醒失败影响应用。</p>
 */
@Component
@ConditionalOnProperty(name = "platform.approval.reminder.enabled", havingValue = "true",
        matchIfMissing = true)
public class ApprovalReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ApprovalReminderScheduler.class);

    private final ApprovalReminderUseCase reminderUseCase;

    public ApprovalReminderScheduler(ApprovalReminderUseCase reminderUseCase) {
        this.reminderUseCase = reminderUseCase;
    }

    /**
     * 周期提醒（阈值 + 冷却由用例内部判断）。
     */
    @Scheduled(cron = "${platform.approval.reminder.cron:0 5 * * * *}")
    public void remind() {
        try {
            reminderUseCase.remindDue();
        } catch (RuntimeException ex) {
            log.warn("[platform] approval reminder scheduler failed error={}", ex.getMessage());
        }
    }
}
