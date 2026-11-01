package com.aicode.enterprise.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.core.domain.exception.NotFoundException;
import com.aicode.core.domain.model.FileReference;
import com.aicode.core.domain.port.FileStoragePort;
import com.aicode.enterprise.domain.exception.ForbiddenException;
import com.aicode.enterprise.domain.exception.IngestQueueFullException;
import com.aicode.enterprise.domain.model.IngestJob;
import com.aicode.enterprise.domain.model.KnowledgeBase;
import com.aicode.enterprise.domain.port.IngestJobLauncher;
import com.aicode.enterprise.domain.port.IngestJobPort;
import com.aicode.enterprise.domain.port.KnowledgeBasePort;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * 建入库作业用例（Week 32）：校验归属 → sha256 幂等 → 存文件 → 建 Job → 异步提交。
 */
@Service
public class CreateIngestJobUseCase {

    private final KnowledgeBasePort knowledgeBasePort;
    private final IngestJobPort ingestJobPort;
    private final FileStoragePort fileStoragePort;
    private final IngestJobLauncher launcher;
    private final Clock clock;

    public CreateIngestJobUseCase(
            KnowledgeBasePort knowledgeBasePort,
            IngestJobPort ingestJobPort,
            FileStoragePort fileStoragePort,
            IngestJobLauncher launcher,
            Clock clock
    ) {
        this.knowledgeBasePort = knowledgeBasePort;
        this.ingestJobPort = ingestJobPort;
        this.fileStoragePort = fileStoragePort;
        this.launcher = launcher;
        this.clock = clock;
    }

    /**
     * @param kbId        知识库
     * @param userId      当前用户
     * @param fileName    原始文件名
     * @param contentType 内容类型（可空）
     * @param bytes       文件字节
     * @return 作业与是否幂等命中
     * @throws InvalidChatRequestException 文件为空/超过限制
     * @throws NotFoundException           知识库不存在
     * @throws ForbiddenException          知识库不属于当前用户
     */
    public CreateIngestJobOutcome create(long kbId, long userId, String fileName, String contentType, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new InvalidChatRequestException("file must not be empty");
        }
        String name = fileName == null ? "" : fileName.trim();
        if (name.isEmpty()) {
            throw new InvalidChatRequestException("file name must not be blank");
        }
        KnowledgeBase knowledgeBase = knowledgeBasePort.findById(kbId)
                .orElseThrow(() -> new NotFoundException("knowledge base not found"));
        if (knowledgeBase.userId() != userId) {
            throw new ForbiddenException("access denied");
        }
        String hash = sha256(bytes);
        Optional<IngestJob> existing = ingestJobPort.findByKbAndHash(kbId, hash);
        if (existing.isPresent()) {
            return new CreateIngestJobOutcome(existing.get(), true);
        }
        FileReference reference = fileStoragePort.store(userId, name, contentType, bytes);
        IngestJob job = IngestJob.pending(UUID.randomUUID().toString(), kbId, userId, name, hash, clock.instant())
                .withFile(reference.fileId());
        ingestJobPort.create(job);
        try {
            launcher.launch(job.jobId());
        } catch (TaskRejectedException ex) {
            ingestJobPort.update(job.failed("ingest queue is full", clock.instant()));
            throw new IngestQueueFullException("ingest queue is full, retry later");
        }
        return new CreateIngestJobOutcome(job, false);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
