package com.aicode.enterprise.domain;

import com.aicode.enterprise.domain.model.FileReference;
import com.aicode.enterprise.domain.model.OcrResult;
import com.aicode.enterprise.domain.port.FileStoragePort;
import com.aicode.enterprise.domain.port.ImageDownloadPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OCR 图片占位符清理器。把 markdown 里的图片占位符替换为文件服务的可访问路径：
 * 解析占位符 src → 从 images 映射取临时 URL → 下载 → 上传文件服务 → 拼访问路径 → 替换。
 * 单张图片失败时保留原占位符，不阻断 OCR 主流程。
 */
public class OcrMarkdownImageProcessor {

    private static final Logger log = LoggerFactory.getLogger(OcrMarkdownImageProcessor.class);

    /**
     * 匹配图片占位符并捕获 src 值。兼容千帆两种格式：
     * {@code <div style="..."><img src="imgs/xxx.jpg" .../></div>} 与裸 {@code <img src="...">}。
     */
    private static final Pattern IMG_PATTERN = Pattern.compile(
            "<div[^>]*>\\s*<img\\s+[^>]*src=\"([^\"]+)\"[^>]*>\\s*</div>|<img\\s+[^>]*src=\"([^\"]+)\"[^>]*>"
    );

    private final ImageDownloadPort imageDownloadPort;
    private final FileStoragePort fileStoragePort;
    private final FileUrlAssembler urlAssembler;

    public OcrMarkdownImageProcessor(
            ImageDownloadPort imageDownloadPort,
            FileStoragePort fileStoragePort,
            FileUrlAssembler urlAssembler
    ) {
        this.imageDownloadPort = imageDownloadPort;
        this.fileStoragePort = fileStoragePort;
        this.urlAssembler = urlAssembler;
    }

    /**
     * 替换 markdown 中的图片占位符。
     *
     * @param result OCR 结果（markdown + 图片映射）
     * @param userId 上传者，用于文件归属
     * @return 替换后的 markdown；无图片或下载失败时保留原占位符
     */
    public String clean(OcrResult result, Long userId) {
        Matcher matcher = IMG_PATTERN.matcher(result.markdown());
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String src = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            String replacement = matcher.group();
            String imageUrl = result.images().get(src);
            if (imageUrl != null) {
                replacement = resolveImage(src, imageUrl, userId, replacement);
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String resolveImage(String src, String imageUrl, Long userId, String fallback) {
        try {
            byte[] bytes = imageDownloadPort.download(imageUrl);
            FileReference reference = fileStoragePort.store(userId, src, null, bytes);
            return "![img](" + urlAssembler.url(reference.fileId()) + ")";
        } catch (RuntimeException ex) {
            log.warn("failed to resolve OCR image placeholder: {}", src);
            return fallback;
        }
    }
}
