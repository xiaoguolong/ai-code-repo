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
import com.aicode.enterprise.domain.model.IngestStepStatus;
import com.aicode.enterprise.domain.port.DocumentPort;
import com.aicode.enterprise.domain.port.IngestJobPort;
import com.aicode.enterprise.infrastructure.config.IngestProperties;
import com.aicode.enterprise.infrastructure.ocr.PdfPageSplitter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
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
    private final PdfPageSplitter pdfPageSplitter;
    private final MeterRegistry meterRegistry;
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
            PdfPageSplitter pdfPageSplitter,
            MeterRegistry meterRegistry,
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
        this.pdfPageSplitter = pdfPageSplitter;
        this.meterRegistry = meterRegistry;
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
            IngestJob finished = job.succeeded(job.documentId(), job.chunkCount(), now());
            jobs.update(finished);
            recordJobMetric(finished);
            log.info("[ingest] job={} succeeded pages={} chunks={} documentId={}",
                    jobId, job.pageCount(), job.chunkCount(), job.documentId());
        } catch (RuntimeException ex) {
            if (cancelled(jobId)) {
                return;
            }
            String message = rootMessage(ex);
            IngestJob failed = load(jobId).failed(message, now());
            jobs.update(failed);
            recordJobMetric(failed);
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
     * 只重清洗/切片/向量化，不重跑 OCR（用于提示词升级后修复历史文档）。
     *
     * @param jobId 作业
     * @return 是否受理（没有 OCR 文本时拒绝）
     */
    public boolean reprocess(String jobId) {
        IngestJob job = load(jobId);
        if (job.ocrMarkdown().isBlank()) {
            return false;
        }
        IngestJob reset = new IngestJob(job.jobId(), job.kbId(), job.userId(), job.fileName(), job.fileId(),
                job.contentHash(), IngestJobStatus.PENDING, job.pageCount(), 0,
                job.ocrMarkdown(), "", "", "", job.createdAt(), job.startedAt(), null);
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
        IngestJob cancelled = job.cancelled(now());
        jobs.update(cancelled);
        recordJobMetric(cancelled);
        return true;
    }

    /**
     * 落步骤并按终态记录 step 耗时指标。
     *
     * @param step 步骤
     */
    private void saveStep(IngestJobStep step) {
        jobs.saveStep(step);
        if (step.status() == IngestStepStatus.SUCCEEDED
                || step.status() == IngestStepStatus.FAILED
                || step.status() == IngestStepStatus.SKIPPED) {
            Timer.builder("ingest.step.duration")
                    .tag("step", step.key().apiKey())
                    .tag("status", step.status().name().toLowerCase(Locale.ROOT))
                    .register(meterRegistry)
                    .record(step.durationMs(), TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 终态作业计数与耗时。
     *
     * @param job 终态作业
     */
    private void recordJobMetric(IngestJob job) {
        String status = job.status().name().toLowerCase(Locale.ROOT);
        Counter.builder("ingest.job").tag("status", status).register(meterRegistry).increment();
        if (job.startedAt() != null && job.finishedAt() != null) {
            Timer.builder("ingest.job.duration").tag("status", status).register(meterRegistry)
                    .record(Duration.between(job.startedAt(), job.finishedAt()));
        }
    }

    private IngestJob uploadStep(IngestJob job) {
        IngestJobStep base = baseStep(job, STEP_UPLOAD, IngestStepKey.UPLOAD,
                "file=" + job.fileName() + "，hash=" + job.contentHash());
        saveStep(base.running(now()));
        saveStep(base.succeeded("fileId=" + job.fileId() + "，bytes 已落盘", "", "", null, 0, now()));
        return job;
    }

    private IngestJob ocrStep(IngestJob job) {
        IngestJobStep base = baseStep(job, STEP_OCR, IngestStepKey.OCR, "file=" + job.fileName());
        if (!job.ocrMarkdown().isBlank()) {
            saveStep(base.skipped("已有 OCR 文本，跳过", now()));
            return job;
        }
        saveStep(base.running(now()));
        long start = System.nanoTime();
        try {
            FileContent content = fileStoragePort.load(job.fileId());
            byte[] bytes = content.bytes();
            int pages;
            int batches = 1;
            List<String> pageTexts = new ArrayList<>();
            if (pdfPageSplitter.isPdf(job.fileName(), content.contentType())) {
                PdfPageSplitter.Batches split = pdfPageSplitter.split(bytes, properties.getOcrBatchPages());
                if (split.totalPages() > properties.getMaxPages()) {
                    throw new IllegalStateException("PDF 页数超限：" + split.totalPages()
                            + " > " + properties.getMaxPages());
                }
                batches = split.chunks().size();
                pages = 0;
                for (int i = 0; i < batches; i++) {
                    OcrResult result = ocrPort.recognize(
                            Base64.getEncoder().encodeToString(split.chunks().get(i)), OcrFileType.PDF);
                    pages += split.counts().get(i);
                    pageTexts.add(imageProcessor.clean(result, job.userId()));
                    if (i < batches - 1) {
                        pauseBetweenBatches();
                    }
                }
            } else {
                OcrResult result = ocrPort.recognize(
                        Base64.getEncoder().encodeToString(bytes),
                        OcrFileType.inferFromFileName(job.fileName()));
                if (result.pageCount() > properties.getMaxPages()) {
                    throw new IllegalStateException("OCR 页数超限：" + result.pageCount()
                            + " > " + properties.getMaxPages());
                }
                pages = result.pageCount();
                pageTexts.add(imageProcessor.clean(result, job.userId()));
            }
            String markdown = String.join("\n\n", pageTexts);
            saveStep(base.succeeded(
                    "页数=" + pages + "，分批=" + batches + "，字符=" + markdown.length(),
                    "", "", null, elapsedMs(start), now()));
            return job.withOcr(pages, markdown);
        } catch (RuntimeException ex) {
            saveStep(base.failed(rootMessage(ex), elapsedMs(start), now()));
            throw ex;
        }
    }

    /** 批次之间稍作停顿，避免把厂商限流打满。 */
    private void pauseBetweenBatches() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting between OCR batches", ex);
        }
    }

    private IngestJob cleanStep(IngestJob job) {
        IngestJobStep base = baseStep(job, STEP_CLEAN, IngestStepKey.CLEAN,
                "ocrChars=" + job.ocrMarkdown().length());
        if (!properties.isCleanEnabled()) {
            saveStep(base.skipped("ingest.clean-enabled=false，OCR 原文直接切片", now()));
            return job;
        }
        saveStep(base.running(now()));
        long start = System.nanoTime();
        try {
            PromptTemplate prompt = promptTemplatePort.render(CLEAN_PROMPT, Map.of());
            // 长文档不能一次喂给模型：max-tokens=1024 会把输出截断（真机 20 页只清洗出 432 字）。
            // 按 1500 字符分段清洗，逐段累加 token；prompt 原文只存模板本身。
            List<String> segments = TextChunker.split(job.ocrMarkdown(), CLEAN_SEGMENT_CHARS, 0);
            StringBuilder cleaned = new StringBuilder();
            int promptTokens = 0;
            int completionTokens = 0;
            String model = properties.getCleanModel().isBlank() ? chatConfig.model() : properties.getCleanModel();
            for (String segment : segments) {
                ChatResult result = cleanSegment(prompt, segment, model);
                String part = acceptCleaned(result.content(), segment);
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
            saveStep(base.succeeded(
                    "清洗后字符=" + text.length() + "，分段=" + segments.size() + "，模型=" + model,
                    prompt.version(), prompt.content(),
                    new TokenUsage(promptTokens, completionTokens, promptTokens + completionTokens),
                    elapsedMs(start), now()));
            return job.withClean(text);
        } catch (RuntimeException ex) {
            saveStep(base.failed(rootMessage(ex), elapsedMs(start), now()));
            throw ex;
        }
    }

    private IngestJob chunkStep(IngestJob job) {
        IngestJobStep base = baseStep(job, STEP_CHUNK, IngestStepKey.CHUNK,
                "textChars=" + textOf(job).length());
        saveStep(base.running(now()));
        try {
            int count = TextChunker.split(textOf(job), ragConfig.chunkSize(), ragConfig.chunkOverlap()).size();
            saveStep(base.succeeded("块数=" + count, "", "", null, 0, now()));
            return job.withDocument(job.documentId(), count);
        } catch (RuntimeException ex) {
            saveStep(base.failed(rootMessage(ex), 0, now()));
            throw ex;
        }
    }

    private IngestJob embedStep(IngestJob job) {
        List<String> chunks = TextChunker.split(textOf(job), ragConfig.chunkSize(), ragConfig.chunkOverlap());
        IngestJobStep base = baseStep(job, STEP_EMBED, IngestStepKey.EMBED, "块数=" + chunks.size());
        saveStep(base.running(now()));
        long start = System.nanoTime();
        int index = 0;
        try {
            for (; index < chunks.size(); index++) {
                embeddingModelPort.embed(chunks.get(index));
            }
            saveStep(base.succeeded("向量数=" + chunks.size(), "", "", null, elapsedMs(start), now()));
            return job;
        } catch (RuntimeException ex) {
            saveStep(base.failed("第 " + (index + 1) + " 块失败：" + rootMessage(ex),
                    elapsedMs(start), now()));
            throw ex;
        }
    }

    private IngestJob indexStep(IngestJob job) {
        List<String> chunks = TextChunker.split(textOf(job), ragConfig.chunkSize(), ragConfig.chunkOverlap());
        String documentId = job.documentId().isBlank() ? "ingest-" + job.jobId() : job.documentId();
        IngestJobStep base = baseStep(job, STEP_INDEX, IngestStepKey.INDEX, "documentId=" + documentId);
        saveStep(base.running(now()));
        long start = System.nanoTime();
        try {
            // 先删切片再删文档：document_chunk.document_id 有外键指向 document，顺序反了会报 FK 错误。
            vectorStorePort.deleteByDocumentId(documentId);
            documentPort.deleteByDocumentId(documentId);
            documentPort.create(new DocumentMetadata(documentId, job.kbId(), job.fileName(),
                    chunks.size(), now()));
            for (int i = 0; i < chunks.size(); i++) {
                String text = chunks.get(i);
                vectorStorePort.put(new DocumentChunk(documentId, job.kbId(), i, text),
                        embeddingModelPort.embed(text));
            }
            saveStep(base.succeeded("documentId=" + documentId + "，块数=" + chunks.size(),
                    "", "", null, elapsedMs(start), now()));
            return job.withDocument(documentId, chunks.size());
        } catch (RuntimeException ex) {
            saveStep(base.failed(rootMessage(ex), elapsedMs(start), now()));
            throw ex;
        }
    }

    /**
     * 清理模型可能输出的"思考过程"前缀（deepseek-v4-pro 在个别输入上会把分析写进正文）。
     *
     * <p>规则：以"我们需要/用户要求/分析/好的"开头时，截到第一个 markdown 标题；
     * 找不到标题则退回原文片段。这是兜底，不能替代提示词约束。</p>
     *
     * @param text     模型输出
     * @param fallback 原始片段
     * @return 可入库正文
     */
    static String sanitizeCleanOutput(String text, String fallback) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return fallback;
        }
        String[] reasoningPrefixes = {"我们需要", "用户要求", "用户需要", "分析：", "好的，", "好的,"};
        boolean reasoning = false;
        for (String prefix : reasoningPrefixes) {
            if (trimmed.startsWith(prefix)) {
                reasoning = true;
                break;
            }
        }
        if (!reasoning) {
            return trimmed;
        }
        int heading = firstHeadingIndex(trimmed);
        return heading >= 0 ? trimmed.substring(heading).trim() : fallback;
    }

    /**
     * 调一次清洗；配置了独立模型且失败时回退到默认模型。
     *
     * @param prompt  提示模板
     * @param segment 原始片段
     * @param model   首选模型
     * @return 模型结果
     */
    private ChatResult cleanSegment(PromptTemplate prompt, String segment, String model) {
        List<ChatMessage> messages = List.of(
                new ChatMessage(MessageRole.SYSTEM, prompt.content()),
                new ChatMessage(MessageRole.USER,
                        "请清洗下面的 OCR 文本，只输出清洗后的 markdown 正文：\n\n" + segment));
        try {
            return chatModelPort.chat(messages,
                    new ChatOptions(model, chatConfig.temperature(), chatConfig.maxTokens()));
        } catch (RuntimeException ex) {
            if (model.equals(chatConfig.model())) {
                throw ex;
            }
            log.warn("[ingest] clean model {} 调用失败，回退 {}：{}", model, chatConfig.model(), rootMessage(ex));
            return chatModelPort.chat(messages, new ChatOptions(chatConfig.model(),
                    chatConfig.temperature(), chatConfig.maxTokens()));
        }
    }

    /**
     * 清洗结果验收：太短或复述指令时退回原文，避免把模型分析写进知识库。
     *
     * @param text     模型输出
     * @param fallback 原始片段
     * @return 可入库正文
     */
    static String acceptCleaned(String text, String fallback) {
        String cleaned = sanitizeCleanOutput(text, fallback);
        if (cleaned.length() < Math.max(20, fallback.length() / 5)) {
            return fallback;
        }
        if (cleaned.contains("用户要求清洗") || cleaned.contains("只输出清洗后的")) {
            return fallback;
        }
        return cleaned;
    }

    private static int firstHeadingIndex(String text) {        int index = 0;
        while (index < text.length()) {
            int lineEnd = text.indexOf('\n', index);
            String line = (lineEnd < 0 ? text.substring(index) : text.substring(index, lineEnd)).trim();
            if (line.startsWith("#")) {
                return index;
            }
            if (lineEnd < 0) {
                break;
            }
            index = lineEnd + 1;
        }
        return text.trim().startsWith("#") ? text.indexOf('#') : -1;
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
