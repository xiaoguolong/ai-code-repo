package com.aicode.enterprise.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.aicode.core.domain.exception.FileStorageException;
import com.aicode.enterprise.application.CancelIngestJobUseCase;
import com.aicode.enterprise.application.CreateIngestJobOutcome;
import com.aicode.enterprise.application.CreateIngestJobUseCase;
import com.aicode.enterprise.application.IngestJobQueryUseCase;
import com.aicode.enterprise.application.ReprocessIngestJobUseCase;
import com.aicode.enterprise.application.RetryIngestJobUseCase;
import com.aicode.enterprise.dto.ApiResponse;
import com.aicode.enterprise.dto.IngestJobCreateResponse;
import com.aicode.enterprise.dto.IngestJobDetailDto;
import com.aicode.enterprise.dto.IngestJobSummaryDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * PDF/OCR 入库作业接口（Week 32）。
 *
 * <p>建作业只做"存文件 + 建 Job + 入队"，OCR/清洗/切片/向量化在异步流水线里执行；
 * 详情返回全部节点（含 clean 步骤的 prompt 原文与 token）。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class IngestJobController {

    private final CreateIngestJobUseCase createUseCase;
    private final IngestJobQueryUseCase queryUseCase;
    private final RetryIngestJobUseCase retryUseCase;
    private final CancelIngestJobUseCase cancelUseCase;
    private final ReprocessIngestJobUseCase reprocessUseCase;

    public IngestJobController(
            CreateIngestJobUseCase createUseCase,
            IngestJobQueryUseCase queryUseCase,
            RetryIngestJobUseCase retryUseCase,
            CancelIngestJobUseCase cancelUseCase,
            ReprocessIngestJobUseCase reprocessUseCase
    ) {
        this.createUseCase = createUseCase;
        this.queryUseCase = queryUseCase;
        this.retryUseCase = retryUseCase;
        this.cancelUseCase = cancelUseCase;
        this.reprocessUseCase = reprocessUseCase;
    }

    /**
     * 上传 PDF/图片并创建入库作业。
     *
     * @param kbId 知识库
     * @param file 文件（≤50MB，multipart）
     * @return 作业 ID 与是否幂等命中
     */
    @PostMapping(value = "/knowledge-bases/{kbId}/ingest-jobs",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<IngestJobCreateResponse> create(
            @PathVariable long kbId,
            @RequestParam("file") MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            throw new com.aicode.core.domain.exception.InvalidChatRequestException("file must not be empty");
        }
        long userId = StpUtil.getLoginIdAsLong();
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new FileStorageException("failed to read uploaded file", ex);
        }
        CreateIngestJobOutcome outcome = createUseCase.create(
                kbId, userId, file.getOriginalFilename(), file.getContentType(), bytes);
        return ApiResponse.success(IngestJobCreateResponse.from(outcome));
    }

    /**
     * 列出知识库下的入库作业。
     *
     * @param kbId  知识库
     * @param limit 条数上限
     * @return 作业列表
     */
    @GetMapping("/knowledge-bases/{kbId}/ingest-jobs")
    public ApiResponse<List<IngestJobSummaryDto>> list(
            @PathVariable long kbId,
            @RequestParam(defaultValue = "20") int limit
    ) {
        long userId = StpUtil.getLoginIdAsLong();
        List<IngestJobSummaryDto> data = queryUseCase.list(kbId, userId, Math.min(Math.max(limit, 1), 100))
                .stream().map(IngestJobSummaryDto::from).toList();
        return ApiResponse.success(data);
    }

    /**
     * 作业详情（Job + 节点，含 prompt 原文）。
     *
     * @param jobId 作业
     * @return 详情
     */
    @GetMapping("/ingest-jobs/{jobId}")
    public ApiResponse<IngestJobDetailDto> detail(@PathVariable String jobId) {
        long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(IngestJobDetailDto.from(queryUseCase.detail(jobId, userId)));
    }

    /**
     * 从失败步重试。
     *
     * @param jobId 作业
     * @return 空响应
     */
    @PostMapping("/ingest-jobs/{jobId}/retry")
    public ApiResponse<Void> retry(@PathVariable String jobId) {
        long userId = StpUtil.getLoginIdAsLong();
        retryUseCase.retry(jobId, userId);
        return ApiResponse.success(null);
    }

    /**
     * 只重清洗/切片（不重跑 OCR）。
     *
     * @param jobId 作业
     * @return 空响应
     */
    @PostMapping("/ingest-jobs/{jobId}/reprocess")
    public ApiResponse<Void> reprocess(@PathVariable String jobId) {
        long userId = StpUtil.getLoginIdAsLong();
        reprocessUseCase.reprocess(jobId, userId);
        return ApiResponse.success(null);
    }

    /**
     * 取消未完成的作业。
     *
     * @param jobId 作业
     * @return 空响应
     */
    @PostMapping("/ingest-jobs/{jobId}/cancel")
    public ApiResponse<Void> cancel(@PathVariable String jobId) {
        long userId = StpUtil.getLoginIdAsLong();
        cancelUseCase.cancel(jobId, userId);
        return ApiResponse.success(null);
    }
}
