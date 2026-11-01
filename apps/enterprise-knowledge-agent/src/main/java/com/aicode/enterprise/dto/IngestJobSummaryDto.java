package com.aicode.enterprise.dto;

import com.aicode.enterprise.domain.model.IngestJob;

import java.time.Instant;

/**
 * 入库作业列表项。
 *
 * @param jobId       作业 ID
 * @param fileName    文件名
 * @param status      状态
 * @param pageCount   页数
 * @param chunkCount  切片数
 * @param documentId  文档 ID
 * @param errorMessage 失败原因
 * @param createdAt   创建时间
 * @param finishedAt  完成时间
 */
public record IngestJobSummaryDto(
        String jobId,
        String fileName,
        String status,
        int pageCount,
        int chunkCount,
        String documentId,
        String errorMessage,
        Instant createdAt,
        Instant finishedAt
) {

    /**
     * @param job 作业
     * @return DTO
     */
    public static IngestJobSummaryDto from(IngestJob job) {
        return new IngestJobSummaryDto(job.jobId(), job.fileName(), job.status().name(), job.pageCount(),
                job.chunkCount(), job.documentId(), job.errorMessage(), job.createdAt(), job.finishedAt());
    }
}
