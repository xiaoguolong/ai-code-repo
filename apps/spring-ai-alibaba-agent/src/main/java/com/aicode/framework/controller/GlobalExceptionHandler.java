package com.aicode.framework.controller;

import cn.dev33.satoken.exception.NotLoginException;
import com.aicode.core.domain.exception.AuthenticationException;
import com.aicode.core.domain.exception.ChatModelException;
import com.aicode.core.domain.exception.GuardrailViolationException;
import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.core.domain.exception.ToolExecutionException;
import com.aicode.framework.domain.exception.AgentExecutionException;
import com.aicode.framework.domain.exception.AgentLoopExceededException;
import com.aicode.framework.dto.ApiResponse;
import com.aicode.framework.platform.domain.exception.PlatformAccessDeniedException;
import com.aicode.framework.platform.domain.exception.PlatformAgentDisabledException;
import com.aicode.framework.platform.domain.exception.PlatformConflictException;
import com.aicode.framework.platform.domain.exception.PlatformNotFoundException;
import com.aicode.framework.workflow.domain.exception.WorkflowNotFoundException;
import com.aicode.framework.workflow.domain.exception.WorkflowNotPendingException;
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
     * 认证失败 → 401。
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("UNAUTHORIZED", "用户名或密码错误"));
    }

    /**
     * Guardrail 内容安全违例 → 400。
     */
    @ExceptionHandler(GuardrailViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleGuardrailViolation(GuardrailViolationException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error("GUARDRAIL_VIOLATION", ex.getMessage()));
    }

    /**
     * 平台权限不足 → 403。
     */
    @ExceptionHandler(PlatformAccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handlePlatformAccessDenied(PlatformAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("FORBIDDEN", ex.getMessage()));
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
     * 工具执行失败（未知工具 / 参数非法）→ 502。
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
     * Agent 执行异常（模型输出空白等）→ 500。
     */
    @ExceptionHandler(AgentExecutionException.class)
    public ResponseEntity<ApiResponse<Void>> handleAgentExecution(AgentExecutionException ex) {
        log.warn("agent execution failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("AGENT_EXECUTION_ERROR", "Agent 执行失败"));
    }

    /**
     * Workflow 不存在 → 404。
     */
    @ExceptionHandler(WorkflowNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleWorkflowNotFound(WorkflowNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("WORKFLOW_NOT_FOUND", ex.getMessage()));
    }

    /**
     * Workflow 非待审状态 → 409。
     */
    @ExceptionHandler(WorkflowNotPendingException.class)
    public ResponseEntity<ApiResponse<Void>> handleWorkflowNotPending(WorkflowNotPendingException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("WORKFLOW_NOT_PENDING", ex.getMessage()));
    }

    /**
     * 平台资源不存在 → 404。
     */
    @ExceptionHandler(PlatformNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handlePlatformNotFound(PlatformNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("PLATFORM_NOT_FOUND", ex.getMessage()));
    }

    /**
     * 平台资源冲突 → 409。
     */
    @ExceptionHandler(PlatformConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handlePlatformConflict(PlatformConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("PLATFORM_CONFLICT", ex.getMessage()));
    }

    /**
     * Agent 已禁用 → 400。
     */
    @ExceptionHandler(PlatformAgentDisabledException.class)
    public ResponseEntity<ApiResponse<Void>> handlePlatformAgentDisabled(PlatformAgentDisabledException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error("AGENT_DISABLED", ex.getMessage()));
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
