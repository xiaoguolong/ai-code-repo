package com.aicode.enterprise.domain.model;

/**
 * 带版本的系统提示。版本随审计一起记录，便于对比 Prompt 迭代效果。
 */
public record PromptTemplate(String version, String content) {
}
