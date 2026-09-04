package com.aicode.enterprise.domain.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * 已存储文件的二进制内容与类型，供下载/预览返回。
 */
public record FileContent(String contentType, byte[] bytes) {

    public FileContent {
        Objects.requireNonNull(bytes, "bytes");
    }

    /**
     * @return 内容字节的防御性副本，避免外部修改内部数组
     */
    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FileContent that)) {
            return false;
        }
        return Objects.equals(contentType, that.contentType) && Arrays.equals(bytes, that.bytes);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hashCode(contentType) + Arrays.hashCode(bytes);
    }
}
