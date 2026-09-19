package com.aicode.core.domain.model;

/**
 * 文件存储后端类型。本地已落地，OSS/OBS 仅接口预留。
 */
public enum StorageType {

    /** 本地磁盘目录。 */
    LOCAL("local"),

    /** 阿里云 OSS（接口预留，未落地）。 */
    OSS("oss"),

    /** 华为云 OBS（接口预留，未落地）。 */
    OBS("obs");

    private final String value;

    StorageType(String value) {
        this.value = value;
    }

    /**
     * @return 落库时使用的类型字符串
     */
    public String value() {
        return value;
    }
}
