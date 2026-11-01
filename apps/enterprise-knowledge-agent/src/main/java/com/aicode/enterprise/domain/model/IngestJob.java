package com.aicode.enterprise.domain.model;

import java.time.Instant;

/**
 * 一次 PDF/OCR 入库作业（Week 32）。
 *
 * <p>{@code ocrMarkdown} 保存 OCR 原文（支持"只重切片"不重跑 OCR），
 * {@code cleanMarkdown} 保存 LLM 清洗结果；{@code contentHash} 与 kbId 唯一，用于重复上传幂等。</p>
 *
 * @param jobId         作业 ID
 * @param kbId          知识库 ID
 * @param userId        发起用户
 * @param fileName      原始文件名
 * @param fileId        文件服务 ID（可能为空：直接上传 OCR 时）
 * @param contentHash   sha256(文件内容)
 * @param status        状态
 * @param pageCount     OCR 页数
 * @param chunkCount    最终切片数
 * @param ocrMarkdown   OCR 合并后的 markdown
 * @param cleanMarkdown LLM 清洗后的 markdown
 * @param documentId    成功后的文档 ID
 * @param errorMessage  失败原因
 * @param createdAt     创建时间
 * @param startedAt     开始执行时间
 * @param finishedAt    终态时间
 */
public record IngestJob(
        String jobId,
        long kbId,
        long userId,
        String fileName,
        String fileId,
        String contentHash,
        IngestJobStatus status,
        int pageCount,
        int chunkCount,
        String ocrMarkdown,
        String cleanMarkdown,
        String documentId,
        String errorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt
) {

    public IngestJob {
        fileId = fileId == null ? "" : fileId;
        ocrMarkdown = ocrMarkdown == null ? "" : ocrMarkdown;
        cleanMarkdown = cleanMarkdown == null ? "" : cleanMarkdown;
        documentId = documentId == null ? "" : documentId;
        errorMessage = errorMessage == null ? "" : errorMessage;
    }

    /**
     * 新建 PENDING 作业。
     *
     * @param jobId       作业 ID
     * @param kbId        知识库
     * @param userId      用户
     * @param fileName    文件名
     * @param contentHash 内容 hash
     * @param createdAt   创建时间
     * @return PENDING 作业
     */
    public static IngestJob pending(
            String jobId, long kbId, long userId, String fileName, String contentHash, Instant createdAt) {
        return new IngestJob(jobId, kbId, userId, fileName, "", contentHash, IngestJobStatus.PENDING,
                0, 0, "", "", "", "", createdAt, null, null);
    }

    /**
     * @param startedAt 开始时间
     * @return RUNNING 作业
     */
    public IngestJob running(Instant startedAt) {
        return new IngestJob(jobId, kbId, userId, fileName, fileId, contentHash, IngestJobStatus.RUNNING,
                pageCount, chunkCount, ocrMarkdown, cleanMarkdown, documentId, "", createdAt, startedAt, null);
    }

    /**
     * @param pages    OCR 页数
     * @param markdown OCR 合并文本
     * @return 带上 OCR 产物的作业
     */
    public IngestJob withOcr(int pages, String markdown) {
        return new IngestJob(jobId, kbId, userId, fileName, fileId, contentHash, status,
                pages, chunkCount, markdown, cleanMarkdown, documentId, errorMessage,
                createdAt, startedAt, finishedAt);
    }

    /**
     * @param markdown 清洗后的文本
     * @return 带上清洗产物的作业
     */
    public IngestJob withClean(String markdown) {
        return new IngestJob(jobId, kbId, userId, fileName, fileId, contentHash, status,
                pageCount, chunkCount, ocrMarkdown, markdown, documentId, errorMessage,
                createdAt, startedAt, finishedAt);
    }

    /**
     * @param fileId 文件服务 ID
     * @return 绑定文件后的作业
     */
    public IngestJob withFile(String fileId) {
        return new IngestJob(jobId, kbId, userId, fileName, fileId, contentHash, status,
                pageCount, chunkCount, ocrMarkdown, cleanMarkdown, documentId, errorMessage,
                createdAt, startedAt, finishedAt);
    }

    /**
     * @param newDocumentId 文档 ID
     * @param chunks        切片数
     * @return 带上文档产物的作业（仍 RUNNING）
     */
    public IngestJob withDocument(String newDocumentId, int chunks) {
        return new IngestJob(jobId, kbId, userId, fileName, fileId, contentHash, status,
                pageCount, chunks, ocrMarkdown, cleanMarkdown, newDocumentId, errorMessage,
                createdAt, startedAt, finishedAt);
    }

    /**
     * @param newDocumentId 文档 ID
     * @param chunks        切片数
     * @param finishedAt    完成时间
     * @return SUCCEEDED 作业
     */
    public IngestJob succeeded(String newDocumentId, int chunks, Instant finishedAt) {
        return new IngestJob(jobId, kbId, userId, fileName, fileId, contentHash, IngestJobStatus.SUCCEEDED,
                pageCount, chunks, ocrMarkdown, cleanMarkdown, newDocumentId, "", createdAt, startedAt, finishedAt);
    }

    /**
     * @param error      错误摘要
     * @param finishedAt 失败时间
     * @return FAILED 作业
     */
    public IngestJob failed(String error, Instant finishedAt) {
        return new IngestJob(jobId, kbId, userId, fileName, fileId, contentHash, IngestJobStatus.FAILED,
                pageCount, chunkCount, ocrMarkdown, cleanMarkdown, documentId, error,
                createdAt, startedAt, finishedAt);
    }

    /**
     * @param finishedAt 取消时间
     * @return CANCELLED 作业
     */
    public IngestJob cancelled(Instant finishedAt) {
        return new IngestJob(jobId, kbId, userId, fileName, fileId, contentHash, IngestJobStatus.CANCELLED,
                pageCount, chunkCount, ocrMarkdown, cleanMarkdown, documentId, "cancelled by user",
                createdAt, startedAt, finishedAt);
    }
}
