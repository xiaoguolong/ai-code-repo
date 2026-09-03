package com.aicode.demo.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * OCR 识别入参。file 为图片/PDF 的 URL 或 Base64，fileType 可空（URL 由服务端推断）。
 */
public record OcrRecognizeRequest(
        @NotBlank(message = "file 不能为空") String file,
        String fileType
) {
}
