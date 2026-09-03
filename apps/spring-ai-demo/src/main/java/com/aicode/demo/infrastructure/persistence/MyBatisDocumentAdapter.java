package com.aicode.demo.infrastructure.persistence;

import com.aicode.demo.domain.model.DocumentMetadata;
import com.aicode.demo.domain.port.DocumentPort;
import com.aicode.demo.infrastructure.persistence.entity.DocumentEntity;
import com.aicode.demo.infrastructure.persistence.mapper.DocumentMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
        documentMapper.insert(new DocumentEntity(metadata.documentId(), metadata.name(), metadata.createdAt()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentMetadata> list() {
        return documentMapper.listByMapAndDefault(Map.of()).stream()
                .map(entity -> new DocumentMetadata(entity.getDocumentId(), entity.getName(), entity.getCreatedAt()))
                .toList();
    }
}
