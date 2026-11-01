package com.aicode.enterprise.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 入库异步执行器（Week 32）：有界队列 + 拒绝策略，避免大文件把企业服务拖死。
 */
@Configuration
public class IngestExecutorConfiguration {

    /**
     * @param properties 入库配置（线程数/队列长度）
     * @return 专用线程池
     */
    @Bean("ingestTaskExecutor")
    public ThreadPoolTaskExecutor ingestTaskExecutor(IngestProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getWorkerCount());
        executor.setMaxPoolSize(properties.getWorkerCount());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setThreadNamePrefix("ingest-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
