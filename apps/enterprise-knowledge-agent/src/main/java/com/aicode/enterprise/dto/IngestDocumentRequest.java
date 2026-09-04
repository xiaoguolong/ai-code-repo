package com.aicode.enterprise.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 文档入库入参。正文为纯文本，上限避免超大文件直接塞爆内存。
 */
public record IngestDocumentRequest(
        @NotBlank(message = "name 不能为空")
        @Size(max = 255, message = "name 长度不能超过 255") String name,
        @NotBlank(message = "content 不能为空")
        @Size(max = 100000, message = "content 长度不能超过 100000") String content
) {
}
