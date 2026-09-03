package com.aicode.demo.application;

/**
 * RAG 问答用例入参。topK 可空，缺省时用配置默认值。
 */
public record KnowledgeQuestion(String question, Integer topK) {
}
