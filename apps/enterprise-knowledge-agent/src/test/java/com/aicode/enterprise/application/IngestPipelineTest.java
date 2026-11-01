package com.aicode.enterprise.application;

import com.aicode.core.domain.model.ChatMessage;
import com.aicode.core.domain.model.ChatOptions;
import com.aicode.core.domain.model.ChatResult;
import com.aicode.core.domain.model.DocumentChunk;
import com.aicode.core.domain.model.FileContent;
import com.aicode.core.domain.model.FileReference;
import com.aicode.core.domain.model.FinishReason;
import com.aicode.core.domain.model.OcrFileType;
import com.aicode.core.domain.model.OcrResult;
import com.aicode.core.domain.model.PromptDescriptor;
import com.aicode.core.domain.model.PromptTemplate;
import com.aicode.core.domain.model.StorageType;
import com.aicode.core.domain.model.TokenUsage;
import com.aicode.core.domain.model.ToolDefinition;
import com.aicode.core.domain.model.VectorSearchHit;
import com.aicode.core.domain.port.ChatModelPort;
import com.aicode.core.domain.port.DocumentOcrPort;
import com.aicode.core.domain.port.EmbeddingModelPort;
import com.aicode.core.domain.port.FileStoragePort;
import com.aicode.core.domain.port.PromptTemplatePort;
import com.aicode.core.domain.port.VectorStorePort;
import com.aicode.enterprise.domain.model.DocumentMetadata;
import com.aicode.enterprise.domain.model.IngestJob;
import com.aicode.enterprise.domain.model.IngestJobStatus;
import com.aicode.enterprise.domain.model.IngestJobStep;
import com.aicode.enterprise.domain.model.IngestStepKey;
import com.aicode.enterprise.domain.model.IngestStepStatus;
import com.aicode.enterprise.domain.port.DocumentPort;
import com.aicode.enterprise.domain.port.IngestJobPort;
import com.aicode.enterprise.infrastructure.config.IngestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 入库流水线测试（全假实现，不连 DB/网络）。
 */
class IngestPipelineTest {

    private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");

    private FakeJobs jobs;
    private FakeOcr ocr;
    private FakeChat chat;
    private FakeDocuments documents;
    private FakeVectors vectors;
    private IngestProperties properties;
    private IngestPipeline pipeline;

