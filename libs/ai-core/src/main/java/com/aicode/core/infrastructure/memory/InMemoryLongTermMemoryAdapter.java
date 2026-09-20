package com.aicode.core.infrastructure.memory;

import com.aicode.core.domain.VectorMath;
import com.aicode.core.domain.model.MemoryHit;
import com.aicode.core.domain.model.MemoryRecord;
import com.aicode.core.domain.port.EmbeddingModelPort;
import com.aicode.core.domain.port.LongTermMemoryPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 内存向量长期记忆。保存时把「任务 + 结论」向量化入库，检索时按余弦相似度排序。
 * 默认启用；默认 {@code embedding.provider=hashing} 可离线跑通，生产切 OpenAI 兼容向量提升召回。
 */
@Component
@ConditionalOnProperty(name = "memory.long-term-provider", havingValue = "memory", matchIfMissing = true)
public class InMemoryLongTermMemoryAdapter implements LongTermMemoryPort {

    private record Entry(MemoryRecord record, float[] vector) {
    }

    private final EmbeddingModelPort embeddingModelPort;
    private final List<Entry> entries = new CopyOnWriteArrayList<>();

    public InMemoryLongTermMemoryAdapter(EmbeddingModelPort embeddingModelPort) {
        this.embeddingModelPort = embeddingModelPort;
    }

    @Override
    public void save(MemoryRecord record) {
        Objects.requireNonNull(record, "record");
        float[] vector = embeddingModelPort.embed(embedText(record));
        entries.add(new Entry(record, vector));
    }

    @Override
    public List<MemoryHit> search(String query, int topK) {
        if (query == null || query.isBlank() || topK <= 0) {
            return List.of();
        }
        float[] queryVector = embeddingModelPort.embed(query);
        return entries.stream()
                .map(entry -> new MemoryHit(entry.record(), VectorMath.cosine(queryVector, entry.vector())))
                .filter(hit -> hit.score() > 0)
                .sorted(Comparator.comparingDouble(MemoryHit::score).reversed())
                .limit(topK)
                .toList();
    }

    private String embedText(MemoryRecord record) {
        return record.task() + " " + record.answer();
    }
}
