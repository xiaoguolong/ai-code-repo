package com.aicode.enterprise.domain.port;

import com.aicode.enterprise.domain.model.KnowledgeBase;

import java.util.List;
import java.util.Optional;

/**
 * 出站端口：知识库持久化。归属校验由用例层依据 userId 判定。
 */
public interface KnowledgeBasePort {

    /**
     * 创建知识库，返回带主键的实体。
     *
     * @param knowledgeBase 知识库，id 可为 null
     * @return 持久化后的知识库（含 id）
     */
    KnowledgeBase create(KnowledgeBase knowledgeBase);

    /**
     * 按主键查询。
     */
    Optional<KnowledgeBase> findById(long id);

    /**
     * 列出某用户的全部知识库。
     */
    List<KnowledgeBase> listByUser(long userId);

    /**
     * 更新名称与描述。
     */
    void update(KnowledgeBase knowledgeBase);

    /**
     * 按主键删除。
     */
    void deleteById(long id);
}
