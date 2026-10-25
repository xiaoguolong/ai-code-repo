package com.aicode.enterprise.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 内部检索请求（Week 25，仅平台调用）。
 *
 * @param enterpriseUserId 知识库归属的本地用户 ID（平台绑定表里记录）
 * @param knowledgeBaseId  知识库 ID
 * @param query            检索问题
 * @param topK             返回条数（可空，缺省用配置默认）
 */
public record InternalSearchRequest(
        @NotNull(message = "enterpriseUserId 不能为空") Long enterpriseUserId,
        @NotNull(message = "knowledgeBaseId 不能为空") @Min(value = 1, message = "knowledgeBaseId 必须为正") Long knowledgeBaseId,
        @NotBlank(message = "query 不能为空") @Size(max = 8000, message = "query 长度不能超过 8000") String query,
        @Min(value = 1, message = "topK 最小为 1") Integer topK
) {
}
