package com.aicode.enterprise.infrastructure.persistence;

import com.aicode.enterprise.domain.model.KnowledgeBase;
import com.aicode.enterprise.domain.port.KnowledgeBasePort;
import com.aicode.enterprise.infrastructure.persistence.entity.KnowledgeBaseEntity;
import com.aicode.enterprise.infrastructure.persistence.mapper.KnowledgeBaseMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 基于 Fluent-MyBatis 的知识库持久化适配器。
 */
@Component
public class MyBatisKnowledgeBaseAdapter implements KnowledgeBasePort {

    private final KnowledgeBaseMapper knowledgeBaseMapper;

    public MyBatisKnowledgeBaseAdapter(KnowledgeBaseMapper knowledgeBaseMapper) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
    }

    @Override
    @Transactional
    public KnowledgeBase create(KnowledgeBase knowledgeBase) {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity(
                knowledgeBase.userId(),
                knowledgeBase.name(),
                knowledgeBase.description(),
                knowledgeBase.createdAt()
        );
        knowledgeBaseMapper.insert(entity);
        return new KnowledgeBase(
                entity.getId(),
                entity.getUserId(),
                entity.getName(),
                entity.getDescription(),
                entity.getCreatedAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<KnowledgeBase> findById(long id) {
        return knowledgeBaseMapper.listByMap(false, Map.of("id", id)).stream()
                .findFirst()
                .map(this::toModel);
    }

    @Override
    @Transactional(readOnly = true)
    public List<KnowledgeBase> listByUser(long userId) {
        return knowledgeBaseMapper.listByMap(false, Map.of("userId", userId)).stream()
                .sorted(Comparator.comparing(KnowledgeBaseEntity::getId))
                .map(this::toModel)
                .toList();
    }

    @Override
    @Transactional
    public void update(KnowledgeBase knowledgeBase) {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(knowledgeBase.id());
        entity.setName(knowledgeBase.name());
        entity.setDescription(knowledgeBase.description());
        knowledgeBaseMapper.updateById(entity);
    }

    @Override
    @Transactional
    public void deleteById(long id) {
        knowledgeBaseMapper.deleteById(id);
    }

    private KnowledgeBase toModel(KnowledgeBaseEntity entity) {
        return new KnowledgeBase(
                entity.getId(),
                entity.getUserId(),
                entity.getName(),
                entity.getDescription(),
                entity.getCreatedAt()
        );
    }
}
