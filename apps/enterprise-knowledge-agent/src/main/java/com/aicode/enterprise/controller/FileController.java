package com.aicode.enterprise.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.aicode.enterprise.application.GetFileUseCase;
import com.aicode.enterprise.application.UploadFileUseCase;
import com.aicode.core.domain.exception.FileStorageException;
import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.core.domain.model.FileContent;
import com.aicode.core.domain.model.FileReference;
import com.aicode.enterprise.dto.ApiResponse;
import com.aicode.enterprise.dto.FileUploadResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 文件上传/访问接入。上传需登录，访问公开（供页面 img 使用）。
 */
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final UploadFileUseCase uploadFileUseCase;
    private final GetFileUseCase getFileUseCase;

    public FileController(UploadFileUseCase uploadFileUseCase, GetFileUseCase getFileUseCase) {
        this.uploadFileUseCase = uploadFileUseCase;
        this.getFileUseCase = getFileUseCase;
    }

    /**
     * 上传文件。
     *
     * @return 200 带 fileId 与访问路径；空文件 400
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileUploadResponse> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidChatRequestException("file must not be empty");
        }
        Long userId = StpUtil.getLoginIdAsLong();
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new FileStorageException("failed to read uploaded file", ex);
        }
        FileReference reference = uploadFileUseCase.upload(userId, file.getOriginalFilename(), file.getContentType(), bytes);
        return ApiResponse.success(FileUploadResponse.from(reference));
    }

    /**
     * 取回文件内容（公开）。
     */
    @GetMapping("/{fileId}")
    public ResponseEntity<byte[]> download(@PathVariable String fileId) {
        FileContent content = getFileUseCase.load(fileId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(resolveMediaType(content.contentType()));
        return new ResponseEntity<>(content.bytes(), headers, HttpStatus.OK);
    }

    private MediaType resolveMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
