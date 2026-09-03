package com.aicode.demo.application;

import com.aicode.demo.domain.model.VectorSearchHit;

import java.util.List;

/**
 * RAG 问答用例出参。sources 为本次注入上下文的检索片段，可能为空。
 */
public record KnowledgeAnswer(String answer, List<VectorSearchHit> sources) {
}
