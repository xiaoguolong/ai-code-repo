package com.aicode.enterprise.dto;

import com.aicode.core.domain.model.FileReference;

/**
 * 文件上传成功体。url 为拼接好的访问路径。
 */
public record FileUploadResponse(String fileId, String url, String originalName, String contentType, long sizeBytes) {

    /**
     * 从文件引用转换。
     */
    public static FileUploadResponse from(FileReference reference) {
        return new FileUploadResponse(
                reference.fileId(),
                reference.accessUrl(),
                reference.originalName(),
                reference.contentType(),
                reference.sizeBytes()
        );
    }
}
