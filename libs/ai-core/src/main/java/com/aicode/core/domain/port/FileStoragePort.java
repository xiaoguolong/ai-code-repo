package com.aicode.core.domain.port;

import com.aicode.core.domain.exception.FileStorageException;
import com.aicode.core.domain.exception.NotFoundException;
import com.aicode.core.domain.model.FileContent;
import com.aicode.core.domain.model.FileReference;

/**
 * 出站端口：文件存储。本地/OSS/OBS 可替换，实现类负责后端协议与元数据落库。
 */
public interface FileStoragePort {

    /**
     * 存储文件字节并持久化元数据，返回含访问路径的文件引用。
     *
     * @param userId      上传者主键
     * @param originalName 原始文件名
     * @param contentType  内容类型，可为空
     * @param content      文件字节
     * @return 文件引用（fileId + 访问路径）
     * @throws FileStorageException 写入后端失败
     */
    FileReference store(Long userId, String originalName, String contentType, byte[] content);

    /**
     * 按 fileId 读取文件内容。
     *
     * @return 文件内容与类型
     * @throws NotFoundException fileId 不存在
     */
    FileContent load(String fileId);
}
