package com.aicode.demo.domain.port;

import com.aicode.demo.domain.exception.OcrException;
import com.aicode.demo.domain.model.OcrFileType;

/**
 * 出站端口：文档 OCR。把图片/PDF 识别为文本。实现类负责厂商协议，领域不依赖 SDK。
 */
public interface DocumentOcrPort {

    /**
     * 对图片或 PDF 进行 OCR，返回识别出的正文文本（markdown）。
     *
     * @param file     图片/PDF 的 URL 或 Base64
     * @param fileType 文件类型；URL 时可传 null 由服务端推断，Base64 必传
     * @return 识别文本，非 null
     * @throws OcrException 密钥缺失、网络或协议错误
     */
    String recognize(String file, OcrFileType fileType);
}
