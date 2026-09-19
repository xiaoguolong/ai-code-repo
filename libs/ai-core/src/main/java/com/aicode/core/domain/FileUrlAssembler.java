package com.aicode.core.domain;

import java.util.Objects;

/**
 * 把 fileId 组装成对外可访问路径（域名 + fileId）。供页面 img/下载使用，避免散落 URL 拼接。
 */
public final class FileUrlAssembler {

    private final String baseUrl;

    public FileUrlAssembler(String baseUrl) {
        this.baseUrl = stripTrailingSlash(Objects.requireNonNull(baseUrl, "baseUrl"));
    }

    /**
     * @param fileId 文件标识
     * @return 形如 {baseUrl}/api/v1/files/{fileId} 的访问路径
     */
    public String url(String fileId) {
        return baseUrl + "/api/v1/files/" + fileId;
    }

    private static String stripTrailingSlash(String value) {
        String trimmed = value.strip();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
