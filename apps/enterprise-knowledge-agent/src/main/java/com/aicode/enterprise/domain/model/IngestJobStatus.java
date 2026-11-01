package com.aicode.enterprise.domain.model;

/**
 * 入库作业状态（Week 32）。
 *
 * <p>状态机：{@code PENDING → RUNNING → SUCCEEDED|FAILED|CANCELLED}；
 * 只有 FAILED 可重试回到 RUNNING；进程重启时把遗留 RUNNING 置 FAILED（可重试）。</p>
 */
public enum IngestJobStatus {

    /** 已建作业，等待执行 */
    PENDING,
    /** 正在按步骤执行 */
    RUNNING,
    /** 全部步骤成功，文档可检索 */
    SUCCEEDED,
    /** 某步失败（可重试） */
    FAILED,
    /** 用户取消（当前步结束后停止） */
    CANCELLED;

    /**
     * @return 是否终态（不可再执行，只能重试/删除）
     */
    public boolean terminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }
}
