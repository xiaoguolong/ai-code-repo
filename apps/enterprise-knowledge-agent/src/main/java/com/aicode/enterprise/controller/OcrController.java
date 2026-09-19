package com.aicode.enterprise.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.aicode.enterprise.application.RecognizeOcrUseCase;
import com.aicode.core.domain.exception.FileStorageException;
import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.core.domain.model.OcrFileType;
import com.aicode.enterprise.dto.ApiResponse;
import com.aicode.enterprise.dto.OcrRecognizeRequest;
import com.aicode.enterprise.dto.OcrRecognizeResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;

/**
 * OCR 识别接入。识别编排与图片占位符清理在用例层。
 */
@RestController
@RequestMapping("/api/v1/ocr")
public class OcrController {

    private final RecognizeOcrUseCase recognizeOcrUseCase;

    public OcrController(RecognizeOcrUseCase recognizeOcrUseCase) {
        this.recognizeOcrUseCase = recognizeOcrUseCase;
    }

    /**
     * 识别图片/PDF 文本（URL 或 Base64）。
     */
    @PostMapping
    public ApiResponse<OcrRecognizeResponse> recognize(@Valid @RequestBody OcrRecognizeRequest request) {
        Long userId = StpUtil.getLoginIdAsLong();
        OcrFileType fileType = OcrFileType.parseNullable(request.fileType());
        String text = recognizeOcrUseCase.recognize(userId, request.file(), fileType);
        return ApiResponse.success(new OcrRecognizeResponse(text));
    }

    /**
     * 上传本地图片/PDF 识别（multipart 表单）。
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<OcrRecognizeResponse> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidChatRequestException("file must not be empty");
        }
        Long userId = StpUtil.getLoginIdAsLong();
        String base64;
        try {
            base64 = Base64.getEncoder().encodeToString(file.getBytes());
        } catch (IOException ex) {
            throw new FileStorageException("failed to read uploaded file", ex);
        }
        OcrFileType fileType = OcrFileType.inferFromFileName(file.getOriginalFilename());
        String text = recognizeOcrUseCase.recognize(userId, base64, fileType);
        return ApiResponse.success(new OcrRecognizeResponse(text));
    }
}
