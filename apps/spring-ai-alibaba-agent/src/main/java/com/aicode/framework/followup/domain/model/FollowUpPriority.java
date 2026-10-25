package com.aicode.framework.followup.domain.model;

/**
 * 随访任务优先级（Week 24）。
 *
 * <p>由风险等级映射（HIGH → HIGH、MEDIUM → MEDIUM、LOW → LOW），模型不参与决定。</p>
 */
public enum FollowUpPriority {

    /** 高风险：需要尽快执行 */
    HIGH,
    /** 中风险 */
    MEDIUM,
    /** 低风险 */
    LOW
}
