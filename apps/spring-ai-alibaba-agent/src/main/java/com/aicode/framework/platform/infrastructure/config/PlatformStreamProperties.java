package com.aicode.framework.platform.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SSE 流式执行配置（Week 31）。
 *
 * <p>并发口径：{@code max-concurrent} 同时限制线程池大小与信号量，保证"最多 N 条流"
 * 且"拿到槽位才建执行记录"——避免出现 RUNNING 记录却没有线程执行的僵尸态。</p>
 */
@ConfigurationProperties(prefix = "platform.execution.stream")
public class PlatformStreamProperties {

    /** 是否启用流式端点；关闭时返回 503，前端自动回退同步端点。 */
    private boolean enabled = true;

    /** 同时进行的流式执行上限。 */
    private int maxConcurrent = 4;

    /** worker 队列容量（信号量已限流，队列只做提交抖动缓冲）。 */
    private int queueCapacity = 16;

    /** 单条流的超时秒数；超时按取消处理（CANCELLED）。 */
    private int timeoutSeconds = 300;

    /** 心跳间隔秒数（SSE 注释行），防止中间层把空闲连接掐掉。 */
    private int heartbeatSeconds = 15;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxConcurrent() {
        return maxConcurrent;
    }

    public void setMaxConcurrent(int maxConcurrent) {
        this.maxConcurrent = maxConcurrent;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getHeartbeatSeconds() {
        return heartbeatSeconds;
    }

    public void setHeartbeatSeconds(int heartbeatSeconds) {
        this.heartbeatSeconds = heartbeatSeconds;
    }
}
