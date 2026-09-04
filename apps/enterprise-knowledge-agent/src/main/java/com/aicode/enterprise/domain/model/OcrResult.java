package com.aicode.enterprise.domain.model;

import java.util.Map;
import java.util.Objects;

/**
 * OCR 识别结果。markdown 为正文文本（含图片占位符），images 为占位符 key 到临时图片 URL 的映射。
 */
public record OcrResult(String markdown, Map<String, String> images) {

    public OcrResult {
        Objects.requireNonNull(markdown, "markdown");
        Objects.requireNonNull(images, "images");
    }
}
