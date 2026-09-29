package com.aicode.framework.workflow.domain.model;

/**
 * 患者风险等级。
 */
public enum RiskLevel {

    /** 低风险 */
    LOW("低风险"),
    /** 中风险 */
    MEDIUM("中风险"),
    /** 高风险 */
    HIGH("高风险");

    private final String label;

    RiskLevel(String label) {
        this.label = label;
    }

    /**
     * @return 中文标签
     */
    public String label() {
        return label;
    }
}
