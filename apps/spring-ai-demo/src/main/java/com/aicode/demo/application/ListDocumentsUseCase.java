package com.aicode.demo.application;

import com.aicode.demo.domain.model.DocumentMetadata;
import com.aicode.demo.domain.port.DocumentPort;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 列出已入库文档元数据。
 */
@Service
public class ListDocumentsUseCase {

    private final DocumentPort documentPort;

    public ListDocumentsUseCase(DocumentPort documentPort) {
        this.documentPort = documentPort;
    }

    /**
     * 返回全部文档元数据。
     */
    public List<DocumentMetadata> list() {
        return documentPort.list();
    }
}
