package com.aicode.enterprise.dto;

/**
 * OCR 识别成功体。text 为清理图片占位符后的 markdown。
 */
public record OcrRecognizeResponse(String text) {
}
