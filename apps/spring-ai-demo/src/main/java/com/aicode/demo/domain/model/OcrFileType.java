package com.aicode.demo.domain.model;

import com.aicode.demo.domain.exception.InvalidChatRequestException;

import java.util.Locale;

/**
 * OCR 输入文件类型。与千帆协议 fileType 对齐，避免魔法数字。
 */
public enum OcrFileType {

    /** PDF 文档。 */
    PDF(0),

    /** 图像文件。 */
    IMAGE(1);

    private final int apiValue;

    OcrFileType(int apiValue) {
        this.apiValue = apiValue;
    }

    /**
     * 千帆协议中的 fileType 取值。
     */
    public int apiValue() {
        return apiValue;
    }

    /**
     * 从协议字符串解析文件类型。空值返回 null（URL 时由服务端推断）。
     *
     * @param value pdf 或 image，大小写不敏感
     * @return 对应枚举；空值返回 null
     * @throws InvalidChatRequestException 非法取值
     */
    public static OcrFileType parseNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OcrFileType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidChatRequestException("invalid fileType: " + value);
        }
    }

    /**
     * 从文件名推断文件类型。pdf 后缀为 PDF，其余按图像处理。
     *
     * @param fileName 原始文件名，可为 null
     * @return PDF 或 IMAGE
     */
    public static OcrFileType inferFromFileName(String fileName) {
        if (fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            return PDF;
        }
        return IMAGE;
    }
}
