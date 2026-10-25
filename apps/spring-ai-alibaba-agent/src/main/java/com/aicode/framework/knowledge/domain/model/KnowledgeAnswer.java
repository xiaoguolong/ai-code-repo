package com.aicode.framework.knowledge.domain.model;

import com.aicode.core.domain.model.TokenUsage;

import java.util.List;

/**
 * 知识库问答结果（Week 25）。
 *
 * @param knowledgeBaseId   知识库 ID
 * @param knowledgeBaseName 知识库名称
 * @param question          问题
 * @param answer            答案（模型生成或降级摘录）
 * @param citations         引用片段（按相似度倒序）
 * @param hitCount          命中切片数
 * @param degraded          是否降级（模型不可用，只返回摘录）
 * @param usage             token 用量
 * @param model             模型名
 * @param steps             轨迹
 */
public record KnowledgeAnswer(
        long knowledgeBaseId,
        String knowledgeBaseName,
        String question,
        String answer,
        List<KnowledgeCitation> citations,
        int hitCount,
        boolean degraded,
        TokenUsage usage,
        String model,
        List<KnowledgeStep> steps
) {

    public KnowledgeAnswer {
        knowledgeBaseName = knowledgeBaseName == null ? "" : knowledgeBaseName;
        question = question == null ? "" : question;
        answer = answer == null ? "" : answer;
        citations = citations == null ? List.of() : List.copyOf(citations);
        usage = usage == null ? TokenUsage.unknown() : usage;
        model = model == null ? "" : model;
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}
