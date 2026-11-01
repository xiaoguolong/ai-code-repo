package com.aicode.enterprise.application;

import com.aicode.enterprise.domain.exception.ConflictException;
import com.aicode.enterprise.domain.port.IngestJobLauncher;
import org.springframework.stereotype.Service;

/**
 * 只重清洗/切片（不重跑 OCR）：提示词升级后修复历史文档（Week 32 增补）。
 */
@Service
public class ReprocessIngestJobUseCase {

    private final IngestJobQueryUseCase queryUseCase;
    private final IngestPipeline pipeline;
    private final IngestJobLauncher launcher;

    public ReprocessIngestJobUseCase(
            IngestJobQueryUseCase queryUseCase,
            IngestPipeline pipeline,
            IngestJobLauncher launcher
    ) {
        this.queryUseCase = queryUseCase;
        this.pipeline = pipeline;
        this.launcher = launcher;
    }

    /**
     * @param jobId  作业
     * @param userId 当前用户
     * @throws ConflictException 没有 OCR 文本（需先正常入库）
     */
    public void reprocess(String jobId, long userId) {
        queryUseCase.detail(jobId, userId);
        if (!pipeline.reprocess(jobId)) {
            throw new ConflictException("job has no OCR text to reprocess");
        }
        launcher.launch(jobId);
    }
}
