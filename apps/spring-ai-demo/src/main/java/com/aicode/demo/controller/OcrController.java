package com.aicode.demo.controller;

import com.aicode.demo.application.RecognizeOcrUseCase;
import com.aicode.demo.domain.exception.InvalidChatRequestException;
import com.aicode.demo.domain.exception.OcrException;
import com.aicode.demo.domain.model.OcrFileType;
import com.aicode.demo.dto.ApiResponse;
import com.aicode.demo.dto.OcrRecognizeRequest;
import com.aicode.demo.dto.OcrRecognizeResponse;
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
 * OCR 识别接入。只做校验与协议转换，识别编排在用例层。
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
     *
     * @return 200 带 data.text；校验失败 400
     */
    @PostMapping
    public ApiResponse<OcrRecognizeResponse> recognize(@Valid @RequestBody OcrRecognizeRequest request) {
        OcrFileType fileType = OcrFileType.parseNullable(request.fileType());
        String text = recognizeOcrUseCase.recognize(request.file(), fileType);
        return ApiResponse.success(new OcrRecognizeResponse(text));
    }

    /**
     * 上传本地图片/PDF 识别（multipart 表单）。
     *
     * @param file 文件，非空；类型按文件扩展名推断
     * @return 200 带 data.text；空文件 400；读取失败 502
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<OcrRecognizeResponse> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidChatRequestException("file must not be empty");
        }
        String base64;
        try {
            base64 = Base64.getEncoder().encodeToString(file.getBytes());
        } catch (IOException ex) {
            throw new OcrException("failed to read uploaded file", ex);
        }
        OcrFileType fileType = OcrFileType.inferFromFileName(file.getOriginalFilename());
        String text = recognizeOcrUseCase.recognize(base64, fileType);
        return ApiResponse.success(new OcrRecognizeResponse(text));
    }
}
