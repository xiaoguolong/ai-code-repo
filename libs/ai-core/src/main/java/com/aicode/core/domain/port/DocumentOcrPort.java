package com.aicode.core.domain.port;

import com.aicode.core.domain.exception.OcrException;
import com.aicode.core.domain.model.OcrFileType;
import com.aicode.core.domain.model.OcrResult;

/**
 * 出站端口：文档 OCR。把图片/PDF 识别为 markdown 文本及图片映射。实现类负责厂商协议。
 */
public interface DocumentOcrPort {

    /**
     * 对图片或 PDF 进行 OCR。
     *
     * @param file     图片/PDF 的 URL 或 Base64
     * @param fileType 文件类型；URL 时可传 null 由服务端推断，Base64 必传
     * @return 识别出的 markdown 文本与图片占位符映射，非 null
     * @throws OcrException 密钥缺失、网络或协议错误
     */
    OcrResult recognize(String file, OcrFileType fileType);
}
