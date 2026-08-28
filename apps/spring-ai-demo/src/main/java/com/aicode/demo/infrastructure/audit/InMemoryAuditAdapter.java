package com.aicode.demo.infrastructure.audit;

import com.aicode.demo.domain.model.ChatAuditRecord;
import com.aicode.demo.domain.port.AuditPort;
import com.aicode.demo.domain.model.TokenStats;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 进程内审计。第1周不引入数据库；重启后统计清零是预期行为。
 */
@Component
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
