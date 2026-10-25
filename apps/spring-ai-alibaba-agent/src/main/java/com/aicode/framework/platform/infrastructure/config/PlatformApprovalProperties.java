package com.aicode.framework.platform.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 审批运维配置（Week 24，{@code platform.approval.*}）。
 *
 * <p>三个能力都可以单独关闭：待办积压 Gauge 采样、审批/执行一致性对账、
 * 待办超时提醒（Week 27）。</p>
 */
@ConfigurationProperties(prefix = "platform.approval")
public class PlatformApprovalProperties {

    /** 待办积压采样配置。 */
    private PendingGauge pendingGauge = new PendingGauge();
    /** 审批/执行一致性对账配置。 */
    private Reconciliation reconciliation = new Reconciliation();
    /** 待办超时提醒配置（Week 27）。 */
    private Reminder reminder = new Reminder();

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

    public Reminder getReminder() {
        return reminder;
    }

    public void setReminder(Reminder reminder) {
        this.reminder = reminder == null ? new Reminder() : reminder;
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

    /**
     * 待办超时提醒（Week 27）：只提醒，不改变审批状态、不自动拒绝。
     *
     * <p>阈值/冷却/单次上限都做上下限夹取，配置写错时回退到合理区间，
     * 避免「阈值写成 0 → 每分钟提醒全量待办」这类事故。</p>
     */
    public static class Reminder {

        /** 是否开启定时提醒。 */
        private boolean enabled = true;
        /** 提醒 cron（默认每小时第 5 分钟）。 */
        private String cron = "0 5 * * * *";
        /** 超过多少小时未处理算超时（默认 24）。 */
        private int thresholdHours = 24;
        /** 同一审批的提醒冷却小时数（默认 12）。 */
        private int cooldownHours = 12;
        /** 单次最多提醒条数（默认 200）。 */
        private int limit = 200;

        /** @return 生效阈值（1..720 小时） */
        public int resolvedThresholdHours() {
            return Math.min(Math.max(thresholdHours, 1), 720);
        }

        /** @return 生效冷却（1..168 小时） */
        public int resolvedCooldownHours() {
            return Math.min(Math.max(cooldownHours, 1), 168);
        }

        /** @return 生效单次上限（1..1000） */
        public int resolvedLimit() {
            return Math.min(Math.max(limit, 1), 1000);
        }

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

        public int getThresholdHours() {
            return thresholdHours;
        }

        public void setThresholdHours(int thresholdHours) {
            this.thresholdHours = thresholdHours;
        }

        public int getCooldownHours() {
            return cooldownHours;
        }

        public void setCooldownHours(int cooldownHours) {
            this.cooldownHours = cooldownHours;
        }

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }
    }
}
