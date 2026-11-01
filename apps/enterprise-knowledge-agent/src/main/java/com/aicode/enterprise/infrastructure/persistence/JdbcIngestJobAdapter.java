package com.aicode.enterprise.infrastructure.persistence;

import com.aicode.enterprise.domain.model.IngestJob;
import com.aicode.enterprise.domain.model.IngestJobStatus;
import com.aicode.enterprise.domain.model.IngestJobStep;
import com.aicode.enterprise.domain.model.IngestStepKey;
import com.aicode.enterprise.domain.model.IngestStepStatus;
import com.aicode.enterprise.domain.port.IngestJobPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 入库作业/步骤落库适配器（Week 32，表 {@code ingest_job}/{@code ingest_job_step}，迁移 V8）。
 *
 * <p>步骤写入用「先 UPDATE 影响 0 行再 INSERT」：PostgreSQL 与 H2 的 upsert 语法不同
 * （与 {@link JdbcSsoUserLinkAdapter} 同口径）。</p>
 */
@Component
public class JdbcIngestJobAdapter implements IngestJobPort {

    private static final String JOB_COLUMNS = """
            SELECT job_id, kb_id, user_id, file_name, file_id, content_hash, status,
                   page_count, chunk_count, ocr_markdown, clean_markdown, document_id, error_message,
                   created_at, started_at, finished_at
            FROM ingest_job
            """;

    private static final String STEP_COLUMNS = """
            SELECT job_id, step_no, step_key, status, input_summary, output_summary,
                   prompt_version, prompt_text, prompt_tokens, completion_tokens,
                   duration_ms, error_message, created_at, updated_at
            FROM ingest_job_step
            """;

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<IngestJob> jobMapper = new IngestJobRowMapper();
    private final RowMapper<IngestJobStep> stepMapper = new IngestJobStepRowMapper();

    public JdbcIngestJobAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void create(IngestJob job) {
        jdbcTemplate.update("""
                        INSERT INTO ingest_job (job_id, kb_id, user_id, file_name, file_id, content_hash,
                                                status, page_count, chunk_count, ocr_markdown, clean_markdown,
                                                document_id, error_message, created_at, started_at, finished_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                job.jobId(), job.kbId(), job.userId(), job.fileName(), job.fileId(), job.contentHash(),
                job.status().name(), job.pageCount(), job.chunkCount(), job.ocrMarkdown(), job.cleanMarkdown(),
                job.documentId(), job.errorMessage(), ts(job.createdAt()), ts(job.startedAt()), ts(job.finishedAt()));
    }

    @Override
    public void update(IngestJob job) {
        jdbcTemplate.update("""
                        UPDATE ingest_job
                        SET file_id = ?, status = ?, page_count = ?, chunk_count = ?, ocr_markdown = ?,
                            clean_markdown = ?, document_id = ?, error_message = ?, started_at = ?, finished_at = ?
                        WHERE job_id = ?
                        """,
                job.fileId(), job.status().name(), job.pageCount(), job.chunkCount(), job.ocrMarkdown(),
                job.cleanMarkdown(), job.documentId(), job.errorMessage(), ts(job.startedAt()),
                ts(job.finishedAt()), job.jobId());
    }

    @Override
    public Optional<IngestJob> findById(String jobId) {
        return jdbcTemplate.query(JOB_COLUMNS + " WHERE job_id = ?", jobMapper, jobId)
                .stream().findFirst();
    }

    @Override
    public Optional<IngestJob> findByKbAndHash(long kbId, String contentHash) {
        return jdbcTemplate.query(
                        JOB_COLUMNS + " WHERE kb_id = ? AND content_hash = ?",
                        jobMapper, kbId, contentHash)
                .stream().findFirst();
    }

    @Override
    public List<IngestJob> listByKb(long kbId, int limit) {
        return jdbcTemplate.query(
                JOB_COLUMNS + " WHERE kb_id = ? ORDER BY created_at DESC, job_id DESC LIMIT ?",
                jobMapper, kbId, Math.max(limit, 0));
    }

    @Override
    public void saveStep(IngestJobStep step) {
        int updated = jdbcTemplate.update("""
                        UPDATE ingest_job_step
                        SET status = ?, input_summary = ?, output_summary = ?, prompt_version = ?,
                            prompt_text = ?, prompt_tokens = ?, completion_tokens = ?,
                            duration_ms = ?, error_message = ?, updated_at = ?
                        WHERE job_id = ? AND step_no = ?
                        """,
                step.status().name(), step.inputSummary(), step.outputSummary(), step.promptVersion(),
                step.promptText(), step.promptTokens(), step.completionTokens(), step.durationMs(),
                step.errorMessage(), ts(step.updatedAt()), step.jobId(), step.stepNo());
        if (updated == 0) {
            jdbcTemplate.update("""
                            INSERT INTO ingest_job_step (job_id, step_no, step_key, status, input_summary,
                                output_summary, prompt_version, prompt_text, prompt_tokens, completion_tokens,
                                duration_ms, error_message, created_at, updated_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    step.jobId(), step.stepNo(), step.key().apiKey(), step.status().name(),
                    step.inputSummary(), step.outputSummary(), step.promptVersion(), step.promptText(),
                    step.promptTokens(), step.completionTokens(), step.durationMs(), step.errorMessage(),
                    ts(step.createdAt()), ts(step.updatedAt()));
        }
    }