    @BeforeEach
    void setUp() {
        jobs = new FakeJobs();
        ocr = new FakeOcr();
        chat = new FakeChat();
        documents = new FakeDocuments();
        vectors = new FakeVectors();
        properties = new IngestProperties();
        pipeline = new IngestPipeline(
                jobs, new FakeFiles(), ocr, new com.aicode.core.domain.OcrMarkdownImageProcessor(null, new FakeFiles(), null),
                chat, new FakePrompts(), documents, text -> new float[]{0.1f, 0.2f}, vectors,
                new RagRuntimeConfig(4, 500, 50), new ChatRuntimeConfig("fake-model", 0.2, 256),
                properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void runsAllStepsAndStoresPromptAndTokens() {
        jobs.create(IngestJob.pending("j1", 1L, 2L, "guide.pdf", "hash-1", NOW).withFile("f1"));

        pipeline.run("j1");

        IngestJob job = jobs.findById("j1").orElseThrow();
        assertThat(job.status()).isEqualTo(IngestJobStatus.SUCCEEDED);
        assertThat(job.pageCount()).isEqualTo(2);
        assertThat(job.chunkCount()).isPositive();
        assertThat(job.documentId()).isEqualTo("ingest-j1");
        assertThat(documents.created).hasSize(1);
        assertThat(vectors.puts).isEqualTo(job.chunkCount());

        List<IngestJobStep> steps = jobs.listSteps("j1");
        assertThat(steps).hasSize(6);
        assertThat(steps).extracting(IngestJobStep::key).containsExactly(
                IngestStepKey.UPLOAD, IngestStepKey.OCR, IngestStepKey.CLEAN,
                IngestStepKey.CHUNK, IngestStepKey.EMBED, IngestStepKey.INDEX);
        assertThat(steps).allMatch(step -> step.status() == IngestStepStatus.SUCCEEDED);
        IngestJobStep clean = steps.get(2);
        assertThat(clean.promptText()).isEqualTo("CLEAN-PROMPT");
        assertThat(clean.promptVersion()).isEqualTo("v1");
        assertThat(clean.promptTokens()).isEqualTo(12);
        assertThat(clean.completionTokens()).isEqualTo(34);
    }

    @Test
    void skipsOcrOnRetryWhenMarkdownAlreadySaved() {
        jobs.create(IngestJob.pending("j2", 1L, 2L, "scan.pdf", "hash-2", NOW)
                .withFile("f1").withOcr(3, "# 已有 OCR 文本"));
        jobs.update(jobs.findById("j2").orElseThrow().failed("previous failure", NOW));

        assertThat(pipeline.retry("j2")).isTrue();
        pipeline.run("j2");

        assertThat(jobs.findById("j2").orElseThrow().status()).isEqualTo(IngestJobStatus.SUCCEEDED);
        assertThat(ocr.calls).isZero();
        assertThat(jobs.listSteps("j2"))
                .filteredOn(step -> step.key() == IngestStepKey.OCR)
                .singleElement()
                .extracting(IngestJobStep::status).isEqualTo(IngestStepStatus.SKIPPED);
    }

    @Test
    void marksJobFailedWhenOcrFails() {
        ocr.failure = new IllegalStateException("OCR HTTP 500");
        jobs.create(IngestJob.pending("j3", 1L, 2L, "bad.pdf", "hash-3", NOW).withFile("f1"));

        pipeline.run("j3");

        IngestJob job = jobs.findById("j3").orElseThrow();
        assertThat(job.status()).isEqualTo(IngestJobStatus.FAILED);
        assertThat(job.errorMessage()).contains("OCR HTTP 500");
        assertThat(jobs.listSteps("j3"))
                .filteredOn(step -> step.key() == IngestStepKey.OCR)
                .singleElement()
                .extracting(IngestJobStep::status).isEqualTo(IngestStepStatus.FAILED);
    }

    @Test
    void doesNothingWhenJobCancelledBeforeRun() {
        jobs.create(IngestJob.pending("j4", 1L, 2L, "c.pdf", "hash-4", NOW).withFile("f1"));

        assertThat(pipeline.cancel("j4")).isTrue();
        pipeline.run("j4");

        assertThat(jobs.findById("j4").orElseThrow().status()).isEqualTo(IngestJobStatus.CANCELLED);
        assertThat(jobs.listSteps("j4")).isEmpty();
        assertThat(ocr.calls).isZero();
    }

    @Test
    void reprocessSkipsOcrAndRebuildsChunks() {
        jobs.create(IngestJob.pending("j5", 1L, 2L, "guide.pdf", "hash-5", NOW)
                .withFile("f1").withOcr(2, "# 旧 OCR 文本"));
        jobs.update(jobs.findById("j5").orElseThrow().succeeded("ingest-j5", 1, NOW));
        documents.created.add(new DocumentMetadata("ingest-j5", 1L, "guide.pdf", 1, NOW));

        assertThat(pipeline.reprocess("j5")).isTrue();
        pipeline.run("j5");

        assertThat(ocr.calls).isZero();
        assertThat(jobs.findById("j5").orElseThrow().status()).isEqualTo(IngestJobStatus.SUCCEEDED);
        assertThat(jobs.listSteps("j5"))
                .filteredOn(step -> step.key() == IngestStepKey.OCR)
                .singleElement()
                .extracting(IngestJobStep::status).isEqualTo(IngestStepStatus.SKIPPED);
        assertThat(documents.created).hasSize(1);
        assertThat(documents.created.get(0).documentId()).isEqualTo("ingest-j5");
    }

    // ---------- 假实现 ----------

    private static final class FakeJobs implements IngestJobPort {
        private final Map<String, IngestJob> jobs = new LinkedHashMap<>();
        private final Map<String, Map<Integer, IngestJobStep>> steps = new LinkedHashMap<>();

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
            return jobs.values().stream().filter(job -> job.kbId() == kbId).limit(limit).toList();
        }

        @Override
        public void saveStep(IngestJobStep step) {
            steps.computeIfAbsent(step.jobId(), key -> new LinkedHashMap<>()).put(step.stepNo(), step);
        }

        @Override
        public List<IngestJobStep> listSteps(String jobId) {
            return new ArrayList<>(steps.getOrDefault(jobId, Map.of()).values());
        }
    }

    private static final class FakeFiles implements FileStoragePort {
        @Override
        public FileReference store(Long userId, String originalName, String contentType, byte[] content) {
            return new FileReference("f1", originalName, contentType, content.length, StorageType.LOCAL, "/f1");
        }

        @Override
        public FileContent load(String fileId) {
            return new FileContent("application/pdf", new byte[]{1, 2, 3});
        }
    }

    private static final class FakeOcr implements DocumentOcrPort {
        private int calls;
        private RuntimeException failure;

        @Override
        public OcrResult recognize(String file, OcrFileType fileType) {
            calls++;
            if (failure != null) {
                throw failure;
            }
            return new OcrResult("# 指南\n\n正文内容。", Map.of(), 2);
        }
    }

    private static final class FakeChat implements ChatModelPort {
        private int calls;

        @Override
        public ChatResult chat(List<ChatMessage> messages, ChatOptions options, List<ToolDefinition> tools) {
            calls++;
            return new ChatResult("清洗后的正文", List.of(), FinishReason.STOP,
                    new TokenUsage(12, 34, 46), "fake-model");
        }
    }

    private static final class FakePrompts implements PromptTemplatePort {
        @Override
        public PromptTemplate load(String name) {
            return new PromptTemplate("v1", "CLEAN-PROMPT");
        }

        @Override
        public List<PromptDescriptor> list() {
            return List.of(new PromptDescriptor("ingest-clean", "v1"));
        }

        @Override
        public PromptTemplate render(String name, Map<String, Object> variables) {
            return new PromptTemplate("v1", "CLEAN-PROMPT");
        }
    }

    private static final class FakeDocuments implements DocumentPort {
        private final List<DocumentMetadata> created = new ArrayList<>();

        @Override
        public void create(DocumentMetadata metadata) {
            created.add(metadata);
        }

        @Override
        public List<DocumentMetadata> listByKnowledgeBase(long knowledgeBaseId) {
            return created;
        }

        @Override
        public void deleteByDocumentId(String documentId) {
            created.removeIf(metadata -> metadata.documentId().equals(documentId));
        }
    }

    private static final class FakeVectors implements VectorStorePort {
        private int puts;

        @Override
        public void put(DocumentChunk chunk, float[] vector) {
            puts++;
        }

        @Override
        public List<VectorSearchHit> search(float[] query, int topK, long knowledgeBaseId) {
            return List.of();
        }

        @Override
        public void deleteByDocumentId(String documentId) {
            // no-op
        }
    }
}
