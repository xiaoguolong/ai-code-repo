package com.aicode.demo.controller;

import com.aicode.demo.application.IngestDocumentCommand;
import com.aicode.demo.application.IngestDocumentUseCase;
import com.aicode.demo.application.ListDocumentsUseCase;
import com.aicode.demo.dto.ApiResponse;
import com.aicode.demo.dto.DocumentListResponse;
import com.aicode.demo.dto.IngestDocumentRequest;
import com.aicode.demo.dto.IngestDocumentResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文档接入。只做校验与协议转换，切片与向量化在用例层。
 */
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final IngestDocumentUseCase ingestDocumentUseCase;
    private final ListDocumentsUseCase listDocumentsUseCase;

    public DocumentController(IngestDocumentUseCase ingestDocumentUseCase, ListDocumentsUseCase listDocumentsUseCase) {
        this.ingestDocumentUseCase = ingestDocumentUseCase;
        this.listDocumentsUseCase = listDocumentsUseCase;
    }

    /**
     * 上传纯文本文档，解析切片并向量化入库。
     *
     * @return 200 带 documentId 与 chunkCount；校验失败 400
     */
    @PostMapping
    public ApiResponse<IngestDocumentResponse> ingest(@Valid @RequestBody IngestDocumentRequest request) {
        IngestDocumentCommand command = new IngestDocumentCommand(request.name(), request.content());
        return ApiResponse.success(IngestDocumentResponse.from(ingestDocumentUseCase.ingest(command)));
    }

    /**
     * 列出已入库文档。
     *
     * @return 200，data.documents 为文档列表
     */
    @GetMapping
    public ApiResponse<DocumentListResponse> list() {
        return ApiResponse.success(DocumentListResponse.from(listDocumentsUseCase.list()));
    }
}
