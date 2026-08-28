package com.aicode.demo.application;

import com.aicode.demo.domain.model.TokenStats;
import com.aicode.demo.domain.port.AuditPort;
import org.springframework.stereotype.Service;

/**
 * 查询累计 Token。Controller 不得直接访问 AuditPort。
 */
@Service
public class TokenStatsUseCase {

    private final AuditPort auditPort;

    public TokenStatsUseCase(AuditPort auditPort) {
        this.auditPort = auditPort;
    }

    /**
     * @return 进程内累计请求次数与 Token
     */
    public TokenStats summary() {
        return auditPort.summary();
    }
}
