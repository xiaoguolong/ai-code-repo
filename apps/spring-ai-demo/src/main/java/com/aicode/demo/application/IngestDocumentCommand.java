package com.aicode.demo.application;

/**
 * 文档入库用例入参。
 *
 * @param name    文档名称
 * @param content 文档正文（纯文本）
 */
public record IngestDocumentCommand(String name, String content) {
}
