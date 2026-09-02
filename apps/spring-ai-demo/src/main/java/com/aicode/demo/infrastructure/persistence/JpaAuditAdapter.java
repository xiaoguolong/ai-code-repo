package com.aicode.demo.infrastructure.persistence;

import com.aicode.demo.domain.model.ChatAuditRecord;
import com.aicode.demo.domain.model.TokenStats;
import com.aicode.demo.domain.port.AuditPort;
import com.aicode.demo.infrastructure.persistence.entity.TokenRecordEntity;
import com.aicode.demo.infrastructure.persistence.repository.TokenRecordRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基于 JPA 的审计适配器。把每次调用写入 token_record。
 */
@Component
public class JpaAuditAdapter implements AuditPort {

    private final TokenRecordRepository tokenRecordRepository;

    public JpaAuditAdapter(TokenRecordRepository tokenRecordRepository) {
        this.tokenRecordRepository = tokenRecordRepository;
    }

    @Override
    @Transactional
    public void record(ChatAuditRecord record) {
        TokenRecordEntity entity = new TokenRecordEntity(
                record.sessionId(),
                record.model(),
                record.promptTokens(),
                record.completionTokens(),
                record.totalTokens(),
                record.latencyMs(),
                record.status().name(),
                record.errorCode(),
                record.occurredAt()
        );
        tokenRecordRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public TokenStats summary() {
        return tokenRecordRepository.findAll().stream()
                .map(entity -> new TokenStats(
                        1,
                        entity.promptTokens(),
                        entity.completionTokens(),
                        entity.totalTokens()
                ))
                .reduce(new TokenStats(0, 0, 0, 0), TokenStats::plus);
    }
}
