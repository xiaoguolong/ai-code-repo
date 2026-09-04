package com.aicode.enterprise.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文本切片器单元测试。
 */
class TextChunkerTest {

    @Test
    void splitsLongTextIntoFixedWindows() {
        List<String> chunks = TextChunker.split("1234567890", 4, 0);

        assertThat(chunks).containsExactly("1234", "5678", "90");
    }

    @Test
    void keepsShortTextAsSingleChunk() {
        List<String> chunks = TextChunker.split("abc", 10, 0);

        assertThat(chunks).containsExactly("abc");
    }

    @Test
    void returnsEmptyForBlankContent() {
        assertThat(TextChunker.split("   ", 10, 0)).isEmpty();
        assertThat(TextChunker.split(null, 10, 0)).isEmpty();
    }

    @Test
    void overlapsAdjacentChunks() {
        List<String> chunks = TextChunker.split("abcdefgh", 4, 2);

        assertThat(chunks).containsExactly("abcd", "cdef", "efgh");
    }
}
