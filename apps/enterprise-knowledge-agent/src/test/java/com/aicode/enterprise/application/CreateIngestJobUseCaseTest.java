package com.aicode.enterprise.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.core.domain.exception.NotFoundException;
import com.aicode.core.domain.model.FileContent;
import com.aicode.core.domain.model.FileReference;
import com.aicode.core.domain.model.StorageType;
import com.aicode.core.domain.port.FileStoragePort;
import com.aicode.enterprise.domain.exception.ForbiddenException;
import com.aicode.enterprise.domain.exception.IngestQueueFullException;
import com.aicode.enterprise.domain.model.IngestJob;
import com.aicode.enterprise.domain.model.IngestJobStatus;
import com.aicode.enterprise.domain.model.IngestJobStep;
import com.aicode.enterprise.domain.model.KnowledgeBase;
import com.aicode.enterprise.domain.port.IngestJobLauncher;
import com.aicode.enterprise.domain.port.IngestJobPort;
import com.aicode.enterprise.domain.port.KnowledgeBasePort;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 建作业用例契约：400（空文件）/403（越权）/429（队列满）/幂等（Week 33）。
 */
class CreateIngestJobUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");

    private final FakeKnowledgeBases knowledgeBases = new FakeKnowledgeBases();
    private final FakeJobs jobs = new FakeJobs();
    private final FakeFiles files = new FakeFiles();
    private final AtomicBoolean launched = new AtomicBoolean();

    private CreateIngestJobUseCase useCase(IngestJobLauncher launcher) {
        return new CreateIngestJobUseCase(knowledgeBases, jobs, files, launcher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private final IngestJobLauncher okLauncher = jobId -> launched.set(true);

    @Test
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> useCase(okLauncher).create(1L, 42L, "a.pdf", "application/pdf", new byte[0]))
                .isInstanceOf(InvalidChatRequestException.class);
        assertThat(launched).isFalse();
    }

    @Test
    void rejectsForeignKnowledgeBase() {
        assertThatThrownBy(() -> useCase(okLauncher).create(1L, 99L, "a.pdf", "application/pdf", new byte[]{1}))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void createsJobAndLaunches() {
        CreateIngestJobOutcome outcome = useCase(okLauncher)
                .create(1L, 42L, "a.pdf", "application/pdf", new byte[]{1, 2});

        assertThat(outcome.duplicate()).isFalse();
        assertThat(outcome.job().fileName()).isEqualTo("a.pdf");
        assertThat(launched).isTrue();
        assertThat(jobs.jobs).hasSize(1);
    }

    @Test
    void returnsExistingJobForDuplicateContent() {
        jobs.jobs.put("existing", IngestJob.pending("existing", 1L, 42L, "a.pdf", sha("same".getBytes()), NOW));

        CreateIngestJobOutcome outcome = useCase(okLauncher)
                .create(1L, 42L, "a.pdf", "application/pdf", "same".getBytes());

        assertThat(outcome.duplicate()).isTrue();
        assertThat(outcome.job().jobId()).isEqualTo("existing");
        assertThat(launched).isFalse();
    }

    @Test
    void marksFailedAndRaisesQueueFullWhenRejected() {
        IngestJobLauncher rejecting = jobId -> {
            throw new TaskRejectedException("full");
        };

        assertThatThrownBy(() -> useCase(rejecting)
                .create(1L, 42L, "a.pdf", "application/pdf", new byte[]{9}))
                .isInstanceOf(IngestQueueFullException.class);
        assertThat(jobs.jobs.values()).singleElement()
                .extracting(IngestJob::status).isEqualTo(IngestJobStatus.FAILED);
    }

    private static String sha(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    // ---------- 假实现 ----------

    private static final class FakeKnowledgeBases implements KnowledgeBasePort {
        private final KnowledgeBase kb = new KnowledgeBase(1L, 42L, "库", "", NOW);

        @Override
        public KnowledgeBase create(KnowledgeBase knowledgeBase) {
            return knowledgeBase;
        }

        @Override
        public Optional<KnowledgeBase> findById(long id) {
            return id == 1L ? Optional.of(kb) : Optional.empty();
        }

        @Override
        public List<KnowledgeBase> listByUser(long userId) {
            return List.of(kb);
        }

        @Override
        public void update(KnowledgeBase knowledgeBase) {
        }

        @Override
        public void deleteById(long id) {
        }
    }

    private static final class FakeJobs implements IngestJobPort {
        private final Map<String, IngestJob> jobs = new LinkedHashMap<>();

        @Override
        public void create(IngestJob job) {
            jobs.put(job.jobId(), job);
        }

        @Override
        public void update(IngestJob job) {
            jobs.put(job.jobId(), job);
        }

        @Override
        public Optional<IngestJob> findById(String jobId) {
            return Optional.ofNullable(jobs.get(jobId));
        }

        @Override
        public Optional<IngestJob> findByKbAndHash(long kbId, String contentHash) {
            return jobs.values().stream()
                    .filter(job -> job.kbId() == kbId && job.contentHash().equals(contentHash))
                    .findFirst();
        }

        @Override
        public List<IngestJob> listByKb(long kbId, int limit) {
            return new ArrayList<>(jobs.values());
        }

        @Override
        public void saveStep(IngestJobStep step) {
        }

        @Override
        public List<IngestJobStep> listSteps(String jobId) {
            return List.of();
        }

        @Override
        public int failUnfinished(String reason) {
            return 0;
        }

        @Override
        public int clearPrompt(String jobId) {
            return 0;
        }
    }

    private static final class FakeFiles implements FileStoragePort {
        @Override
        public FileReference store(Long userId, String originalName, String contentType, byte[] content) {
            return new FileReference("f1", originalName, contentType, content.length, StorageType.LOCAL, "/f1");
        }

        @Override
        public FileContent load(String fileId) {
            return new FileContent("application/pdf", new byte[]{1});
        }
    }

    @Test
    void rejectsUnknownKnowledgeBase() {
        assertThatThrownBy(() -> useCase(okLauncher).create(999L, 42L, "a.pdf", "application/pdf", new byte[]{1}))
                .isInstanceOf(NotFoundException.class);
    }
}
