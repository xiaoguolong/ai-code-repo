package com.aicode.core.domain.model;

import java.util.Map;
import java.util.Objects;

/**
 * OCR 识别结果。markdown 为正文文本（含图片占位符），images 为占位符 key 到临时图片 URL 的映射。
 *
 * @param markdown  合并后的 markdown 正文
 * @param images    占位符 key → 临时图片 URL
 * @param pageCount 厂商返回的页数（图片为 1；Week 32 起用于入库作业展示）
 */
public record OcrResult(String markdown, Map<String, String> images, int pageCount) {

    public OcrResult {
        Objects.requireNonNull(markdown, "markdown");
        Objects.requireNonNull(images, "images");
    }

    /**
     * 兼容旧调用：不关心页数时按 0 处理。
     *
     * @param markdown 正文
     * @param images   图片映射
     */
    public OcrResult(String markdown, Map<String, String> images) {
        this(markdown, images, 0);
    }
}
