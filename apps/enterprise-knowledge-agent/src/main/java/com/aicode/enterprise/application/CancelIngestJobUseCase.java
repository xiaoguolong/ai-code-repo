package com.aicode.enterprise.application;

import com.aicode.enterprise.domain.exception.ConflictException;
import org.springframework.stereotype.Service;

/**
 * 取消入库作业（Week 32）：先鉴权，再置 CANCELLED（执行线程在当前步后停止）。
 */
@Service
public class CancelIngestJobUseCase {

    private final IngestJobQueryUseCase queryUseCase;
    private final IngestPipeline pipeline;

    public CancelIngestJobUseCase(IngestJobQueryUseCase queryUseCase, IngestPipeline pipeline) {
        this.queryUseCase = queryUseCase;
        this.pipeline = pipeline;
    }

    /**
     * @param jobId  作业
     * @param userId 当前用户
     * @throws ConflictException 已是终态
     */
    public void cancel(String jobId, long userId) {
        queryUseCase.detail(jobId, userId);
        if (!pipeline.cancel(jobId)) {
            throw new ConflictException("job is already finished");
        }
    }
}
