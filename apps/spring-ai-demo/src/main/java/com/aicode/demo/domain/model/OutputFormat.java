package com.aicode.demo.domain.model;

import com.aicode.demo.domain.exception.InvalidChatRequestException;

/**
 * 模型输出形态。JSON 时必须能解析为对象，否则 422。
 */
public enum OutputFormat {

    /** 自由文本。 */
    TEXT,

    /** 单 JSON 对象。 */
    JSON;

    /**
     * 解析请求中的 responseFormat。空则 TEXT。
     *
     * @throws InvalidChatRequestException 非法取值
     */
    public static OutputFormat from(String raw) {
        if (raw == null || raw.isBlank()) {
            return TEXT;
        }
        try {
            return OutputFormat.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidChatRequestException("responseFormat must be TEXT or JSON");
        }
    }
}
