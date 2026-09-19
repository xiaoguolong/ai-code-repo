package com.aicode.enterprise.dto;

import com.aicode.enterprise.application.KnowledgeAnswer;
import com.aicode.core.domain.model.VectorSearchHit;

import java.util.List;

/**
 * 知识库问答成功体。
 */
public record RagChatResponse(String sessionId, String answer, List<RagSourceDto> sources) {

    /**
     * 从用例出参转换。
     */
    public static RagChatResponse from(KnowledgeAnswer answer) {
        List<RagSourceDto> sources = answer.sources().stream()
                .map(RagChatResponse::toSource)
                .toList();
        return new RagChatResponse(answer.sessionId(), answer.answer(), sources);
    }

    private static RagSourceDto toSource(VectorSearchHit hit) {
        return new RagSourceDto(hit.chunkIndex(), hit.content(), hit.score());
    }
}
