package com.aicode.demo.infrastructure.vector;

import com.aicode.demo.domain.model.DocumentChunk;
import com.aicode.demo.domain.model.VectorSearchHit;
import com.aicode.demo.domain.port.VectorStorePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 进程内向量库：按余弦相似度检索。默认启用；rag.vector-store=pgvector 时让位给 pgvector。
 */
@Component
@ConditionalOnProperty(name = "rag.vector-store", havingValue = "memory", matchIfMissing = true)
public class InMemoryVectorStoreAdapter implements VectorStorePort {

    private record Entry(DocumentChunk chunk, float[] vector) {
    }

    private final List<Entry> entries = new CopyOnWriteArrayList<>();

    @Override
    public void put(DocumentChunk chunk, float[] vector) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(vector, "vector");
        entries.add(new Entry(chunk, vector.clone()));
    }

    @Override
    public List<VectorSearchHit> search(float[] query, int topK) {
        Objects.requireNonNull(query, "query");
        int limit = Math.max(0, topK);
        return entries.stream()
                .map(entry -> new VectorSearchHit(
                        entry.chunk.documentId(),
                        entry.chunk.chunkIndex(),
                        entry.chunk.content(),
                        cosine(query, entry.vector)
                ))
                .sorted(Comparator.comparingDouble(VectorSearchHit::score).reversed())
                .limit(limit)
                .toList();
    }

    static double cosine(float[] a, float[] b) {
        double dot = 0;
        int length = Math.min(a.length, b.length);
        for (int i = 0; i < length; i++) {
            dot += (double) a[i] * b[i];
        }
        double normA = 0;
        double normB = 0;
        for (float value : a) {
            normA += (double) value * value;
        }
        for (float value : b) {
            normB += (double) value * value;
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
