package com.aicode.framework.platform.infrastructure.scheduling;

import com.aicode.framework.observability.domain.AgentObservabilityPort;
import com.aicode.framework.observability.domain.MonitoringMetricNames;
import com.aicode.framework.platform.domain.model.ApprovalStatus;
import com.aicode.framework.platform.domain.port.PlatformApprovalPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 待办积压采样（Week 24，TD-03）。
 *
 * <p>审批没有超时（有意不自动拒绝，避免高风险患者静默失去随访），因此积压只能靠
 * Gauge + Prometheus 告警暴露：`approval_pending_count > 20` 持续 30 分钟即告警。</p>
 *
 * <p>标签选择：<b>不打租户标签</b>。租户数会随着医院接入增长，打在标签上会让时间序列爆炸；
 * 需要分租户看时用审批列表接口（`GET /approvals?status=PENDING`）。</p>
 */
@Component
@ConditionalOnProperty(name = "platform.approval.pending-gauge.enabled", havingValue = "true",
        matchIfMissing = true)
public class PendingApprovalGaugeScheduler {

    private static final Logger log = LoggerFactory.getLogger(PendingApprovalGaugeScheduler.class);

    private final PlatformApprovalPort approvalPort;
    private final AgentObservabilityPort observability;

    public PendingApprovalGaugeScheduler(
            PlatformApprovalPort approvalPort,
            AgentObservabilityPort observability
    ) {
        this.approvalPort = approvalPort;
        this.observability = observability;
    }

    /**
     * 采样待审条数并写入 Gauge。失败只记 DEBUG：观测不是业务单点。
     */
    @Scheduled(
            fixedDelayString = "${platform.approval.pending-gauge.interval-ms:60000}",
            initialDelayString = "${platform.approval.pending-gauge.initial-delay-ms:20000}")
    public void sample() {
        try {
            long pending = approvalPort.countAll(ApprovalStatus.PENDING);
            observability.recordGauge(MonitoringMetricNames.APPROVAL_PENDING_COUNT, pending);
        } catch (RuntimeException ex) {
            log.debug("[platform] pending approval gauge sampling failed: {}", ex.toString());
        }
    }
}
