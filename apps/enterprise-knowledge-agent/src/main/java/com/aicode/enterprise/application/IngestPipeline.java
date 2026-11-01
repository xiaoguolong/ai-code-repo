package com.aicode.enterprise.application;

import com.aicode.core.domain.OcrMarkdownImageProcessor;
import com.aicode.core.domain.TextChunker;
import com.aicode.core.domain.exception.NotFoundException;
import com.aicode.core.domain.model.ChatMessage;
import com.aicode.core.domain.model.ChatOptions;
import com.aicode.core.domain.model.ChatResult;
import com.aicode.core.domain.model.DocumentChunk;
import com.aicode.core.domain.model.FileContent;
import com.aicode.core.domain.model.MessageRole;
import com.aicode.core.domain.model.OcrFileType;
import com.aicode.core.domain.model.OcrResult;
import com.aicode.core.domain.model.PromptTemplate;
import com.aicode.core.domain.model.TokenUsage;
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
import com.aicode.enterprise.domain.port.DocumentPort;
import com.aicode.enterprise.domain.port.IngestJobPort;
import com.aicode.enterprise.infrastructure.config.IngestProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 入库流水线（Week 32）：upload → ocr → clean → chunk → embed → index。
 *
 * <p>每一步都落 {@link IngestJobStep}（状态/耗时/摘要/prompt/token），供"节点视图"回放。
 * 幂等：重复执行成功的作业直接返回；OCR 已有 {@code ocrMarkdown} 时跳过；
 * 索引使用确定性 documentId（{@code ingest-<jobId>}），重试先删后建。</p>
 */
@Service
public class IngestPipeline {

    private static final Logger log = LoggerFactory.getLogger(IngestPipeline.class);

    private static final int STEP_UPLOAD = 1;
    private static final int STEP_OCR = 2;
    private static final int STEP_CLEAN = 3;
    private static final int STEP_CHUNK = 4;
    private static final int STEP_EMBED = 5;
    private static final int STEP_INDEX = 6;

    /** clean 步骤使用的模板名（版本由 prompt.version 决定）。 */
    private static final String CLEAN_PROMPT = "ingest-clean";

    /** 清洗分段长度：1500 字符约 700-900 token，低于 chat.max-tokens=1024，避免输出截断。 */
    private static final int CLEAN_SEGMENT_CHARS = 1500;

    private final IngestJobPort jobs;
    private final FileStoragePort fileStoragePort;
    private final DocumentOcrPort ocrPort;
    private final OcrMarkdownImageProcessor imageProcessor;
    private final ChatModelPort chatModelPort;
    private final PromptTemplatePort promptTemplatePort;
    private final DocumentPort documentPort;
    private final EmbeddingModelPort embeddingModelPort;
    private final VectorStorePort vectorStorePort;
    private final RagRuntimeConfig ragConfig;
    private final ChatRuntimeConfig chatConfig;
    private final IngestProperties properties;
    private final Clock clock;

