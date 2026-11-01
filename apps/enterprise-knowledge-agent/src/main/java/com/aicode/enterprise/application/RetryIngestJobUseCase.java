package com.aicode.enterprise.application;

import com.aicode.enterprise.domain.exception.ConflictException;
import com.aicode.enterprise.domain.port.IngestJobLauncher;
import org.springframework.stereotype.Service;

/**
 * 重试入库作业（Week 32）：先鉴权，再重置失败状态并重新入队。
 */
@Service
public class RetryIngestJobUseCase {

    private final IngestJobQueryUseCase queryUseCase;
    private final IngestPipeline pipeline;
    private final IngestJobLauncher launcher;

    public RetryIngestJobUseCase(
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
     * @throws ConflictException 非 FAILED 状态
     */
    public void retry(String jobId, long userId) {
        queryUseCase.detail(jobId, userId);
        if (!pipeline.retry(jobId)) {
            throw new ConflictException("only FAILED job can be retried");
        }
        launcher.launch(jobId);
    }
}
