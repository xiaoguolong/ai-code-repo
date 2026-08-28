package com.aicode.demo.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 聊天接口入参。message 上限与上下文窗口预留对齐，防止单请求撑满。
 */
public record ChatRequest(
        @Size(max = 64) String sessionId,
        @NotBlank @Size(max = 8000) String message
) {
}
