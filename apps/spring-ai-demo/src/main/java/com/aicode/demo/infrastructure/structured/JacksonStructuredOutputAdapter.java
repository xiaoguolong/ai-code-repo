package com.aicode.demo.infrastructure.structured;

import com.aicode.demo.domain.exception.StructuredOutputException;
import com.aicode.demo.domain.port.StructuredOutputPort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 用 Jackson 解析模型返回的 JSON 文本。会剥离常见 markdown 围栏。
 */
@Component
public class JacksonStructuredOutputAdapter implements StructuredOutputPort {

    private final ObjectMapper objectMapper;

    public JacksonStructuredOutputAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> parseObject(String json) throws StructuredOutputException {
        if (json == null) {
            throw new StructuredOutputException("model returned null for JSON output");
        }
        String stripped = stripFences(json.trim());
        try {
            Map<String, Object> map = objectMapper.readValue(stripped, new TypeReference<>() {
            });
            if (map == null) {
                throw new StructuredOutputException("model returned JSON null");
            }
            return map;
        } catch (Exception ex) {
            throw new StructuredOutputException("model output is not valid JSON object: " + ex.getMessage());
        }
    }

    private String stripFences(String text) {
        if (text.startsWith("```json") && text.endsWith("```")) {
            return text.substring(7, text.length() - 3).trim();
        }
        if (text.startsWith("```") && text.endsWith("```")) {
            return text.substring(3, text.length() - 3).trim();
        }
        return text;
    }
}
