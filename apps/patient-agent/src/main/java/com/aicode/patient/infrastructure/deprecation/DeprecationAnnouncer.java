package com.aicode.patient.infrastructure.deprecation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时打印弃用告警（Week 21）。
 *
 * <p>为什么还要日志：响应头只有客户端能看到，运维/值班看启动日志才能第一时间知道
 * 「这台机器上还跑着一个即将下线的服务」。</p>
 */
@Component
public class DeprecationAnnouncer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DeprecationAnnouncer.class);

    private final String successorUrl;

    /**
     * @param successorUrl 替代入口地址
     */
    public DeprecationAnnouncer(
            @Value("${patient.deprecated.successor-url:http://localhost:8084/api/v1/platform/agents/patient-analysis/runs}")
            String successorUrl
    ) {
        this.successorUrl = successorUrl;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.warn("[deprecated] patient-agent（8083）计划于 {} 下线，Run 入口统一为 {}；"
                        + "响应头已带 Deprecation / Sunset / Link，本周不删除任何业务代码。",
                DeprecatedApiFilter.SUNSET_AT, successorUrl);
    }
}
