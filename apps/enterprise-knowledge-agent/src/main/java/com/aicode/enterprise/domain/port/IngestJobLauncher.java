package com.aicode.enterprise.domain.port;

/**
 * 入库作业异步启动端口（Week 32）。用例只负责"提交"，不依赖线程池实现。
 */
public interface IngestJobLauncher {

    /**
     * 提交作业执行；线程池满时抛运行时异常（由接口层转 429）。
     *
     * @param jobId 作业 ID
     */
    void launch(String jobId);
}
