package com.aicode.framework.platform.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 审批运维配置（Week 24，{@code platform.approval.*}）。
 *
 * <p>两个能力都可以单独关闭：待办积压 Gauge 采样、审批/执行一致性对账。</p>
 */
@ConfigurationProperties(prefix = "platform.approval")
public class PlatformApprovalProperties {

    /** 待办积压采样配置。 */
    private PendingGauge pendingGauge = new PendingGauge();
    /** 审批/执行一致性对账配置。 */
    private Reconciliation reconciliation = new Reconciliation();

    public PendingGauge getPendingGauge() {
        return pendingGauge;
    }

    public void setPendingGauge(PendingGauge pendingGauge) {
        this.pendingGauge = pendingGauge == null ? new PendingGauge() : pendingGauge;
    }

    public Reconciliation getReconciliation() {
        return reconciliation;
    }

    public void setReconciliation(Reconciliation reconciliation) {
        this.reconciliation = reconciliation == null ? new Reconciliation() : reconciliation;
    }

    /**
     * 待办积压 Gauge。
     */
    public static class PendingGauge {

        /** 是否开启周期采样。 */
        private boolean enabled = true;
        /** 采样间隔（毫秒）。 */
        private long intervalMs = 60_000L;
        /** 首次采样延迟（毫秒），给应用启动留出时间。 */
        private long initialDelayMs = 20_000L;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getIntervalMs() {
            return intervalMs;
        }

        public void setIntervalMs(long intervalMs) {
            this.intervalMs = intervalMs;
        }

        public long getInitialDelayMs() {
            return initialDelayMs;
        }

        public void setInitialDelayMs(long initialDelayMs) {
            this.initialDelayMs = initialDelayMs;
        }
    }

    /**
     * 审批/执行一致性对账。
     */
    public static class Reconciliation {

        /** 是否开启对账。 */
        private boolean enabled = true;
        /** 对账 cron（默认每 10 分钟）。 */
        private String cron = "0 */10 * * * *";
        /** 每次每种状态最多处理条数。 */
        private int limit = 200;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }
    }
}
