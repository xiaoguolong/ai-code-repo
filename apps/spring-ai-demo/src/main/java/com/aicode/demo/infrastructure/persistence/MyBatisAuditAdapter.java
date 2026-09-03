package com.aicode.demo.infrastructure.persistence;

import com.aicode.demo.domain.model.ChatAuditRecord;
import com.aicode.demo.domain.model.TokenStats;
import com.aicode.demo.domain.port.AuditPort;
import com.aicode.demo.infrastructure.persistence.entity.TokenRecordEntity;
import com.aicode.demo.infrastructure.persistence.mapper.TokenRecordMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 基于 Fluent-MyBatis 的审计适配器。把每次调用写入 token_record。
 */
@Component
@ConditionalOnProperty(name = "chat.audit-provider", havingValue = "mybatis", matchIfMissing = true)
public class MyBatisAuditAdapter implements AuditPort {

    private final TokenRecordMapper tokenRecordMapper;

    public MyBatisAuditAdapter(TokenRecordMapper tokenRecordMapper) {
        this.tokenRecordMapper = tokenRecordMapper;
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
        tokenRecordMapper.insert(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public TokenStats summary() {
        return tokenRecordMapper.listByMapAndDefault(Map.of()).stream()
                .map(entity -> new TokenStats(
                        1,
                        entity.getPromptTokens(),
                        entity.getCompletionTokens(),
                        entity.getTotalTokens()
                ))
                .reduce(new TokenStats(0, 0, 0, 0), TokenStats::plus);
    }
}
