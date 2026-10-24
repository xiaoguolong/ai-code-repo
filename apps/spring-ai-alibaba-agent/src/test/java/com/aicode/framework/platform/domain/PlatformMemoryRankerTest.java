package com.aicode.framework.platform.domain;

import com.aicode.framework.platform.domain.model.PlatformMemoryHit;
import com.aicode.framework.platform.domain.model.PlatformMemoryRecord;
import com.aicode.framework.platform.domain.model.PlatformMemoryVector;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 记忆排序纯函数测试：余弦降序、阈值过滤、topK 截断。 */
class PlatformMemoryRankerTest {

    @Test
    void ordersByCosineSimilarityDescending() {
        List<PlatformMemoryHit> hits = PlatformMemoryRanker.rank(
                List.of(vector("m-close", 0.7f, 0.7f, 0f), vector("m-closer", 0.95f, 0.31f, 0f)),
                new float[]{1f, 0f, 0f}, 5, 0.0);

        assertThat(hits).extracting(hit -> hit.record().memoryId())
                .containsExactly("m-closer", "m-close");
        assertThat(hits.get(0).score()).isGreaterThan(hits.get(1).score());
    }

    @Test
    void dropsCandidatesWithNonPositiveSimilarity() {
        List<PlatformMemoryHit> hits = PlatformMemoryRanker.rank(
                List.of(vector("m-close", 0.7f, 0.7f, 0f), vector("m-orthogonal", 0f, 1f, 0f)),
                new float[]{1f, 0f, 0f}, 5, 0.0);

        assertThat(hits).extracting(hit -> hit.record().memoryId()).containsExactly("m-close");
    }

    @Test
    void filtersScoresBelowThreshold() {
        List<PlatformMemoryHit> hits = PlatformMemoryRanker.rank(
                List.of(vector("m-exact", 1f, 0f, 0f), vector("m-diagonal", 0.5f, 0.5f, 0f)),
                new float[]{1f, 0f, 0f}, 5, 0.8);

        assertThat(hits).extracting(hit -> hit.record().memoryId()).containsExactly("m-exact");
    }

    @Test
    void capsResultAtTopK() {
        List<PlatformMemoryHit> hits = PlatformMemoryRanker.rank(
                List.of(vector("a", 1f, 0f, 0f), vector("b", 0.9f, 0.1f, 0f), vector("c", 0.8f, 0.2f, 0f)),
                new float[]{1f, 0f, 0f}, 2, 0.0);

        assertThat(hits).hasSize(2);
    }

    @Test
    void returnsEmptyListForNoCandidatesOrBlankQueryVector() {
        assertThat(PlatformMemoryRanker.rank(List.of(), new float[]{1f}, 5, 0.0)).isEmpty();
        assertThat(PlatformMemoryRanker.rank(
                List.of(vector("a", 1f, 0f, 0f)), new float[]{0f, 0f, 0f}, 5, 0.0)).isEmpty();
        assertThat(PlatformMemoryRanker.rank(
                List.of(vector("a", 1f, 0f, 0f)), new float[]{1f, 0f, 0f}, 0, 0.0)).isEmpty();
    }

    private PlatformMemoryVector vector(String memoryId, float x, float y, float z) {
        return new PlatformMemoryVector(
                new PlatformMemoryRecord(memoryId, "default", 2L, "P001", "task-" + memoryId, "answer",
                        Instant.parse("2026-10-24T00:00:00Z")),
                new float[]{x, y, z});
    }
}
