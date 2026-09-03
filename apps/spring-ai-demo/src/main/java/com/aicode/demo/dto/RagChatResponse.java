package com.aicode.demo.dto;

import com.aicode.demo.application.KnowledgeAnswer;
import com.aicode.demo.domain.model.VectorSearchHit;

import java.util.List;

/**
 * RAG 问答成功体。
 */
public record RagChatResponse(String answer, List<RagSourceDto> sources) {

    /**
     * 从用例出参转换。
     */
    public static RagChatResponse from(KnowledgeAnswer answer) {
        List<RagSourceDto> sources = answer.sources().stream()
                .map(RagChatResponse::toSource)
                .toList();
        return new RagChatResponse(answer.answer(), sources);
    }

    private static RagSourceDto toSource(VectorSearchHit hit) {
        return new RagSourceDto(hit.chunkIndex(), hit.content(), hit.score());
    }
}
