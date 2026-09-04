package com.aicode.enterprise.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建知识库入参。
 */
public record CreateKnowledgeBaseRequest(
        @NotBlank(message = "name 不能为空")
        @Size(max = 255, message = "name 长度不能超过 255") String name,
        @Size(max = 1000, message = "description 长度不能超过 1000") String description
) {
}
