package com.aicode.enterprise.application;

/**
 * 更新知识库用例入参。
 *
 * @param userId          归属用户
 * @param knowledgeBaseId 知识库主键
 * @param name            新名称
 * @param description     新描述
 */
public record UpdateKnowledgeBaseCommand(Long userId, Long knowledgeBaseId, String name, String description) {
}
