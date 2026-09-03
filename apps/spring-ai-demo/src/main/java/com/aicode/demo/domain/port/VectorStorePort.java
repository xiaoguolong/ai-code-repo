package com.aicode.demo.domain.port;

import com.aicode.demo.domain.model.DocumentChunk;
import com.aicode.demo.domain.model.VectorSearchHit;

import java.util.List;

/**
 * 出站端口：向量库。负责切片与向量的存储、相似度检索。实现类 pgvector / 内存可替换。
 */
public interface VectorStorePort {

    /**
     * 存入一个切片及其向量。
     *
     * @param chunk  切片（含归属文档与序号）
     * @param vector 切片向量，维度与库约定一致
     */
    void put(DocumentChunk chunk, float[] vector);

    /**
     * 按余弦相似度检索最相关的切片。
     *
     * @param query 查询向量
     * @param topK  返回条数上限
     * @return 按相似度降序的命中列表；空库返回空列表，永不返回 null
     */
    List<VectorSearchHit> search(float[] query, int topK);
}
