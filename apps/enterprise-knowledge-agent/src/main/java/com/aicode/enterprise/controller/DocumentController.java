package com.aicode.enterprise.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.aicode.enterprise.application.DeleteDocumentUseCase;
import com.aicode.enterprise.application.IngestDocumentCommand;
import com.aicode.enterprise.application.IngestDocumentUseCase;
import com.aicode.enterprise.application.ListDocumentsUseCase;
import com.aicode.enterprise.dto.ApiResponse;
import com.aicode.enterprise.dto.DocumentItemDto;
import com.aicode.enterprise.dto.IngestDocumentRequest;
import com.aicode.enterprise.dto.IngestDocumentResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文档接入。只做校验、取当前用户与协议转换，切片与向量化在用例层。
 */
@RestController
@RequestMapping("/api/v1/knowledge-bases/{kbId}/documents")
public class DocumentController {

    private final IngestDocumentUseCase ingestDocumentUseCase;
    private final ListDocumentsUseCase listDocumentsUseCase;
    private final DeleteDocumentUseCase deleteDocumentUseCase;

    public DocumentController(
            IngestDocumentUseCase ingestDocumentUseCase,
            ListDocumentsUseCase listDocumentsUseCase,
            DeleteDocumentUseCase deleteDocumentUseCase
    ) {
        this.ingestDocumentUseCase = ingestDocumentUseCase;
        this.listDocumentsUseCase = listDocumentsUseCase;
        this.deleteDocumentUseCase = deleteDocumentUseCase;
    }

    /**
     * 上传纯文本文档，解析切片并向量化入库。
     */
    @PostMapping
    public ApiResponse<IngestDocumentResponse> ingest(@PathVariable Long kbId, @Valid @RequestBody IngestDocumentRequest request) {
        Long userId = StpUtil.getLoginIdAsLong();
        IngestDocumentCommand command = new IngestDocumentCommand(kbId, userId, request.name(), request.content());
        return ApiResponse.success(IngestDocumentResponse.from(ingestDocumentUseCase.ingest(command)));
    }

    /**
     * 列出某知识库文档。
     */
    @GetMapping
    public ApiResponse<List<DocumentItemDto>> list(@PathVariable Long kbId) {
        Long userId = StpUtil.getLoginIdAsLong();
        List<DocumentItemDto> data = listDocumentsUseCase.list(userId, kbId).stream()
                .map(DocumentItemDto::from)
                .toList();
        return ApiResponse.success(data);
    }

    /**
     * 删除某文档。
     */
    @DeleteMapping("/{docId}")
    public ApiResponse<Void> delete(@PathVariable Long kbId, @PathVariable String docId) {
        Long userId = StpUtil.getLoginIdAsLong();
        deleteDocumentUseCase.delete(userId, kbId, docId);
        return ApiResponse.success(null);
    }
}
