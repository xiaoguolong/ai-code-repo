package com.aicode.enterprise.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 入库流水线配置（Week 32）。
 *
 * <p>prompt 原文永久保存在 {@code ingest_job_step.prompt_text}，本配置只管流程开关，
 * 不提供保留期/TTL（用户已确认"用个表存就行"）。</p>
 */
@Component
@ConfigurationProperties(prefix = "ingest")
public class IngestProperties {

    /** 是否执行 clean（LLM 清洗）步骤；false 时 OCR 原文直接切片。 */
    private boolean cleanEnabled = true;

    /** 单次 OCR 最大页数，超过直接失败（防跑飞）。 */
    private int maxPages = 200;

    /** 异步执行线程数（企业侧有界）。 */
    private int workerCount = 2;

    /** 排队上限，满了拒绝新作业（返回 429）。 */
    private int queueCapacity = 64;

    public boolean isCleanEnabled() {
        return cleanEnabled;
    }

    public void setCleanEnabled(boolean cleanEnabled) {
        this.cleanEnabled = cleanEnabled;
    }

    public int getMaxPages() {
        return maxPages;
    }

    public void setMaxPages(int maxPages) {
        this.maxPages = maxPages;
    }

    public int getWorkerCount() {
        return workerCount;
    }

    public void setWorkerCount(int workerCount) {
        this.workerCount = workerCount;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }
}
