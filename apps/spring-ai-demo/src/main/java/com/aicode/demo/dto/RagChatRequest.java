package com.aicode.demo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * RAG 问答入参。topK 可空，缺省用配置默认。
 */
public record RagChatRequest(
        @NotBlank(message = "question 不能为空")
        @Size(max = 8000, message = "question 长度不能超过 8000") String question,
        @Min(value = 1, message = "topK 最小为 1")
        @Max(value = 50, message = "topK 最大为 50") Integer topK
) {
}
