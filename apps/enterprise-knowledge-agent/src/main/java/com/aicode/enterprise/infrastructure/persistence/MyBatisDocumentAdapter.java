package com.aicode.enterprise.infrastructure.persistence;

import com.aicode.enterprise.domain.model.DocumentMetadata;
import com.aicode.enterprise.domain.port.DocumentPort;
import com.aicode.enterprise.infrastructure.persistence.entity.DocumentEntity;
import com.aicode.enterprise.infrastructure.persistence.mapper.DocumentMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 基于 Fluent-MyBatis 的文档元数据适配器。
 */
@Component
public class MyBatisDocumentAdapter implements DocumentPort {

    private final DocumentMapper documentMapper;

    public MyBatisDocumentAdapter(DocumentMapper documentMapper) {
        this.documentMapper = documentMapper;
    }

    @Override
    @Transactional
    public void create(DocumentMetadata metadata) {
        documentMapper.insert(new DocumentEntity(
                metadata.documentId(),
                metadata.knowledgeBaseId(),
                metadata.name(),
                metadata.chunkCount(),
                metadata.createdAt()
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentMetadata> listByKnowledgeBase(long knowledgeBaseId) {
        return documentMapper.listByMap(false, Map.of("knowledgeBaseId", knowledgeBaseId)).stream()
                .sorted(Comparator.comparing(DocumentEntity::getId))
                .map(entity -> new DocumentMetadata(
                        entity.getDocumentId(),
                        entity.getKnowledgeBaseId(),
                        entity.getName(),
                        entity.getChunkCount() == null ? 0 : entity.getChunkCount(),
                        entity.getCreatedAt()
                ))
                .toList();
    }

    @Override
    @Transactional
    public void deleteByDocumentId(String documentId) {
        documentMapper.deleteByMap(false, Map.of("documentId", documentId));
    }
}
