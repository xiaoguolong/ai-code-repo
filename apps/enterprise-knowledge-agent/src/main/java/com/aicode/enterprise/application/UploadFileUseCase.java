package com.aicode.enterprise.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.core.domain.model.FileReference;
import com.aicode.core.domain.port.FileStoragePort;
import org.springframework.stereotype.Service;

/**
 * 文件上传用例：校验非空后委派文件存储端口。
 */
@Service
public class UploadFileUseCase {

    private final FileStoragePort fileStoragePort;

    public UploadFileUseCase(FileStoragePort fileStoragePort) {
        this.fileStoragePort = fileStoragePort;
    }

    /**
     * @throws InvalidChatRequestException 文件为空
     */
    public FileReference upload(Long userId, String originalName, String contentType, byte[] content) {
        if (content == null || content.length == 0) {
            throw new InvalidChatRequestException("file must not be empty");
        }
        return fileStoragePort.store(userId, originalName, contentType, content);
    }
}
