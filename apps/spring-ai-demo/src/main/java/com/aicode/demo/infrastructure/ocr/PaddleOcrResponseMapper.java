package com.aicode.demo.infrastructure.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 将千帆 PaddleOCR-VL 的 OCR JSON 响应映射为识别文本。
 *
 * <p>厂商返回范本（已脱敏）：</p>
 * <pre>{@code
 * {
 *   "id": "as-****",
 *   "result": {
 *     "layoutParsingResults": [
 *       {
 *         "markdown": {
 *           "text": "# 标题\n\n正文...",
 *           "images": { "imgs/img_xxx.jpg": "http://..." }
 *         }
 *       }
 *     ],
 *     "dataInfo": { "type": "pdf", "numPages": 1 }
 *   },
 *   "usage": { "numPages": 1 }
 * }
 * }</pre>
 *
 * <p>解析规则：遍历 {@code result.layoutParsingResults}（数组，图片为 1 个元素、PDF 每页 1 个），
 * 逐页拼接 {@code markdown.text}。</p>
 *
 * <p>{@code markdown.text} 中的 {@code <div><img src="imgs/...">} 为图片占位符，真实图片 URL 在
 * {@code markdown.images}。图片占位暂不处理，待文件存储功能上线后用上传标识替换。</p>
 */
public class PaddleOcrResponseMapper {

    private final ObjectMapper objectMapper;

    public PaddleOcrResponseMapper() {
        this(new ObjectMapper());
    }

    public PaddleOcrResponseMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param json 厂商响应体
     * @return 所有页拼接后的 markdown 文本
     * @throws IllegalStateException 响应缺少 result.layoutParsingResults 或 JSON 非法
     */
    public String map(String json) {
        try {
            JsonNode pages = objectMapper.readTree(json).path("result").path("layoutParsingResults");
            if (!pages.isArray() || pages.isEmpty()) {
                throw new IllegalStateException("OCR response has no result.layoutParsingResults");
            }
            StringBuilder builder = new StringBuilder();
            for (JsonNode page : pages) {
                JsonNode text = page.path("markdown").path("text");
                if (text.isTextual()) {
                    builder.append(text.asText()).append('\n');
                }
            }
            return builder.toString().trim();
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("OCR response parse failed", ex);
        }
    }
}
