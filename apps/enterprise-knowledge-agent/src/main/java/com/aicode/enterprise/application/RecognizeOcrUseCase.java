package com.aicode.enterprise.application;

import com.aicode.enterprise.domain.OcrMarkdownImageProcessor;
import com.aicode.enterprise.domain.exception.InvalidChatRequestException;
import com.aicode.enterprise.domain.model.OcrFileType;
import com.aicode.enterprise.domain.model.OcrResult;
import com.aicode.enterprise.domain.port.DocumentOcrPort;
import org.springframework.stereotype.Service;

/**
 * OCR 识别用例：识别 → 清理图片占位符 → 返回可访问路径的 markdown。
 */
@Service
public class RecognizeOcrUseCase {

    private final DocumentOcrPort ocrPort;
    private final OcrMarkdownImageProcessor imageProcessor;

    public RecognizeOcrUseCase(DocumentOcrPort ocrPort, OcrMarkdownImageProcessor imageProcessor) {
        this.ocrPort = ocrPort;
        this.imageProcessor = imageProcessor;
    }

    /**
     * 识别图片/PDF 文本，并把图片占位符替换为文件服务访问路径。
     *
     * @param userId   当前用户，用于文件归属
     * @param file     图片/PDF 的 URL 或 Base64
     * @param fileType 文件类型；URL 时可 null
     * @return 清理后的 markdown 文本
     * @throws InvalidChatRequestException 文件标识空白
     */
    public String recognize(Long userId, String file, OcrFileType fileType) {
        if (file == null || file.isBlank()) {
            throw new InvalidChatRequestException("file must not be blank");
        }
        OcrResult result = ocrPort.recognize(file, fileType);
        return imageProcessor.clean(result, userId);
    }
}
