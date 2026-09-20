package com.aicode.core.domain;

/**
 * 向量相似度工具（纯函数）。供向量库与向量记忆复用，避免各处重复实现余弦计算。
 */
public final class VectorMath {

    private VectorMath() {
    }

    /**
     * 计算两个向量的余弦相似度。长度不齐时按较短者截断；任一为零向量返回 0。
     *
     * @param a 向量 a
     * @param b 向量 b
     * @return 余弦相似度，零向量或空向量返回 0
     */
    public static double cosine(float[] a, float[] b) {
        int length = Math.min(a.length, b.length);
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
