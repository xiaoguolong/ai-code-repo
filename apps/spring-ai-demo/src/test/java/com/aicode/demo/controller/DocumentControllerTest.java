package com.aicode.demo.controller;

import com.aicode.demo.application.IngestDocumentCommand;
import com.aicode.demo.application.IngestDocumentOutcome;
import com.aicode.demo.application.IngestDocumentUseCase;
import com.aicode.demo.application.ListDocumentsUseCase;
import com.aicode.demo.domain.model.DocumentMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 文档上传与列表接口。
 */
@WebMvcTest(controllers = DocumentController.class)
@Import(GlobalExceptionHandler.class)
@ActiveProfiles("test")
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IngestDocumentUseCase ingestDocumentUseCase;

    @MockitoBean
    private ListDocumentsUseCase listDocumentsUseCase;

    @Test
    void shouldRejectBlankContent() throws Exception {
        mockMvc.perform(post("/api/v1/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"doc\",\"content\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldReturnDocumentData() throws Exception {
        when(ingestDocumentUseCase.ingest(any(IngestDocumentCommand.class)))
                .thenReturn(new IngestDocumentOutcome("doc-1", "知识库文档", 3));

        mockMvc.perform(post("/api/v1/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"知识库文档\",\"content\":\"hello world\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.documentId").value("doc-1"))
                .andExpect(jsonPath("$.data.chunkCount").value(3));
    }

    @Test
    void shouldListDocuments() throws Exception {
        when(listDocumentsUseCase.list()).thenReturn(List.of(
                new DocumentMetadata("doc-1", "知识库文档", Instant.parse("2026-09-03T00:00:00Z"))
        ));

        mockMvc.perform(get("/api/v1/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documents[0].documentId").value("doc-1"))
                .andExpect(jsonPath("$.data.documents[0].name").value("知识库文档"));
    }
}
