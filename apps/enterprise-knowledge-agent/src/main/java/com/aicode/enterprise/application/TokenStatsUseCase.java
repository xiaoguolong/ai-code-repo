package com.aicode.enterprise.application;

import com.aicode.enterprise.domain.model.TokenStats;
import com.aicode.enterprise.domain.port.AuditPort;
import org.springframework.stereotype.Service;

/**
 * 查询用户累计 Token 用例。Controller 不得直接访问 AuditPort。
 */
@Service
public class TokenStatsUseCase {

    private final AuditPort auditPort;

    public TokenStatsUseCase(AuditPort auditPort) {
        this.auditPort = auditPort;
    }

    /**
     * @return 当前用户累计请求次数与 Token
     */
    public TokenStats summary(Long userId) {
        return auditPort.summaryByUser(userId);
    }
}
