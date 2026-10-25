package com.aicode.framework.followup.domain.model;

/**
 * 随访任务状态（Week 24）。
 *
 * <p>只有 {@link #PENDING} 可以迁移到终态；完成/取消都走
 * {@code UPDATE ... WHERE status='PENDING'} 的原子迁移，重复操作返回 409。</p>
 */
public enum FollowUpStatus {

    /** 待执行 */
    PENDING,
    /** 已完成 */
    COMPLETED,
    /** 已取消（转诊/拒访等） */
    CANCELLED
}
