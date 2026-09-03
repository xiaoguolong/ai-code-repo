package com.aicode.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 聊天接口入参。message 上限与上下文窗口预留对齐，防止单请求撑满。
 */
public record ChatRequest(
        @Size(max = 64, message = "sessionId 长度不能超过 64") String sessionId,
        @NotBlank(message = "message 不能为空")
        @Size(max = 8000, message = "message 长度不能超过 8000") String message,
        @Size(max = 32, message = "template 长度不能超过 32") String template,
        @Size(max = 8, message = "responseFormat 长度不能超过 8") String responseFormat
) {
}
