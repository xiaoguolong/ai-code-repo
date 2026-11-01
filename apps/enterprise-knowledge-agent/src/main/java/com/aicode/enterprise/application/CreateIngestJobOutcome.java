package com.aicode.enterprise.application;

import com.aicode.enterprise.domain.model.IngestJob;

/**
 * 建作业结果。
 *
 * @param job       作业（新建或命中的已有作业）
 * @param duplicate 是否命中幂等（同一知识库同一文件已上传过）
 */
public record CreateIngestJobOutcome(IngestJob job, boolean duplicate) {
}
