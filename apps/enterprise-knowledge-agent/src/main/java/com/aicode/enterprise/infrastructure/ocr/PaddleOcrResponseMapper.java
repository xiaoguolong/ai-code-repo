package com.aicode.enterprise.infrastructure.ocr;

import com.aicode.enterprise.domain.model.OcrResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将千帆 PaddleOCR-VL 的 OCR JSON 响应映射为 markdown 文本与图片映射。
 *
 * <p>厂商返回范本（已脱敏）：</p>
 * <pre>{@code
 * {
 *   "result": {
 *     "layoutParsingResults": [
 *       {
 *         "markdown": {
 *           "text": "# 标题\n\n正文...",
 *           "images": { "imgs/img_xxx.jpg": "http://..." }
 *         }
 *       }
 *     ]
 *   }
 * }
 * }</pre>
 *
 * <p>解析规则：遍历 {@code result.layoutParsingResults}（数组，图片为 1 个元素、PDF 每页 1 个），
 * 逐页拼接 {@code markdown.text}，合并每页 {@code markdown.images}（占位符 key → 临时图片 URL）。</p>
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
     * @return 所有页拼接后的 markdown 文本与图片映射
     * @throws IllegalStateException 响应缺少 result.layoutParsingResults 或 JSON 非法
     */
    public OcrResult map(String json) {
        try {
            JsonNode pages = objectMapper.readTree(json).path("result").path("layoutParsingResults");
            if (!pages.isArray() || pages.isEmpty()) {
                throw new IllegalStateException("OCR response has no result.layoutParsingResults");
            }
            StringBuilder builder = new StringBuilder();
            Map<String, String> images = new LinkedHashMap<>();
            for (JsonNode page : pages) {
                JsonNode markdown = page.path("markdown");
                JsonNode text = markdown.path("text");
                if (text.isTextual()) {
                    builder.append(text.asText()).append('\n');
                }
                JsonNode imageNode = markdown.path("images");
                if (imageNode.isObject()) {
                    imageNode.fields().forEachRemaining(entry ->
                            images.put(entry.getKey(), entry.getValue().asText()));
                }
            }
            return new OcrResult(builder.toString().trim(), Map.copyOf(images));
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("OCR response parse failed", ex);
        }
    }
}
