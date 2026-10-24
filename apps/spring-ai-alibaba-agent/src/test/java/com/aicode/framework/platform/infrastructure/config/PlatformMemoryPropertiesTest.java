package com.aicode.framework.platform.infrastructure.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 记忆配置默认值与边界收敛。 */
class PlatformMemoryPropertiesTest {

    @Test
    void fallsBackToDocumentedDefaults() {
        PlatformMemoryProperties properties = new PlatformMemoryProperties(0, -1, 0, 0);

        assertThat(properties.resolvedCandidateLimit()).isEqualTo(500);
        assertThat(properties.resolvedMinScore()).isEqualTo(0.05);
        assertThat(properties.resolvedDefaultTopK()).isEqualTo(5);
        assertThat(properties.resolvedMaxTopK()).isEqualTo(20);
    }

    @Test
    void clampsTopKAndAcceptsZeroMinScore() {
        PlatformMemoryProperties properties = new PlatformMemoryProperties(10, 0.0, 50, 3);

        assertThat(properties.resolvedCandidateLimit()).isEqualTo(10);
        assertThat(properties.resolvedMinScore()).isZero();
        assertThat(properties.resolvedDefaultTopK()).isEqualTo(3);
        assertThat(properties.resolvedMaxTopK()).isEqualTo(3);
    }

    @Test
    void clampsMinScoreIntoUnitRange() {
        assertThat(new PlatformMemoryProperties(1, 2.5, 1, 1).resolvedMinScore()).isEqualTo(0.05);
    }
}
