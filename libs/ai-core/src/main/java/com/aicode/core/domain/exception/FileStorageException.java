package com.aicode.core.domain.exception;

/**
 * 文件存储失败（读写磁盘、上传对象存储失败）。映射为 HTTP 502。
 */
public class FileStorageException extends RuntimeException {

    public FileStorageException(String message) {
        super(message);
    }

    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
