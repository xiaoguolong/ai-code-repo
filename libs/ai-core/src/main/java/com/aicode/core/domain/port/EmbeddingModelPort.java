package com.aicode.core.domain.port;

import com.aicode.core.domain.exception.EmbeddingException;

/**
 * 出站端口：文本向量化。实现类负责厂商协议（OpenAI 兼容 / 离线哈希），领域不依赖 SDK。
 * 向量维度由实现与向量库共同约定，生产 pgvector 列固定为 1024。
 */
public interface EmbeddingModelPort {

    /**
     * 把一段文本转换为归一化向量。
     *
     * @param text 待向量化文本，非空
     * @return 固定维度的浮点向量，长度与向量库列一致
     * @throws EmbeddingException 密钥缺失、网络或协议错误
     */
    float[] embed(String text);
}
