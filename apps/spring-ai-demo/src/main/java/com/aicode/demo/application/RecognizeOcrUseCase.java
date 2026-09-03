package com.aicode.demo.application;

import com.aicode.demo.domain.exception.InvalidChatRequestException;
import com.aicode.demo.domain.model.OcrFileType;
import com.aicode.demo.domain.port.DocumentOcrPort;
import org.springframework.stereotype.Service;

/**
 * OCR 识别用例：校验入参后委派给 OCR 端口。
 */
@Service
public class RecognizeOcrUseCase {

    private final DocumentOcrPort ocrPort;

    public RecognizeOcrUseCase(DocumentOcrPort ocrPort) {
        this.ocrPort = ocrPort;
    }

    /**
     * 识别一张图片或一个 PDF 的文本。
     *
     * @param file     图片/PDF 的 URL 或 Base64
     * @param fileType 文件类型；URL 时可 null
     * @return 识别文本
     * @throws InvalidChatRequestException 文件标识空白
     */
    public String recognize(String file, OcrFileType fileType) {
        if (file == null || file.isBlank()) {
            throw new InvalidChatRequestException("file must not be blank");
        }
        return ocrPort.recognize(file, fileType);
    }
}
