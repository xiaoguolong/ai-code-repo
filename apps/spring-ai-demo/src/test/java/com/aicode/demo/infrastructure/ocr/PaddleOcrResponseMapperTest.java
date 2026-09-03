package com.aicode.demo.infrastructure.ocr;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 千帆 PaddleOCR-VL 响应解析器：遍历 result.layoutParsingResults 拼接每页 markdown.text。
 */
class PaddleOcrResponseMapperTest {

    private final PaddleOcrResponseMapper mapper = new PaddleOcrResponseMapper();

    @Test
    void shouldExtractMarkdownText_fromSinglePage() {
        String json = "{\"id\":\"1\",\"result\":{\"layoutParsingResults\":["
                + "{\"markdown\":{\"text\":\"识别出的正文文本\"}}"
                + "]}}";

        assertThat(mapper.map(json)).isEqualTo("识别出的正文文本");
    }

    @Test
    void shouldConcatenateMarkdownText_acrossAllPages() {
        String json = "{\"id\":\"1\",\"result\":{\"layoutParsingResults\":["
                + "{\"markdown\":{\"text\":\"第一页文本\"}},"
                + "{\"markdown\":{\"text\":\"第二页文本\"}}"
                + "]}}";

        assertThat(mapper.map(json)).contains("第一页文本", "第二页文本");
    }

    @Test
    void shouldThrow_whenResponseMissingLayoutParsingResults() {
        assertThatThrownBy(() -> mapper.map("{\"id\":\"1\"}"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldThrow_whenJsonInvalid() {
        assertThatThrownBy(() -> mapper.map("not-json"))
                .isInstanceOf(IllegalStateException.class);
    }
}
