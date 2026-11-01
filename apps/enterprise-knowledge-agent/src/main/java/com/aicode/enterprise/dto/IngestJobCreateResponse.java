package com.aicode.enterprise.dto;

import com.aicode.enterprise.application.CreateIngestJobOutcome;
import com.aicode.enterprise.domain.model.IngestJob;

/**
 * 建入库作业响应。
 *
 * @param jobId     作业 ID
 * @param status    状态
 * @param fileName  文件名
 * @param duplicate 是否命中等价文件（幂等）
 */
public record IngestJobCreateResponse(String jobId, String status, String fileName, boolean duplicate) {

    /**
     * @param outcome 用例结果
     * @return 响应
     */
    public static IngestJobCreateResponse from(CreateIngestJobOutcome outcome) {
        IngestJob job = outcome.job();
        return new IngestJobCreateResponse(job.jobId(), job.status().name(), job.fileName(), outcome.duplicate());
    }
}
