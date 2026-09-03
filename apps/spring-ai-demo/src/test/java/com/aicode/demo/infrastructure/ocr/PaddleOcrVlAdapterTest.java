package com.aicode.demo.infrastructure.ocr;

import com.aicode.demo.domain.model.OcrFileType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 千帆 PaddleOCR-VL 适配器：请求体组装。
 */
class PaddleOcrVlAdapterTest {

    @Test
    void shouldBuildPayload_withFileType() {
        Map<String, Object> payload = PaddleOcrVlAdapter.buildPayload(
                "paddleocr-vl-0.9b", "https://example.com/a.jpg", OcrFileType.IMAGE
        );

        assertThat(payload.get("model")).isEqualTo("paddleocr-vl-0.9b");
        assertThat(payload.get("file")).isEqualTo("https://example.com/a.jpg");
        assertThat(payload.get("fileType")).isEqualTo(1);
    }

    @Test
    void shouldBuildPayload_withPdfFileType() {
        Map<String, Object> payload = PaddleOcrVlAdapter.buildPayload(
                "paddleocr-vl-0.9b", "base64data", OcrFileType.PDF
        );

        assertThat(payload.get("fileType")).isEqualTo(0);
    }

    @Test
    void shouldOmitFileType_whenNull() {
        Map<String, Object> payload = PaddleOcrVlAdapter.buildPayload(
                "paddleocr-vl-0.9b", "https://example.com/a.pdf", null
        );

        assertThat(payload).doesNotContainKey("fileType");
    }
}
