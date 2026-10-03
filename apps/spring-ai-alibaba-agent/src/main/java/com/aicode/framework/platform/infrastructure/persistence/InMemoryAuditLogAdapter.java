package com.aicode.framework.platform.infrastructure.persistence;

import com.aicode.framework.platform.domain.model.AuditAction;
import com.aicode.framework.platform.domain.model.AuditLogEntry;
import com.aicode.framework.platform.domain.model.AuditResult;
import com.aicode.framework.platform.domain.port.AuditLogPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 进程内审计日志存储（Week 16，{@code platform.persistence.mode=memory}）。
 *
 * <p>无数据库启动 / 局部单测时使用，保证审计调用点始终有可注入实现。</p>
 */
@Component
@ConditionalOnProperty(name = "platform.persistence.mode", havingValue = "memory")
public class InMemoryAuditLogAdapter implements AuditLogPort {

    private final ConcurrentHashMap<Long, AuditLogEntry> store = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);

    @Override
    public void record(AuditLogEntry entry) {
        long auditId = sequence.incrementAndGet();
        store.put(auditId, new AuditLogEntry(
                auditId, entry.userId(), entry.action(), entry.resource(), entry.result(),
                entry.traceId() == null ? "" : entry.traceId(),
                truncate(entry.detail()), entry.createdAt()));
    }

    @Override
    public List<AuditLogEntry> listByUserId(long userId) {
        return store.values().stream()
                .filter(entry -> entry.userId() == userId)
                .sorted(Comparator.comparing(AuditLogEntry::createdAt).reversed())
                .toList();
    }

    @Override
    public List<AuditLogEntry> listAll() {
        return store.values().stream()
                .sorted(Comparator.comparing(AuditLogEntry::createdAt).reversed())
                .toList();
    }

    private String truncate(String detail) {
        if (detail == null) {
            return "";
        }
        return detail.length() <= AuditLogEntry.MAX_DETAIL_LENGTH
                ? detail
                : detail.substring(0, AuditLogEntry.MAX_DETAIL_LENGTH);
    }
}
