package com.aicode.framework.platform.infrastructure.persistence;

import com.aicode.framework.platform.domain.model.ExecutionRecord;
import com.aicode.framework.platform.domain.port.ExecutionRecordPort;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内执行记录存储（Week 12 V1）。
 */
@Component
public class InMemoryExecutionRecordAdapter implements ExecutionRecordPort {

    private final ConcurrentHashMap<String, ExecutionRecord> store = new ConcurrentHashMap<>();

    @Override
    public void save(ExecutionRecord record) {
        store.put(record.executionId(), record);
    }

    @Override
    public Optional<ExecutionRecord> findById(String executionId) {
        return Optional.ofNullable(store.get(executionId));
    }

    @Override
    public List<ExecutionRecord> listAll() {
        return store.values().stream()
                .sorted(Comparator.comparing(ExecutionRecord::startedAt).reversed())
                .toList();
    }

    @Override
    public List<ExecutionRecord> listByUserId(long userId) {
        return store.values().stream()
                .filter(record -> record.userId() == userId)
                .sorted(Comparator.comparing(ExecutionRecord::startedAt).reversed())
                .toList();
    }
}
