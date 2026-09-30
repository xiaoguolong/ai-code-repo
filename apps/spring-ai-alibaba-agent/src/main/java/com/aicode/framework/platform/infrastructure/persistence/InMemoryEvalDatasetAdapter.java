package com.aicode.framework.platform.infrastructure.persistence;

import com.aicode.core.domain.model.EvalDataset;
import com.aicode.framework.platform.domain.port.EvalDatasetPort;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内评估数据集存储。
 */
@Component
public class InMemoryEvalDatasetAdapter implements EvalDatasetPort {

    private final ConcurrentHashMap<String, EvalDataset> store = new ConcurrentHashMap<>();

    @Override
    public void save(EvalDataset dataset) {
        store.put(dataset.datasetKey(), dataset);
    }

    @Override
    public Optional<EvalDataset> findByKey(String datasetKey) {
        return Optional.ofNullable(store.get(datasetKey));
    }

    @Override
    public List<EvalDataset> listAll() {
        return store.values().stream()
                .sorted(Comparator.comparing(EvalDataset::datasetKey))
                .toList();
    }

    boolean isEmpty() {
        return store.isEmpty();
    }
}
