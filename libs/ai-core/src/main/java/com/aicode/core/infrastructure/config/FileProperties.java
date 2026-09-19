package com.aicode.core.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文件上传服务配置。
 *
 * @param storageDir     本地存储根目录，对应 FILE_STORAGE_DIR
 * @param publicBaseUrl  对外访问域名前缀，对应 FILE_PUBLIC_BASE_URL
 */
@ConfigurationProperties(prefix = "file")
public record FileProperties(String storageDir, String publicBaseUrl) {

    /**
     * @return 有效存储目录，缺省 ./data/files
     */
    public String resolvedStorageDir() {
        return storageDir == null || storageDir.isBlank() ? "./data/files" : storageDir;
    }

    /**
     * @return 有效对外域名，缺省 http://localhost:8081
     */
    public String resolvedPublicBaseUrl() {
        return publicBaseUrl == null || publicBaseUrl.isBlank() ? "http://localhost:8081" : publicBaseUrl;
    }
}
