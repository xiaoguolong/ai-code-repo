package com.aicode.demo.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文本切片器：固定窗口 + 重叠，尾部不产生碎片。
 */
class TextChunkerTest {

    @Test
    void shouldSplitLongTextIntoNonOverlappingChunks() {
        List<String> chunks = TextChunker.split("0123456789abcdefghij", 10, 0);

        assertThat(chunks).containsExactly("0123456789", "abcdefghij");
    }

    @Test
    void shouldReturnSingleChunk_whenTextShorterThanChunkSize() {
        List<String> chunks = TextChunker.split("abc", 10, 2);

        assertThat(chunks).containsExactly("abc");
    }

    @Test
    void shouldReturnEmpty_whenContentBlank() {
        assertThat(TextChunker.split(null, 10, 2)).isEmpty();
        assertThat(TextChunker.split("   ", 10, 2)).isEmpty();
    }

    @Test
    void shouldOverlapBetweenAdjacentChunks() {
        List<String> chunks = TextChunker.split("0123456789abcdefghij", 10, 4);

        assertThat(chunks).containsExactly("0123456789", "6789abcdef", "cdefghij");
    }

    @Test
    void shouldNotProduceEmptyChunk_whenOverlapEqualsChunkSize() {
        List<String> chunks = TextChunker.split("0123456789", 10, 10);

        assertThat(chunks).containsExactly("0123456789");
    }
}
