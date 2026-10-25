package com.aicode.framework.followup.domain.model;

/**
 * 随访任务来源（Week 24）。
 *
 * <p>来源决定「重复生成」的判重方式：{@link #APPROVAL} 按 {@code approvalId + title} 唯一，
 * {@link #AGENT} 每次运行都生成一套新任务（同一天重复跑会得到重复任务，由调用方决定是否取消）。</p>
 */
public enum FollowUpSource {

    /** HIGH 风险审批通过后自动生成 */
    APPROVAL,
    /** 医生直接调用 `followup` Agent 生成 */
    AGENT
}
