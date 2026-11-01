package com.aicode.enterprise.dto;

import com.aicode.enterprise.domain.model.DocumentChunkView;

/**
 * 文档切片 DTO。
 *
 * @param chunkIndex 序号（0 起）
 * @param content    正文
 * @param chars      字符数（前端展示用）
 */
public record DocumentChunkDto(int chunkIndex, String content, int chars) {

    /**
     * @param view 领域视图
     * @return DTO
     */
    public static DocumentChunkDto from(DocumentChunkView view) {
        return new DocumentChunkDto(view.chunkIndex(), view.content(), view.content().length());
    }
}
