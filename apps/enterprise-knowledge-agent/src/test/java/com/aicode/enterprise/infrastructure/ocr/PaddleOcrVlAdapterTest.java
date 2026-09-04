package com.aicode.enterprise.infrastructure.ocr;

import com.aicode.enterprise.domain.model.OcrFileType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 千帆 OCR 适配器请求体组装单元测试。
 */
class PaddleOcrVlAdapterTest {

    @Test
    void includesQianfanSpecificParams() {
        Map<String, Object> payload = PaddleOcrVlAdapter.buildPayload("paddleocr-vl-0.9b", "file", OcrFileType.PDF);

        assertThat(payload)
                .containsEntry("model", "paddleocr-vl-0.9b")
                .containsEntry("file", "file")
                .containsEntry("fileType", 0)
                .containsEntry("visualize", true)
                .containsEntry("useLayoutDetection", true)
                .containsEntry("useChartRecognition", true);
    }

    @Test
    void omitsFileTypeWhenNull() {
        Map<String, Object> payload = PaddleOcrVlAdapter.buildPayload("m", "f", null);

        assertThat(payload).doesNotContainKey("fileType");
    }
}
