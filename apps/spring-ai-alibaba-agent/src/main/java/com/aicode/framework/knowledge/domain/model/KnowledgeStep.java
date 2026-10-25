package com.aicode.framework.knowledge.domain.model;

/**
 * 知识库问答的一步轨迹（Week 25）。
 *
 * @param stepNo  序号
 * @param step    步骤名
 * @param summary 摘要（不含正文）
 */
public record KnowledgeStep(int stepNo, String step, String summary) {
}
