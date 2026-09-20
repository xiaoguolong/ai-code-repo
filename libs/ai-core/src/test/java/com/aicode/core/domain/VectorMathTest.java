package com.aicode.core.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 余弦相似度纯函数测试。
 */
class VectorMathTest {

    @Test
    void identicalVectorsHaveCosineOne() {
        float[] v = {1f, 2f, 3f};

        assertThat(VectorMath.cosine(v, v)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void orthogonalVectorsHaveCosineZero() {
        assertThat(VectorMath.cosine(new float[]{1f, 0f}, new float[]{0f, 1f})).isZero();
    }

    @Test
    void zeroVectorHasCosineZero() {
        assertThat(VectorMath.cosine(new float[]{0f, 0f}, new float[]{1f, 1f})).isZero();
    }

    @Test
    void handlesDifferentLengthsByTruncatingToShorter() {
        assertThat(VectorMath.cosine(new float[]{1f, 1f}, new float[]{1f, 1f, 100f})).isCloseTo(
                1.0, org.assertj.core.data.Offset.offset(1e-9));
    }
}
