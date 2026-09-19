package com.aicode.enterprise.infrastructure.persistence;

import com.aicode.core.domain.model.TokenRecord;
import com.aicode.core.domain.model.TokenStats;
import com.aicode.core.domain.port.AuditPort;
import com.aicode.enterprise.infrastructure.persistence.entity.TokenRecordEntity;
import com.aicode.enterprise.infrastructure.persistence.mapper.TokenRecordMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 基于 Fluent-MyBatis 的审计适配器。把每次调用写入 token_record。
 */
@Component
public class MyBatisAuditAdapter implements AuditPort {

    private final TokenRecordMapper tokenRecordMapper;

    public MyBatisAuditAdapter(TokenRecordMapper tokenRecordMapper) {
        this.tokenRecordMapper = tokenRecordMapper;
    }

    @Override
    @Transactional
    public void record(TokenRecord record) {
        tokenRecordMapper.insert(new TokenRecordEntity(
                record.sessionId(),
                record.userId(),
                record.model(),
                record.promptTokens(),
                record.completionTokens(),
                record.totalTokens(),
                record.createdAt()
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public TokenStats summaryByUser(long userId) {
        return tokenRecordMapper.listByMap(false, Map.of("userId", userId)).stream()
                .map(entity -> new TokenStats(
                        1,
                        entity.getPromptTokens() == null ? 0 : entity.getPromptTokens(),
                        entity.getCompletionTokens() == null ? 0 : entity.getCompletionTokens(),
                        entity.getTotalTokens() == null ? 0 : entity.getTotalTokens()
                ))
                .reduce(new TokenStats(0, 0, 0, 0), TokenStats::plus);
    }
}
