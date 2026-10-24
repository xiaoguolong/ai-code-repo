package com.aicode.framework.platform.domain;

import com.aicode.core.domain.VectorMath;
import com.aicode.framework.platform.domain.model.PlatformMemoryHit;
import com.aicode.framework.platform.domain.model.PlatformMemoryVector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 记忆语义排序纯函数（Week 21）。
 *
 * <p>把「候选集 → 余弦 → 阈值 → topK」这一段从适配器里抽出来：
 * JDBC / 内存两种实现共用同一份排序逻辑，单测也只测这一份，避免两套实现漂移。</p>
 */
public final class PlatformMemoryRanker {

    private PlatformMemoryRanker() {
    }

    /**
     * 对候选记忆做余弦排序。
     *
     * @param candidates   候选（已按 scope 过滤）
     * @param queryVector  查询向量；空或全零时返回空列表
     * @param topK         返回条数上限，&lt;=0 时返回空列表
     * @param minScore     最低相似度（含）
     * @return 按相似度降序、长度不超过 topK 的命中列表
     */
    public static List<PlatformMemoryHit> rank(
            List<PlatformMemoryVector> candidates,
            float[] queryVector,
            int topK,
            double minScore
    ) {
        if (candidates == null || candidates.isEmpty() || topK <= 0 || isEmptyVector(queryVector)) {
            return List.of();
        }
        List<PlatformMemoryHit> hits = new ArrayList<>();
        for (PlatformMemoryVector candidate : candidates) {
            if (candidate == null || isEmptyVector(candidate.embedding())) {
                continue;
            }
            double score = VectorMath.cosine(queryVector, candidate.embedding());
            if (score >= minScore && score > 0) {
                hits.add(new PlatformMemoryHit(candidate.record(), score));
            }
        }
        hits.sort(Comparator.comparingDouble(PlatformMemoryHit::score).reversed());
        return hits.size() <= topK ? List.copyOf(hits) : List.copyOf(hits.subList(0, topK));
    }

    private static boolean isEmptyVector(float[] vector) {
        if (vector == null || vector.length == 0) {
            return true;
        }
        for (float value : vector) {
            if (value != 0f) {
                return false;
            }
        }
        return true;
    }
}
