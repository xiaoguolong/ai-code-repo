package com.aicode.enterprise.domain.model;

/**
 * 模型适配器的统一出参。屏蔽厂商 JSON，领域只认本类型。
 */
public record ChatResult(String content, TokenUsage usage, String model) {
}
