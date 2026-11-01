package com.aicode.framework.platform.infrastructure.stream;

import com.aicode.framework.platform.infrastructure.config.PlatformStreamProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 流式执行线程池与槽位（Week 31）。
 *
 * <p>为什么需要两套限流：</p>
 * <ul>
 *   <li><b>信号量</b>：在"建执行记录"之前决定能不能接，接不了直接 429，不留 RUNNING 僵尸；</li>
 *   <li><b>线程池</b>：真正跑模型调用；大小等于信号量，队列只吸收提交瞬间的抖动。</li>
 * </ul>
 * <p>心跳用单独的单线程调度器：模型长时间没有增量时也要给浏览器/nginx 一点字节。</p>
 */
@Component
public class PlatformStreamDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PlatformStreamDispatcher.class);

    private final ThreadPoolExecutor executor;
    private final Semaphore slots;
    private final ScheduledExecutorService heartbeatScheduler;
    private final int heartbeatSeconds;

    public PlatformStreamDispatcher(PlatformStreamProperties properties) {
        int maxConcurrent = Math.max(1, properties.getMaxConcurrent());
        this.slots = new Semaphore(maxConcurrent, true);
        this.heartbeatSeconds = Math.max(1, properties.getHeartbeatSeconds());
        this.executor = new ThreadPoolExecutor(
                maxConcurrent, maxConcurrent,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(Math.max(1, properties.getQueueCapacity())),
                runnable -> {
                    Thread thread = new Thread(runnable, "platform-sse-worker");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
        this.heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "platform-sse-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 尝试占用一个并发槽位。
     *
     * @return true 表示可以接这条流
     */
    public boolean tryAcquire() {
        return slots.tryAcquire();
    }

    /** 释放槽位（必须在 worker 的 finally 里调用）。 */
    public void release() {
        slots.release();
    }

    /**
     * 提交 worker 任务。
     *
     * @param task 任务
     * @throws RejectedExecutionException 队列满（正常不会发生，信号量已限流）
     */
    public void submit(Runnable task) {
        executor.execute(task);
    }

    /**
     * 注册心跳。
     *
     * @param ping 心跳动作
     * @return 可取消的句柄
     */
    public ScheduledFuture<?> scheduleHeartbeat(Runnable ping) {
        return heartbeatScheduler.scheduleAtFixedRate(
                ping, heartbeatSeconds, heartbeatSeconds, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        log.info("[platform] shutting down sse dispatcher");
        heartbeatScheduler.shutdownNow();
        executor.shutdownNow();
    }
}
