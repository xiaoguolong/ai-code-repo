package com.aicode.enterprise.domain.model;

/**
 * 入库步骤标识（Week 32）。顺序即执行顺序，落库用 {@link #apiKey()}。
 */
public enum IngestStepKey {

    /** 保存文件、算 hash、建作业 */
    UPLOAD("upload"),
    /** 千帆 OCR（PDF/图片 → markdown） */
    OCR("ocr"),
    /** LLM 清洗/纠错/分节（唯一带 prompt 的步骤） */
    CLEAN("clean"),
    /** 切片（TextChunker） */
    CHUNK("chunk"),
    /** 逐块向量化 */
    EMBED("embed"),
    /** 写 document + document_chunk */
    INDEX("index");

    private final String apiKey;

    IngestStepKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * @return 落库与接口展示用的 key
     */
    public String apiKey() {
        return apiKey;
    }

    /**
     * 从库里的 key 解析。
     *
     * @param key 原始 key
     * @return 对应枚举
     * @throws IllegalArgumentException 未知 key
     */
    public static IngestStepKey fromKey(String key) {
        for (IngestStepKey value : values()) {
            if (value.apiKey.equals(key)) {
                return value;
            }
        }
        throw new IllegalArgumentException("unknown ingest step key: " + key);
    }
}
