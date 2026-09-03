package com.aicode.demo.controller;

import com.aicode.demo.application.RecognizeOcrUseCase;
import com.aicode.demo.domain.model.OcrFileType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OCR 识别接口。
 */
@WebMvcTest(controllers = OcrController.class)
@Import(GlobalExceptionHandler.class)
@ActiveProfiles("test")
class OcrControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecognizeOcrUseCase recognizeOcrUseCase;

    @Test
    void shouldReturnText() throws Exception {
        when(recognizeOcrUseCase.recognize(eq("https://example.com/a.jpg"), eq(OcrFileType.IMAGE)))
                .thenReturn("识别文本");

        mockMvc.perform(post("/api/v1/ocr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"file\":\"https://example.com/a.jpg\",\"fileType\":\"image\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.text").value("识别文本"));
    }

    @Test
    void shouldRejectBlankFile() throws Exception {
        mockMvc.perform(post("/api/v1/ocr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"file\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldRejectInvalidFileType() throws Exception {
        mockMvc.perform(post("/api/v1/ocr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"file\":\"https://example.com/a.jpg\",\"fileType\":\"video\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldUploadPdfAndReturnText() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "PDFDATA".getBytes());
        when(recognizeOcrUseCase.recognize(anyString(), eq(OcrFileType.PDF))).thenReturn("识别文本");

        mockMvc.perform(multipart("/api/v1/ocr/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.text").value("识别文本"));
    }

    @Test
    void shouldRejectEmptyUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/api/v1/ocr/upload").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
