package com.aicode.demo.controller;

import com.aicode.demo.domain.exception.ChatModelException;
import com.aicode.demo.domain.exception.InvalidChatRequestException;
import com.aicode.demo.dto.ApiResponse;
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
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("invalid request");
        return ResponseEntity.badRequest().body(ApiResponse.error("validation_error", message));
    }

    /**
     * 用例层入参失败 → 400。
     */
    @ExceptionHandler(InvalidChatRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalid(InvalidChatRequestException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error("validation_error", ex.getMessage()));
    }

    /**
     * 模型失败 → 502，对外文案固定，细节只打服务端日志。
     */
    @ExceptionHandler(ChatModelException.class)
    public ResponseEntity<ApiResponse<Void>> handleModel(ChatModelException ex) {
        // 完整堆栈只打服务端日志，帮助定位根因；客户端仍只见固定文案。
        log.warn("chat model failed: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error("chat_model_error", "LLM call failed"));
    }

    /**
     * 未预期错误 → 500。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception ex) {
        log.error("unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("internal_error", "An error occurred. Please try again."));
    }
}
