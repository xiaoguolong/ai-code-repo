package com.aicode.enterprise.application;

import com.aicode.core.domain.model.FileContent;
import com.aicode.core.domain.port.FileStoragePort;
import org.springframework.stereotype.Service;

/**
 * 取回文件内容用例。
 */
@Service
public class GetFileUseCase {

    private final FileStoragePort fileStoragePort;

    public GetFileUseCase(FileStoragePort fileStoragePort) {
        this.fileStoragePort = fileStoragePort;
    }

    /**
     * @return 文件内容与类型
     */
    public FileContent load(String fileId) {
        return fileStoragePort.load(fileId);
    }
}
