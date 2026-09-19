package com.aicode.patient.controller;

import com.aicode.patient.domain.exception.AgentExecutionException;
import com.aicode.patient.domain.exception.AgentLoopExceededException;
import com.aicode.core.domain.exception.ChatModelException;
import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.core.domain.exception.ToolExecutionException;
import com.aicode.patient.dto.ApiResponse;
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
     * 模型失败 → 502。
     */
    @ExceptionHandler(ChatModelException.class)
    public ResponseEntity<ApiResponse<Void>> handleModel(ChatModelException ex) {
        log.warn("chat model failed: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error("CHAT_MODEL_ERROR", "模型调用失败"));
    }

    /**
     * 工具执行失败（未知工具/参数非法）→ 502。
     */
    @ExceptionHandler(ToolExecutionException.class)
    public ResponseEntity<ApiResponse<Void>> handleTool(ToolExecutionException ex) {
        log.warn("tool execution failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error("TOOL_EXECUTION_ERROR", "工具执行失败"));
    }

    /**
     * Agent 超迭代 → 500。
     */
    @ExceptionHandler(AgentLoopExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleLoopExceeded(AgentLoopExceededException ex) {
        log.warn("agent loop exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("AGENT_LOOP_EXCEEDED", "Agent 未能在限定步数内完成"));
    }

    /**
     * Agent 执行异常 → 500。
     */
    @ExceptionHandler(AgentExecutionException.class)
    public ResponseEntity<ApiResponse<Void>> handleAgentExecution(AgentExecutionException ex) {
        log.warn("agent execution failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("AGENT_EXECUTION_ERROR", "Agent 执行失败"));
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
