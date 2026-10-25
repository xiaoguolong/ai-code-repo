package com.aicode.framework.knowledge.dto;

import com.aicode.framework.platform.domain.model.KnowledgeBinding;

import java.util.List;

/**
 * 知识库绑定列表响应（Week 25）。
 *
 * @param total 条数
 * @param items 绑定列表
 */
public record KnowledgeBindingListResponse(int total, List<KnowledgeBindingResponse> items) {

    /**
     * 由领域模型列表转换。
     *
     * @param bindings 绑定
     * @return 响应体
     */
    public static KnowledgeBindingListResponse from(List<KnowledgeBinding> bindings) {
        List<KnowledgeBindingResponse> items = bindings == null
                ? List.of()
                : bindings.stream().map(KnowledgeBindingResponse::from).toList();
        return new KnowledgeBindingListResponse(items.size(), items);
    }
}
