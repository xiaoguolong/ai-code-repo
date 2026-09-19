package com.aicode.core.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本切片器：固定窗口 + 重叠，把文档正文切成适合向量化与检索的小块。
 * 纯函数，不依赖任何端口，便于单测覆盖。
 */
public final class TextChunker {

    private TextChunker() {
    }

    /**
     * 按字符窗口切片。相邻块重叠 chunkOverlap 字符，尾部不产生长度 0 的碎片。
     *
     * @param content      文档正文
     * @param chunkSize    单块最大字符数
     * @param chunkOverlap 相邻块重叠字符数
     * @return 有序切片文本列表；空文本返回空列表
     */
    public static List<String> split(String content, int chunkSize, int chunkOverlap) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        int size = Math.max(1, chunkSize);
        int overlap = Math.max(0, Math.min(chunkOverlap, size - 1));
        String text = content.strip();
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + size, text.length());
            chunks.add(text.substring(start, end));
            if (end >= text.length()) {
                break;
            }
            start = end - overlap;
        }
        return List.copyOf(chunks);
    }
}
