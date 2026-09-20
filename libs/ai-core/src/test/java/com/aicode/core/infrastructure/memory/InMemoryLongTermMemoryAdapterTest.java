package com.aicode.core.infrastructure.memory;

import com.aicode.core.domain.model.MemoryHit;
import com.aicode.core.domain.model.MemoryRecord;
import com.aicode.core.domain.port.EmbeddingModelPort;
import com.aicode.core.infrastructure.config.EmbeddingProperties;
import com.aicode.core.infrastructure.embedding.HashingEmbeddingAdapter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内存向量长期记忆适配器测试。用离线 Hashing 向量，不连真实网络。
 */
class InMemoryLongTermMemoryAdapterTest {

    private final EmbeddingModelPort embedding = new HashingEmbeddingAdapter(
            new EmbeddingProperties("hashing", null, null, null, 256, 0));
    private final InMemoryLongTermMemoryAdapter adapter = new InMemoryLongTermMemoryAdapter(embedding);

    private MemoryRecord record(String id, String task, String answer) {
        return new MemoryRecord(id, "s1", task, answer, null);
    }

    @Test
    void searchReturnsEmptyWhenNoMemory() {
        assertThat(adapter.search("任意 查询", 3)).isEmpty();
    }

    @Test
    void searchReturnsEmptyForBlankQuery() {
        adapter.save(record("m1", "患者 P001 血压 评估", "血压偏高"));

        assertThat(adapter.search("   ", 3)).isEmpty();
    }

    @Test
    void ranksSemanticallyRelatedMemoryFirst() {
        adapter.save(record("m1", "患者 P001 血压 评估", "血压偏高"));
        adapter.save(record("m2", "患者 P002 血脂 评估", "血脂正常"));
        adapter.save(record("m3", "患者 P001 血糖 评估", "血糖偏高"));

        List<MemoryHit> hits = adapter.search("P001 血压", 2);

        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).record().memoryId()).isEqualTo("m1");
        assertThat(hits.get(0).score()).isGreaterThan(0);
        assertThat(hits).allMatch(hit -> hit.score() > 0);
    }

    @Test
    void respectsTopK() {
        adapter.save(record("m1", "患者 P001 血压 评估", "血压偏高"));
        adapter.save(record("m3", "患者 P001 血糖 评估", "血糖偏高"));

        assertThat(adapter.search("P001", 1)).hasSize(1);
    }
}
