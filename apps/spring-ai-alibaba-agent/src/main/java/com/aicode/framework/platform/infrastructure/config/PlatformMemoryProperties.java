package com.aicode.framework.platform.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 长期记忆配置（Week 21，前缀 {@code platform.memory}）。
 *
 * @param candidateLimit 语义检索候选集上限（防止全表扫描）
 * @param minScore       最低余弦相似度，超出 [0,1] 或负数时回退 0.05
 * @param defaultTopK    请求未指定条数时的默认值
 * @param maxTopK        单次返回条数硬上限
 */
@ConfigurationProperties(prefix = "platform.memory")
public record PlatformMemoryProperties(
        int candidateLimit,
        double minScore,
        int defaultTopK,
        int maxTopK
) {

    /** 默认候选集上限。 */
    public static final int DEFAULT_CANDIDATE_LIMIT = 500;
    /** 默认最低相似度。 */
    public static final double DEFAULT_MIN_SCORE = 0.05;
    /** 默认返回条数。 */
    public static final int DEFAULT_TOP_K = 5;
    /** 单次返回硬上限。 */
    public static final int MAX_TOP_K_LIMIT = 20;

    /**
     * @return 有效候选集上限，非法时回退 500
     */
    public int resolvedCandidateLimit() {
        return candidateLimit <= 0 ? DEFAULT_CANDIDATE_LIMIT : candidateLimit;
    }

    /**
     * @return 有效最低相似度，`&lt;0` 或 `&gt;1` 时回退 0.05
     */
    public double resolvedMinScore() {
        return (minScore < 0 || minScore > 1) ? DEFAULT_MIN_SCORE : minScore;
    }

    /**
     * @return 有效硬上限，非法时回退 20
     */
    public int resolvedMaxTopK() {
        return maxTopK <= 0 ? MAX_TOP_K_LIMIT : maxTopK;
    }

    /**
     * @return 有效默认条数：非法回退 5，且不超过硬上限
     */
    public int resolvedDefaultTopK() {
        if (defaultTopK <= 0) {
            return DEFAULT_TOP_K;
        }
        return Math.min(defaultTopK, resolvedMaxTopK());
    }
}