    public IngestPipeline(
            IngestJobPort jobs,
            FileStoragePort fileStoragePort,
            DocumentOcrPort ocrPort,
            OcrMarkdownImageProcessor imageProcessor,
            ChatModelPort chatModelPort,
            PromptTemplatePort promptTemplatePort,
            DocumentPort documentPort,
            EmbeddingModelPort embeddingModelPort,
            VectorStorePort vectorStorePort,
            RagRuntimeConfig ragConfig,
            ChatRuntimeConfig chatConfig,
            IngestProperties properties,
            Clock clock
    ) {
        this.jobs = jobs;
        this.fileStoragePort = fileStoragePort;
        this.ocrPort = ocrPort;
        this.imageProcessor = imageProcessor;
        this.chatModelPort = chatModelPort;
        this.promptTemplatePort = promptTemplatePort;
        this.documentPort = documentPort;
        this.embeddingModelPort = embeddingModelPort;
        this.vectorStorePort = vectorStorePort;
        this.ragConfig = ragConfig;
        this.chatConfig = chatConfig;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 执行或继续一个作业。异步线程调用；已成功/已取消直接返回。
     *
     * @param jobId 作业 ID
     * @throws NotFoundException 作业不存在
     */
    public void run(String jobId) {
        IngestJob job = load(jobId);
        if (job.status() == IngestJobStatus.SUCCEEDED || job.status() == IngestJobStatus.CANCELLED) {
            return;
        }
        job = job.running(now());
        jobs.update(job);
        try {
            job = uploadStep(job);
            job = ocrStep(job);
            if (cancelled(jobId)) {
                return;
            }
            job = cleanStep(job);
            if (cancelled(jobId)) {
                return;
            }
            job = chunkStep(job);
            job = embedStep(job);
            if (cancelled(jobId)) {
                return;
            }
            job = indexStep(job);
            if (cancelled(jobId)) {
                return;
            }
            jobs.update(job.succeeded(job.documentId(), job.chunkCount(), now()));
            log.info("[ingest] job={} succeeded pages={} chunks={} documentId={}",
                    jobId, job.pageCount(), job.chunkCount(), job.documentId());
        } catch (RuntimeException ex) {
            if (cancelled(jobId)) {
                return;
            }
            String message = rootMessage(ex);
            jobs.update(load(jobId).failed(message, now()));
            log.warn("[ingest] job={} failed: {}", jobId, message);
        }
    }

    /**
     * 重试失败作业（成功/已取消返回 false）。
     *
     * @param jobId 作业
     * @return 是否受理
     */
    public boolean retry(String jobId) {
        IngestJob job = load(jobId);
        if (job.status() != IngestJobStatus.FAILED) {
            return false;
        }
        IngestJob reset = new IngestJob(job.jobId(), job.kbId(), job.userId(), job.fileName(), job.fileId(),
                job.contentHash(), IngestJobStatus.PENDING, job.pageCount(), job.chunkCount(),
                job.ocrMarkdown(), job.cleanMarkdown(), "", "", job.createdAt(), job.startedAt(), null);
        jobs.update(reset);
        return true;
    }

    /**
     * 取消未完成作业。
     *
     * @param jobId 作业
     * @return 是否受理
     */
    public boolean cancel(String jobId) {
        IngestJob job = load(jobId);
        if (job.status().terminal()) {
            return false;
        }
        jobs.update(job.cancelled(now()));
        return true;
    }

    private IngestJob uploadStep(IngestJob job) {
        IngestJobStep base = baseStep(job, STEP_UPLOAD, IngestStepKey.UPLOAD,
                "file=" + job.fileName() + "，hash=" + job.contentHash());
        jobs.saveStep(base.running(now()));
        jobs.saveStep(base.succeeded("fileId=" + job.fileId() + "，bytes 已落盘", "", "", null, 0, now()));
        return job;
    }

    private IngestJob ocrStep(IngestJob job) {
        IngestJobStep base = baseStep(job, STEP_OCR, IngestStepKey.OCR, "file=" + job.fileName());
        if (!job.ocrMarkdown().isBlank()) {
            jobs.saveStep(base.skipped("已有 OCR 文本，跳过", now()));
            return job;
        }
        jobs.saveStep(base.running(now()));
        long start = System.nanoTime();
        try {
            FileContent content = fileStoragePort.load(job.fileId());
            String base64 = Base64.getEncoder().encodeToString(content.bytes());
            OcrResult result = ocrPort.recognize(base64, OcrFileType.inferFromFileName(job.fileName()));
            if (result.pageCount() > properties.getMaxPages()) {
                throw new IllegalStateException("OCR 页数超限：" + result.pageCount()
                        + " > " + properties.getMaxPages());
            }
            String markdown = imageProcessor.clean(result, job.userId());
            jobs.saveStep(base.succeeded(
                    "页数=" + result.pageCount() + "，字符=" + markdown.length(),
                    "", "", null, elapsedMs(start), now()));
            return job.withOcr(result.pageCount(), markdown);
        } catch (RuntimeException ex) {
            jobs.saveStep(base.failed(rootMessage(ex), elapsedMs(start), now()));
            throw ex;
        }
    }

    private IngestJob cleanStep(IngestJob job) {
        IngestJobStep base = baseStep(job, STEP_CLEAN, IngestStepKey.CLEAN,
                "ocrChars=" + job.ocrMarkdown().length());
        if (!properties.isCleanEnabled()) {
            jobs.saveStep(base.skipped("ingest.clean-enabled=false，OCR 原文直接切片", now()));
            return job;
        }
        jobs.saveStep(base.running(now()));
        long start = System.nanoTime();
        try {
            PromptTemplate prompt = promptTemplatePort.render(CLEAN_PROMPT, Map.of());
            // 长文档不能一次喂给模型：max-tokens=1024 会把输出截断（真机 20 页只清洗出 432 字）。
            // 按 1500 字符分段清洗，逐段累加 token；prompt 原文只存模板本身。
            List<String> segments = TextChunker.split(job.ocrMarkdown(), CLEAN_SEGMENT_CHARS, 0);
            StringBuilder cleaned = new StringBuilder();
            int promptTokens = 0;
            int completionTokens = 0;
            String model = chatConfig.model();
            for (String segment : segments) {
                ChatResult result = chatModelPort.chat(
                        List.of(new ChatMessage(MessageRole.SYSTEM, prompt.content()),
                                new ChatMessage(MessageRole.USER, segment)),
                        new ChatOptions(chatConfig.model(), chatConfig.temperature(), chatConfig.maxTokens()));
                String part = result.content() == null || result.content().isBlank()
                        ? segment : result.content();
                cleaned.append(part);
                if (!cleaned.isEmpty() && cleaned.charAt(cleaned.length() - 1) != '\n') {
                    cleaned.append('\n');
                }
                promptTokens += result.usage().promptTokens();
                completionTokens += result.usage().completionTokens();
                if (result.model() != null && !result.model().isBlank()) {
                    model = result.model();
                }
            }
            String text = cleaned.toString().trim();
            jobs.saveStep(base.succeeded(
                    "清洗后字符=" + text.length() + "，分段=" + segments.size() + "，模型=" + model,
                    prompt.version(), prompt.content(),
                    new TokenUsage(promptTokens, completionTokens, promptTokens + completionTokens),
                    elapsedMs(start), now()));
            return job.withClean(text);
        } catch (RuntimeException ex) {
            jobs.saveStep(base.failed(rootMessage(ex), elapsedMs(start), now()));
            throw ex;
        }
    }

    private IngestJob chunkStep(IngestJob job) {
        IngestJobStep base = baseStep(job, STEP_CHUNK, IngestStepKey.CHUNK,
                "textChars=" + textOf(job).length());
        jobs.saveStep(base.running(now()));
        try {
            int count = TextChunker.split(textOf(job), ragConfig.chunkSize(), ragConfig.chunkOverlap()).size();
            jobs.saveStep(base.succeeded("块数=" + count, "", "", null, 0, now()));
            return job.withDocument(job.documentId(), count);
        } catch (RuntimeException ex) {
            jobs.saveStep(base.failed(rootMessage(ex), 0, now()));
            throw ex;
        }
    }

    private IngestJob embedStep(IngestJob job) {
        List<String> chunks = TextChunker.split(textOf(job), ragConfig.chunkSize(), ragConfig.chunkOverlap());
        IngestJobStep base = baseStep(job, STEP_EMBED, IngestStepKey.EMBED, "块数=" + chunks.size());
        jobs.saveStep(base.running(now()));
        long start = System.nanoTime();
        int index = 0;
        try {
            for (; index < chunks.size(); index++) {
                embeddingModelPort.embed(chunks.get(index));
            }
            jobs.saveStep(base.succeeded("向量数=" + chunks.size(), "", "", null, elapsedMs(start), now()));
            return job;
        } catch (RuntimeException ex) {
            jobs.saveStep(base.failed("第 " + (index + 1) + " 块失败：" + rootMessage(ex),
                    elapsedMs(start), now()));
            throw ex;
        }
    }

    private IngestJob indexStep(IngestJob job) {
        List<String> chunks = TextChunker.split(textOf(job), ragConfig.chunkSize(), ragConfig.chunkOverlap());
        String documentId = job.documentId().isBlank() ? "ingest-" + job.jobId() : job.documentId();
        IngestJobStep base = baseStep(job, STEP_INDEX, IngestStepKey.INDEX, "documentId=" + documentId);
        jobs.saveStep(base.running(now()));
        long start = System.nanoTime();
        try {
            documentPort.deleteByDocumentId(documentId);
            documentPort.create(new DocumentMetadata(documentId, job.kbId(), job.fileName(),
                    chunks.size(), now()));
            for (int i = 0; i < chunks.size(); i++) {
                String text = chunks.get(i);
                vectorStorePort.put(new DocumentChunk(documentId, job.kbId(), i, text),
                        embeddingModelPort.embed(text));
            }
            jobs.saveStep(base.succeeded("documentId=" + documentId + "，块数=" + chunks.size(),
                    "", "", null, elapsedMs(start), now()));
            return job.withDocument(documentId, chunks.size());
        } catch (RuntimeException ex) {
            jobs.saveStep(base.failed(rootMessage(ex), elapsedMs(start), now()));
            throw ex;
        }
    }

    private IngestJobStep baseStep(IngestJob job, int stepNo, IngestStepKey key, String input) {
        return IngestJobStep.pending(job.jobId(), stepNo, key, input, now());
    }

    private String textOf(IngestJob job) {
        return job.cleanMarkdown().isBlank() ? job.ocrMarkdown() : job.cleanMarkdown();
    }

    private boolean cancelled(String jobId) {
        return jobs.findById(jobId).map(job -> job.status() == IngestJobStatus.CANCELLED).orElse(true);
    }

    private IngestJob load(String jobId) {
        return jobs.findById(jobId).orElseThrow(() -> new NotFoundException("ingest job not found"));
    }

    private Instant now() {
        return clock.instant();
    }

    private static long elapsedMs(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    private static String rootMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
