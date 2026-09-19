package com.aicode.enterprise.application;

import com.aicode.core.domain.model.VectorSearchHit;

import java.util.List;

/**
 * 知识库问答用例出参。sources 为本次注入上下文的检索片段，可能为空。
 *
 * @param sessionId 会话标识
 * @param answer    模型答案
 * @param sources   检索来源
 */
public record KnowledgeAnswer(String sessionId, String answer, List<VectorSearchHit> sources) {
}
