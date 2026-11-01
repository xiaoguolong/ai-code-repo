package com.aicode.enterprise.domain.model;

/**
 * 入库步骤状态（Week 32）。
 *
 * <p>{@code SKIPPED} 用于"重试时已有产物"（例如已有 OCR 文本就跳过 OCR）。</p>
 */
public enum IngestStepStatus {

    /** 尚未执行 */
    PENDING,
    /** 执行中 */
    RUNNING,
    /** 成功 */
    SUCCEEDED,
    /** 失败（可重试） */
    FAILED,
    /** 跳过（已有产物或用户取消） */
    SKIPPED
}
