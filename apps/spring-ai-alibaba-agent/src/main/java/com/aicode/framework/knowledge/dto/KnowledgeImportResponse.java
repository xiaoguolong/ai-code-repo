package com.aicode.framework.knowledge.dto;

import com.aicode.framework.platform.application.KnowledgeImportResult;

import java.util.List;

/**
 * 知识库导入响应（Week 25）。
 *
 * @param tenantKey        租户
 * @param enterpriseUserId 企业本地用户 ID
 * @param imported         新增条数
 * @param updated          更新条数
 * @param items            导入后的绑定
 */
public record KnowledgeImportResponse(
        String tenantKey,
        long enterpriseUserId,
        int imported,
        int updated,
        List<KnowledgeBindingResponse> items
) {

    /**
     * 由用例结果转换。
     *
     * @param result 导入结果
     * @return 响应体
     */
    public static KnowledgeImportResponse from(KnowledgeImportResult result) {
        return new KnowledgeImportResponse(
                result.tenantKey(), result.enterpriseUserId(), result.imported(), result.updated(),
                result.bindings().stream().map(KnowledgeBindingResponse::from).toList());
    }
}
