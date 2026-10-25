package com.aicode.framework.platform.infrastructure.scheduling;

import com.aicode.framework.platform.application.PlatformMemoryMaintenanceUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 长期记忆过期清理定时任务（Week 23）。
 *
 * <p>默认每天 03:30 执行；{@code platform.memory.lifecycle.cleanup-enabled=false} 时不装配。
 * 单测关闭该开关，避免后台线程影响时序断言。任务本身只删过期行，不删 {@code expires_at=null}。</p>
 */
@Component
@ConditionalOnProperty(
        name = "platform.memory.lifecycle.cleanup-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class MemoryCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(MemoryCleanupScheduler.class);

    private final PlatformMemoryMaintenanceUseCase maintenanceUseCase;

    public MemoryCleanupScheduler(PlatformMemoryMaintenanceUseCase maintenanceUseCase) {
        this.maintenanceUseCase = maintenanceUseCase;
    }

    /**
     * 清理过期记忆；异常只记日志，不让调度线程停摆。
     */
    @Scheduled(cron = "${platform.memory.lifecycle.cleanup-cron:0 30 3 * * *}")
    public void purgeExpiredMemories() {
        try {
            int deleted = maintenanceUseCase.purgeExpiredScheduled();
            log.info("[platform] memory cleanup finished deleted={}", deleted);
        } catch (RuntimeException ex) {
            log.warn("[platform] memory cleanup failed error={}", ex.getMessage());
        }
    }
}
