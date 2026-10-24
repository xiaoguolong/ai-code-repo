package com.aicode.enterprise.controller;

import cn.dev33.satoken.exception.NotLoginException;
import com.aicode.core.domain.exception.AuthenticationException;
import com.aicode.core.domain.exception.ChatModelException;
import com.aicode.enterprise.domain.exception.ConflictException;
import com.aicode.core.domain.exception.EmbeddingException;
import com.aicode.core.domain.exception.FileStorageException;
import com.aicode.enterprise.domain.exception.ForbiddenException;
import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.core.domain.exception.NotFoundException;
import com.aicode.core.domain.exception.OcrException;
import com.aicode.enterprise.domain.exception.SsoUnavailableException;
import com.aicode.enterprise.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常映射。客户端永远看不到堆栈和密钥。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Bean Validation 失败 → 400。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("请求参数不合法");
        return ResponseEntity.badRequest().body(ApiResponse.error("VALIDATION_ERROR", message));
    }

    /**
     * 用例层入参失败 → 400。
     */
    @ExceptionHandler(InvalidChatRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalid(InvalidChatRequestException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error("VALIDATION_ERROR", ex.getMessage()));
    }

    /**
     * 未登录 → 401。
     */
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotLogin(NotLoginException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("UNAUTHORIZED", "未登录或登录已过期"));
    }

    /**
     * 认证失败（用户名/密码错误）→ 401。
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("UNAUTHORIZED", "用户名或密码错误"));
    }

    /**
     * 越权 → 403。
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(ForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("FORBIDDEN", "无权访问该资源"));
    }

    /**
     * 资源不存在 → 404。
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("NOT_FOUND", "资源不存在"));
    }

    /**
     * 资源冲突 → 409。
     */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("CONFLICT", ex.getMessage()));
    }

    /**
     * 模型失败 → 502。
     */
    @ExceptionHandler(ChatModelException.class)
    public ResponseEntity<ApiResponse<Void>> handleModel(ChatModelException ex) {
        log.warn("chat model failed: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error("CHAT_MODEL_ERROR", "模型调用失败"));
    }

    /**
     * 向量化失败 → 502。
     */
    @ExceptionHandler(EmbeddingException.class)
    public ResponseEntity<ApiResponse<Void>> handleEmbedding(EmbeddingException ex) {
        log.warn("embedding failed: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error("EMBEDDING_ERROR", "向量化服务不可用"));
    }

    /**
     * OCR 失败 → 502。
     */
    @ExceptionHandler(OcrException.class)
    public ResponseEntity<ApiResponse<Void>> handleOcr(OcrException ex) {
        log.warn("ocr failed: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error("OCR_ERROR", "OCR 识别服务不可用"));
    }

    /**
     * 文件存储失败 → 502。
     */
    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<ApiResponse<Void>> handleFile(FileStorageException ex) {
        log.warn("file storage failed: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error("FILE_STORAGE_ERROR", "文件服务不可用"));
    }

    /**
     * 统一身份源不可用 → 503（Week 21）。
     *
     * <p>刻意不映射成 401：平台超时/5xx 与「密码错了」是两种故障，
     * 混用会让用户反复改密码而真正的问题在平台侧。</p>
     */
    @ExceptionHandler(SsoUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleSsoUnavailable(SsoUnavailableException ex) {
        log.warn("[sso] unified identity unavailable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("AUTH_SSO_UNAVAILABLE", "统一身份服务暂不可用，请稍后重试"));
    }

    /**
     * 未预期错误 → 500。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception ex) {
        log.error("unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "系统繁忙，请稍后重试"));
    }
}
