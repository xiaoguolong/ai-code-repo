package com.aicode.framework.platform.infrastructure.persistence;

import com.aicode.core.domain.model.EvalReport;
import com.aicode.framework.platform.domain.port.EvalRunPort;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内评估报告存储。
 */
@Component
public class InMemoryEvalRunAdapter implements EvalRunPort {

    private final ConcurrentHashMap<String, EvalReport> store = new ConcurrentHashMap<>();

    @Override
    public void save(EvalReport report) {
        store.put(report.runId(), report);
    }

    @Override
    public Optional<EvalReport> findById(String runId) {
        return Optional.ofNullable(store.get(runId));
    }

    @Override
    public List<EvalReport> listByUserId(long userId) {
        return store.values().stream()
                .filter(report -> report.userId() == userId)
                .sorted(Comparator.comparing(EvalReport::finishedAt).reversed())
                .toList();
    }

    @Override
    public List<EvalReport> listAll() {
        return store.values().stream()
                .sorted(Comparator.comparing(EvalReport::finishedAt).reversed())
                .toList();
    }
}
