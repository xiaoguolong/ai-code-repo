package com.aicode.enterprise.infrastructure.startup;

import com.aicode.enterprise.domain.port.IngestJobPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动恢复（Week 33）：进程重启后把遗留 RUNNING/PENDING 置 FAILED，
 * 避免页面一直显示"处理中"且无法重试。
 */
@Component
public class IngestJobRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IngestJobRecovery.class);

    private final IngestJobPort ingestJobPort;

    public IngestJobRecovery(IngestJobPort ingestJobPort) {
        this.ingestJobPort = ingestJobPort;
    }

    @Override
    public void run(ApplicationArguments args) {
        int recovered = ingestJobPort.failUnfinished("restarted before completion");
        if (recovered > 0) {
            log.warn("[ingest] 启动恢复：{} 个 RUNNING/PENDING 作业已置为 FAILED（可重试）", recovered);
        }
    }
}
