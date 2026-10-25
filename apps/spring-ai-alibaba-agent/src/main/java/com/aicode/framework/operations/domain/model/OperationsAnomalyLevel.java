package com.aicode.framework.operations.domain.model;

/**
 * 运营异常级别（Week 24）。
 *
 * <p>级别只影响日报里的排序与措辞，不触发告警——告警由 Prometheus 规则负责
 * （见 {@code deploy/monitoring/prometheus/alerts.yml}）。</p>
 */
public enum OperationsAnomalyLevel {

    /** 需要当天处理：失败率高、审批积压。 */
    HIGH(0),
    /** 需要关注趋势：失败率偏高、token 突增、单 Agent 失败集中。 */
    MEDIUM(1),
    /** 只做记录：窗口内没有流量。 */
    INFO(2);

    private final int rank;

    OperationsAnomalyLevel(int rank) {
        this.rank = rank;
    }

    /**
     * @return 排序权重，越小越紧急
     */
    public int rank() {
        return rank;
    }

    /**
     * @return 中文展示名
     */
    public String label() {
        return switch (this) {
            case HIGH -> "高";
            case MEDIUM -> "中";
            case INFO -> "提示";
        };
    }
}
