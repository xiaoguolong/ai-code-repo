package com.aicode.enterprise.infrastructure.executor;

import com.aicode.enterprise.application.IngestPipeline;
import com.aicode.enterprise.domain.port.IngestJobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * 用专用线程池执行入库作业（Week 32）。线程池满时抛 {@code TaskRejectedException}，
 * 由接口层转 429。
 */
@Component
public class IngestJobExecutor implements IngestJobLauncher {

    private final AsyncTaskExecutor executor;
    private final IngestPipeline pipeline;

    public IngestJobExecutor(
            @Qualifier("ingestTaskExecutor") AsyncTaskExecutor executor,
            IngestPipeline pipeline
    ) {
        this.executor = executor;
        this.pipeline = pipeline;
    }

    @Override
    public void launch(String jobId) {
        executor.execute(() -> pipeline.run(jobId));
    }
}
