package com.aicode.enterprise.infrastructure.storage;

import com.aicode.enterprise.domain.FileUrlAssembler;
import com.aicode.enterprise.domain.exception.FileStorageException;
import com.aicode.enterprise.domain.exception.NotFoundException;
import com.aicode.enterprise.domain.model.FileContent;
import com.aicode.enterprise.domain.model.FileReference;
import com.aicode.enterprise.domain.model.StorageType;
import com.aicode.enterprise.domain.port.FileStoragePort;
import com.aicode.enterprise.infrastructure.config.FileProperties;
import com.aicode.enterprise.infrastructure.persistence.entity.FileRecordEntity;
import com.aicode.enterprise.infrastructure.persistence.mapper.FileRecordMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 本地磁盘文件存储适配器。文件落在 {@code file.storage-dir}，元数据落 file_record。
 * OSS/OBS 通过 FileStoragePort 接口预留，后续新增实现类即可替换。
 */
@Component
public class LocalFileStorageAdapter implements FileStoragePort {

    private final FileRecordMapper fileRecordMapper;
    private final FileUrlAssembler urlAssembler;
    private final Path storageDir;
    private final Clock clock;

    public LocalFileStorageAdapter(
            FileRecordMapper fileRecordMapper,
            FileUrlAssembler urlAssembler,
            FileProperties fileProperties,
            Clock clock
    ) {
        this.fileRecordMapper = fileRecordMapper;
        this.urlAssembler = urlAssembler;
        this.storageDir = Path.of(fileProperties.resolvedStorageDir());
        this.clock = clock;
    }

    @Override
    public FileReference store(Long userId, String originalName, String contentType, byte[] content) {
        String fileId = UUID.randomUUID().toString();
        try {
            Files.createDirectories(storageDir);
            Files.write(storageDir.resolve(fileId), content);
        } catch (IOException ex) {
            throw new FileStorageException("failed to write local file", ex);
        }
        fileRecordMapper.insert(new FileRecordEntity(
                fileId,
                originalName,
                contentType,
                content.length,
                StorageType.LOCAL.value(),
                fileId,
                userId,
                clock.instant()
        ));
        return new FileReference(
                fileId,
                originalName,
                contentType,
                content.length,
                StorageType.LOCAL,
                urlAssembler.url(fileId)
        );
    }

    @Override
    public FileContent load(String fileId) {
        List<FileRecordEntity> found = fileRecordMapper.listByMap(false, Map.of("fileId", fileId));
        if (found.isEmpty()) {
            throw new NotFoundException("file not found: " + fileId);
        }
        FileRecordEntity record = found.get(0);
        try {
            byte[] bytes = Files.readAllBytes(storageDir.resolve(record.getStorageKey()));
            return new FileContent(record.getContentType(), bytes);
        } catch (IOException ex) {
            throw new FileStorageException("failed to read local file", ex);
        }
    }
}
