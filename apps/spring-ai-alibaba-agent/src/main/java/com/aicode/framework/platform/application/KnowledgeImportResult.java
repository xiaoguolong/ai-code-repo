package com.aicode.framework.platform.application;

import com.aicode.framework.platform.domain.model.KnowledgeBinding;

import java.util.List;

/**
 * 知识库导入结果（Week 25）。
 *
 * @param tenantKey        租户
 * @param enterpriseUserId 企业本地用户 ID
 * @param imported         新增条数
 * @param updated          更新条数
 * @param bindings         导入后的租户绑定
 */
public record KnowledgeImportResult(
        String tenantKey,
        long enterpriseUserId,
        int imported,
        int updated,
        List<KnowledgeBinding> bindings
) {

    public KnowledgeImportResult {
        bindings = bindings == null ? List.of() : List.copyOf(bindings);
    }
}
