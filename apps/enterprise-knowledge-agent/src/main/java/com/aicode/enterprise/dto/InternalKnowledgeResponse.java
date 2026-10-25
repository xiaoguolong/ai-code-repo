package com.aicode.enterprise.dto;

import com.aicode.enterprise.application.InternalKnowledgeBaseView;
import com.aicode.enterprise.application.InternalKnowledgeCatalog;
import com.aicode.enterprise.application.InternalKnowledgeHit;
import com.aicode.enterprise.application.InternalKnowledgeResult;

import java.util.List;

/**
 * 内部接口响应（Week 25）。
 *
 * <p>扁平结构：清单返回 {@code bases}，检索返回 {@code knowledgeBase + hits}。</p>
 */
public record InternalKnowledgeResponse(
        Long enterpriseUserId,
        KnowledgeBaseItem knowledgeBase,
        List<BaseItem> bases,
        List<HitItem> hits
) {

    /**
     * 清单响应。
     *
     * @param catalog 清单
     * @return 响应体
     */
    public static InternalKnowledgeResponse catalog(InternalKnowledgeCatalog catalog) {
        return new InternalKnowledgeResponse(
                catalog.enterpriseUserId(), null,
                catalog.bases().stream().map(BaseItem::from).toList(), null);
    }

    /**
     * 检索响应。
     *
     * @param result 检索结果
     * @return 响应体
     */
    public static InternalKnowledgeResponse search(InternalKnowledgeResult result) {
        return new InternalKnowledgeResponse(
                result.enterpriseUserId(),
                KnowledgeBaseItem.from(result.knowledgeBase()),
                null,
                result.hits().stream().map(HitItem::from).toList());
    }

    /**
     * 知识库条目。
     *
     * @param knowledgeBaseId ID
     * @param name            名称
     * @param description     描述
     * @param documentCount   文档数
     */
    public record BaseItem(long knowledgeBaseId, String name, String description, int documentCount) {

        static BaseItem from(InternalKnowledgeBaseView view) {
            return new BaseItem(view.knowledgeBaseId(), view.name(), view.description(), view.documentCount());
        }
    }

    /**
     * 检索命中的知识库。
     *
     * @param knowledgeBaseId ID
     * @param name            名称
     * @param description     描述
     */
    public record KnowledgeBaseItem(long knowledgeBaseId, String name, String description) {

        static KnowledgeBaseItem from(InternalKnowledgeBaseView view) {
            return new KnowledgeBaseItem(view.knowledgeBaseId(), view.name(), view.description());
        }
    }

    /**
     * 命中切片。
     *
     * @param documentId   文档 ID
     * @param documentName 文档名
     * @param chunkIndex   切片号
     * @param content      正文
     * @param score        相似度
     */
    public record HitItem(
            String documentId,
            String documentName,
            int chunkIndex,
            String content,
            double score
    ) {

        static HitItem from(InternalKnowledgeHit hit) {
            return new HitItem(
                    hit.documentId(), hit.documentName(), hit.chunkIndex(), hit.content(), hit.score());
        }
    }
}
