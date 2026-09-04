package com.aicode.enterprise.infrastructure.ocr;

import com.aicode.enterprise.domain.model.OcrResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 千帆 OCR 响应映射单元测试：解析 markdown 文本与图片映射。
 */
class PaddleOcrResponseMapperTest {

    private final PaddleOcrResponseMapper mapper = new PaddleOcrResponseMapper();

    @Test
    void mapsMarkdownAndImages() {
        String json = """
                {
                  "result": {
                    "layoutParsingResults": [
                      {
                        "markdown": {
                          "text": "# 标题\\n\\n<div><img src=\\"imgs/img_1.jpg\\"></div>",
                          "images": { "imgs/img_1.jpg": "http://tmp/img_1.jpg" }
                        }
                      }
                    ]
                  }
                }
                """;

        OcrResult result = mapper.map(json);

        assertThat(result.markdown()).contains("# 标题").contains("<img src=\"imgs/img_1.jpg\">");
        assertThat(result.images()).containsEntry("imgs/img_1.jpg", "http://tmp/img_1.jpg");
    }

    @Test
    void mapsEmptyImages() {
        String json = """
                {
                  "result": {
                    "layoutParsingResults": [
                      { "markdown": { "text": "纯文本" } }
                    ]
                  }
                }
                """;

        OcrResult result = mapper.map(json);

        assertThat(result.markdown()).isEqualTo("纯文本");
        assertThat(result.images()).isEmpty();
    }
}