    @Override
    public List<IngestJobStep> listSteps(String jobId) {
        return jdbcTemplate.query(
                STEP_COLUMNS + " WHERE job_id = ? ORDER BY step_no ASC", stepMapper, jobId);
    }

    @Override
    public int failUnfinished(String reason) {
        return jdbcTemplate.update("""
                        UPDATE ingest_job
                        SET status = 'FAILED', error_message = ?, finished_at = CURRENT_TIMESTAMP
                        WHERE status IN ('RUNNING', 'PENDING')
                        """, reason);
    }

    @Override
    public int clearPrompt(String jobId) {
        return jdbcTemplate.update(
                "UPDATE ingest_job_step SET prompt_text = '' WHERE job_id = ? AND prompt_text <> ''",
                jobId);
    }

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    /** ingest_job 行映射。 */
    private static final class IngestJobRowMapper implements RowMapper<IngestJob> {
        @Override
        public IngestJob mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new IngestJob(
                    rs.getString("job_id"),
                    rs.getLong("kb_id"),
                    rs.getLong("user_id"),
                    rs.getString("file_name"),
                    rs.getString("file_id"),
                    rs.getString("content_hash"),
                    IngestJobStatus.valueOf(rs.getString("status")),
                    rs.getInt("page_count"),
                    rs.getInt("chunk_count"),
                    rs.getString("ocr_markdown"),
                    rs.getString("clean_markdown"),
                    rs.getString("document_id"),
                    rs.getString("error_message"),
                    instant(rs.getTimestamp("created_at")),
                    instant(rs.getTimestamp("started_at")),
                    instant(rs.getTimestamp("finished_at")));
        }
    }

    /** ingest_job_step 行映射。 */
    private static final class IngestJobStepRowMapper implements RowMapper<IngestJobStep> {
        @Override
        public IngestJobStep mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new IngestJobStep(
                    rs.getString("job_id"),
                    rs.getInt("step_no"),
                    IngestStepKey.fromKey(rs.getString("step_key")),
                    IngestStepStatus.valueOf(rs.getString("status")),
                    rs.getString("input_summary"),
                    rs.getString("output_summary"),
                    rs.getString("prompt_version"),
                    rs.getString("prompt_text"),
                    rs.getInt("prompt_tokens"),
                    rs.getInt("completion_tokens"),
                    rs.getLong("duration_ms"),
                    rs.getString("error_message"),
                    instant(rs.getTimestamp("created_at")),
                    instant(rs.getTimestamp("updated_at")));
        }
    }
}
