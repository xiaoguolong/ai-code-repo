package com.aicode.enterprise.infrastructure.persistence;

import com.aicode.enterprise.domain.model.IngestJob;
import com.aicode.enterprise.domain.model.IngestJobStatus;
import com.aicode.enterprise.domain.model.IngestJobStep;
import com.aicode.enterprise.domain.model.IngestStepKey;
import com.aicode.enterprise.domain.model.IngestStepStatus;
import com.aicode.enterprise.domain.port.IngestJobPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 入库作业落库适配器测试（H2 MODE=PostgreSQL + Flyway V8）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class JdbcIngestJobAdapterTest {

    private static final long USER_ID = 990101L;
    private static final long KB_ID = 990201L;

    @Autowired
    private IngestJobPort ingestJobPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Instant now = Instant.parse("2026-11-01T00:00:00Z");

    @BeforeEach
    void seedUserAndKb() {
        jdbcTemplate.update(
                "MERGE INTO app_user (id, username, password_hash) KEY (id) VALUES (?, ?, ?)",
                USER_ID, "w32-user", "x");
        jdbcTemplate.update(
                "MERGE INTO knowledge_base (id, user_id, name, description) KEY (id) VALUES (?, ?, ?, ?)",
                KB_ID, USER_ID, "W32 测试库", "");
    }

    @Test
    void createsJobAndReadsItBack() {
        IngestJob job = IngestJob.pending("job-1", KB_ID, USER_ID, "guide.pdf", "hash-1", now);

        ingestJobPort.create(job);

        IngestJob loaded = ingestJobPort.findById("job-1").orElseThrow();
        assertThat(loaded.status()).isEqualTo(IngestJobStatus.PENDING);
        assertThat(loaded.fileName()).isEqualTo("guide.pdf");
        assertThat(loaded.contentHash()).isEqualTo("hash-1");
        assertThat(loaded.pageCount()).isZero();
    }

    @Test
    void updatesJobThroughOcrAndTerminalState() {
        ingestJobPort.create(IngestJob.pending("job-2", KB_ID, USER_ID, "scan.pdf", "hash-2", now));

        IngestJob running = ingestJobPort.findById("job-2").orElseThrow().running(now);
        ingestJobPort.update(running);
        ingestJobPort.update(running.withOcr(20, "# 第 1 页\n正文"));

        IngestJob succeeded = ingestJobPort.findById("job-2").orElseThrow()
                .succeeded("doc-1", 143, now.plusSeconds(30));
        ingestJobPort.update(succeeded);

        IngestJob loaded = ingestJobPort.findById("job-2").orElseThrow();
        assertThat(loaded.status()).isEqualTo(IngestJobStatus.SUCCEEDED);
        assertThat(loaded.pageCount()).isEqualTo(20);
        assertThat(loaded.chunkCount()).isEqualTo(143);
        assertThat(loaded.ocrMarkdown()).contains("正文");
        assertThat(loaded.documentId()).isEqualTo("doc-1");
        assertThat(loaded.finishedAt()).isEqualTo(now.plusSeconds(30));
    }

    @Test
    void findsByKbAndHashAndListsRecentJobs() {
        ingestJobPort.create(IngestJob.pending("job-3", KB_ID, USER_ID, "a.pdf", "hash-3", now));
        ingestJobPort.create(IngestJob.pending("job-4", KB_ID, USER_ID, "b.pdf", "hash-4", now.plusSeconds(1)));

        assertThat(ingestJobPort.findByKbAndHash(KB_ID, "hash-3")).get()
                .extracting(IngestJob::jobId).isEqualTo("job-3");
        assertThat(ingestJobPort.findByKbAndHash(KB_ID, "missing")).isEmpty();
        List<IngestJob> jobs = ingestJobPort.listByKb(KB_ID, 10);
        assertThat(jobs).extracting(IngestJob::jobId).containsExactly("job-4", "job-3");
    }

    @Test
    void savesStepAsUpsertWithPromptAndTokens() {
        ingestJobPort.create(IngestJob.pending("job-5", KB_ID, USER_ID, "c.pdf", "hash-5", now));
        IngestJobStep pending = IngestJobStep.pending("job-5", 1, IngestStepKey.UPLOAD, "file=c.pdf (2MB)", now);
        ingestJobPort.saveStep(pending);
        ingestJobPort.saveStep(pending.running(now).succeeded("fileId=f1", "", "", null, 12, now));

        List<IngestJobStep> steps = ingestJobPort.listSteps("job-5");
        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).status()).isEqualTo(IngestStepStatus.SUCCEEDED);
        assertThat(steps.get(0).durationMs()).isEqualTo(12);
        assertThat(steps.get(0).inputSummary()).contains("c.pdf");
    }
}
