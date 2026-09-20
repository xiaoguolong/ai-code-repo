package com.aicode.core.domain.port;

import com.aicode.core.domain.model.MemoryHit;
import com.aicode.core.domain.model.MemoryRecord;

import java.util.List;

/**
 * 出站端口：长期记忆。保存历史任务结论，并支持按语义相似度检索。
 * 实现可替换：内存向量默认，后续可落向量库。
 */
public interface LongTermMemoryPort {

    /**
     * 保存一条长期记忆。
     *
     * @param record 任务 + 结论记录
     */
    void save(MemoryRecord record);

    /**
     * 按语义相似度检索历史记忆。
     *
     * @param query 查询文本（通常是当前任务），空白时返回空列表
     * @param topK  返回条数上限
     * @return 正相关的命中，按相似度降序；无命中返回空列表，永不返回 null
     */
    List<MemoryHit> search(String query, int topK);
}
