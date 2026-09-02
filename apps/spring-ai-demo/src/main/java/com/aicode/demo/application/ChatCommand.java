package com.aicode.demo.application;

import com.aicode.demo.domain.model.OutputFormat;

/**
 * 发起聊天的用例入参。sessionId 可空，由用例生成。
 *
 * @param template     模板名，空则用默认 system
 * @param outputFormat TEXT 或 JSON
 */
public record ChatCommand(String sessionId, String message, String template, OutputFormat outputFormat) {

    /**
     * 第1周兼容：默认模板与文本输出。
     */
    public ChatCommand(String sessionId, String message) {
        this(sessionId, message, null, OutputFormat.TEXT);
    }
}
