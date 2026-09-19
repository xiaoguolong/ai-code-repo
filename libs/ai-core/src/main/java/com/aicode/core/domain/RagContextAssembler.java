package com.aicode.core.domain;

import com.aicode.core.domain.model.VectorSearchHit;

import java.util.List;

/**
 * 组装 RAG 问答的用户消息：检索片段 + 问题。用户提问始终作为 user 内容，不并入系统提示。
 */
public final class RagContextAssembler {

    private RagContextAssembler() {
    }

    /**
     * 把检索到的片段与问题拼成一条 user 消息正文，供模型回答。
     *
     * @param hits     相似度检索结果，可能为空
     * @param question 用户问题，非空
     * @return 形如「参考资料：...\n\n问题：...」的文本
     */
    public static String buildUserMessage(List<VectorSearchHit> hits, String question) {
        StringBuilder builder = new StringBuilder();
        if (hits.isEmpty()) {
            builder.append("参考资料：（无）").append("\n\n");
        } else {
            builder.append("参考资料：").append("\n");
            for (VectorSearchHit hit : hits) {
                builder.append("[片段 ").append(hit.chunkIndex() + 1).append("] ")
                        .append(hit.content()).append("\n\n");
            }
        }
        builder.append("问题：").append(question);
        return builder.toString();
    }
}
