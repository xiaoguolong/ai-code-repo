package com.aicode.demo.infrastructure.audit;

import com.aicode.demo.domain.model.ChatAuditRecord;
import com.aicode.demo.domain.port.AuditPort;
import com.aicode.demo.domain.model.TokenStats;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 进程内审计。显式设置 chat.audit-provider=memory 时启用；默认使用 JPA。
 */
@Component
@ConditionalOnProperty(name = "chat.audit-provider", havingValue = "memory")
public class InMemoryAuditAdapter implements AuditPort {

    private final ConcurrentLinkedQueue<ChatAuditRecord> records = new ConcurrentLinkedQueue<>();

    @Override
    public void record(ChatAuditRecord record) {
        records.add(Objects.requireNonNull(record, "record"));
    }

    @Override
    public TokenStats summary() {
        long count = 0;
        long prompt = 0;
        long completion = 0;
        long total = 0;
        for (ChatAuditRecord record : records) {
            count++;
            prompt += record.promptTokens();
            completion += record.completionTokens();
            total += record.totalTokens();
        }
        return new TokenStats(count, prompt, completion, total);
    }
}
