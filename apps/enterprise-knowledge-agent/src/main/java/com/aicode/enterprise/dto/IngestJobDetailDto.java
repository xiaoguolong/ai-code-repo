package com.aicode.enterprise.dto;

import com.aicode.enterprise.application.IngestJobDetail;

import java.time.Instant;
import java.util.List;

/**
 * 入库作业详情（Job + 节点）。
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
 * @param steps       节点列表（含 prompt 原文）
 */
public record IngestJobDetailDto(
        String jobId,
        String fileName,
        String status,
        int pageCount,
        int chunkCount,
        String documentId,
        String errorMessage,
        Instant createdAt,
        Instant finishedAt,
        List<IngestStepDto> steps
) {

    /**
     * @param detail 用例结果
     * @return DTO
     */
    public static IngestJobDetailDto from(IngestJobDetail detail) {
        var job = detail.job();
        return new IngestJobDetailDto(job.jobId(), job.fileName(), job.status().name(), job.pageCount(),
                job.chunkCount(), job.documentId(), job.errorMessage(), job.createdAt(), job.finishedAt(),
                detail.steps().stream().map(IngestStepDto::from).toList());
    }
}
