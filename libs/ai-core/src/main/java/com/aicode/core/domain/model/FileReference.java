package com.aicode.core.domain.model;

import java.util.Objects;

/**
 * 已上传文件的引用。fileId 对外暴露，accessUrl 为拼接好的可访问路径。
 */
public record FileReference(
        String fileId,
        String originalName,
        String contentType,
        long sizeBytes,
        StorageType storageType,
        String accessUrl
) {

    public FileReference {
        Objects.requireNonNull(fileId, "fileId");
        Objects.requireNonNull(originalName, "originalName");
        Objects.requireNonNull(storageType, "storageType");
        Objects.requireNonNull(accessUrl, "accessUrl");
    }
}
