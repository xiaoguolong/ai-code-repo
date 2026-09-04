package com.aicode.enterprise.domain.model;

/**
 * 可列出的 Prompt 模板元数据，不含全文。
 */
public record PromptDescriptor(String name, String version) {
}
