package com.aicode.enterprise.application;

/**
 * 知识库问答用例入参。topK 可空，缺省用配置默认值。
 *
 * @param knowledgeBaseId 限定知识库
 * @param userId          提问用户
 * @param sessionId       会话标识，空则生成
 * @param question        问题
 * @param topK            检索条数
 */
public record KnowledgeQuestion(Long knowledgeBaseId, Long userId, String sessionId, String question, Integer topK) {
}
