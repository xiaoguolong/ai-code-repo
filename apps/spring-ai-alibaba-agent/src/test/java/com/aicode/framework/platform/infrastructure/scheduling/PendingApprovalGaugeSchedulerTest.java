package com.aicode.framework.platform.infrastructure.scheduling;

import com.aicode.framework.observability.support.RecordingObservabilityPort;
import com.aicode.framework.platform.domain.model.ApprovalStatus;
import com.aicode.framework.platform.domain.model.PlatformApproval;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformApprovalAdapter;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 待办积压采样（Week 24，TD-03）：Gauge 值 = 当前 PENDING 审批数；采样失败不影响应用。
 */
class PendingApprovalGaugeSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-10-25T09:00:00Z");

    @Test
    void samplesPendingApprovalCountIntoGauge() {
        InMemoryPlatformApprovalAdapter approvals = new InMemoryPlatformApprovalAdapter();
        approvals.save(approval("a1", ApprovalStatus.PENDING));
        approvals.save(approval("a2", ApprovalStatus.PENDING));
        approvals.save(approval("a3", ApprovalStatus.APPROVED));
        RecordingObservabilityPort observability = new RecordingObservabilityPort();

        new PendingApprovalGaugeScheduler(approvals, observability).sample();

        assertThat(observability.calls).contains("recordGauge:approval.pending.count=2.0");
    }

    @Test
    void samplingFailureIsSwallowed() {
        RecordingObservabilityPort observability = new RecordingObservabilityPort();
        PendingApprovalGaugeScheduler scheduler = new PendingApprovalGaugeScheduler(
                new FailingApprovalPort(), observability);

        assertThatCode(scheduler::sample).doesNotThrowAnyException();
        assertThat(observability.sawCall("recordGauge")).isFalse();
    }

    private PlatformApproval approval(String id, ApprovalStatus status) {
        return new PlatformApproval(
                id, "e-" + id, "default", 2L, "patient-analysis", "P002", "HIGH", "任务",
                status, "", status == ApprovalStatus.PENDING ? null : 1L,
                NOW, status == ApprovalStatus.PENDING ? null : NOW);
    }

    /**
     * countAll 必失败的端口：验证采样异常被吞掉。
     */
    private static final class FailingApprovalPort
            extends com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformApprovalAdapter {

        @Override
        public long countAll(ApprovalStatus status) {
            throw new IllegalStateException("db down");
        }
    }
}
