package com.aicode.framework.controller;

import cn.dev33.satoken.exception.NotLoginException;
import com.aicode.core.domain.exception.AuthenticationException;
import com.aicode.core.domain.exception.ChatModelException;
import com.aicode.core.domain.exception.GuardrailViolationException;
import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.core.domain.exception.ToolExecutionException;
import com.aicode.framework.domain.exception.AgentExecutionException;
import com.aicode.framework.domain.exception.AgentLoopExceededException;
import com.aicode.framework.dto.ApiErrorCode;
import com.aicode.framework.dto.ApiResponse;
import com.aicode.framework.infrastructure.logging.TraceIds;
import com.aicode.framework.platform.domain.exception.PlatformAccessDeniedException;
import com.aicode.framework.platform.domain.exception.PlatformAgentDisabledException;
import com.aicode.framework.platform.domain.exception.PlatformConflictException;
import com.aicode.framework.platform.domain.exception.PlatformNotFoundException;
import com.aicode.framework.workflow.domain.exception.WorkflowNotFoundException;
import com.aicode.framework.workflow.domain.exception.WorkflowNotPendingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常映射（Week 16 异常规范）。
 *
 * <p>规则：</p>
 * <ul>
 *   <li>错误码只能来自 {@link ApiErrorCode}，禁止字符串字面量；</li>
 *   <li>HTTP 状态取自错误码枚举，不在此处硬编码；</li>
 *   <li>响应统一带 {@code traceId}（来自 {@link TraceIds}），便于串联服务端日志；</li>
 *   <li>客户端永远看不到堆栈、SQL 报文、厂商原始响应与密钥。</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Bean Validation 失败 → 400 {@code VALIDATION_ERROR}。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse(ApiErrorCode.VALIDATION_ERROR.message());
        return respond(ApiErrorCode.VALIDATION_ERROR, message);
    }

    /**
     * 用例层入参失败 → 400 {@code VALIDATION_ERROR}。
     */
    @ExceptionHandler(InvalidChatRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalid(InvalidChatRequestException ex) {
        return respond(ApiErrorCode.VALIDATION_ERROR, ex.getMessage());
    }

    /**
     * 未登录或登录态过期 → 401 {@code UNAUTHORIZED}。
     */
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotLogin(NotLoginException ex) {
        return respond(ApiErrorCode.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED.message());
    }

    /**
     * 凭据错误 → 401 {@code UNAUTHORIZED}；不区分「用户不存在」与「密码错误」。
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return respond(ApiErrorCode.UNAUTHORIZED, "用户名或密码错误");
    }

    /**
     * Guardrail 内容安全违例 → 400 {@code GUARDRAIL_VIOLATION}。
     */
    @ExceptionHandler(GuardrailViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleGuardrailViolation(GuardrailViolationException ex) {
        return respond(ApiErrorCode.GUARDRAIL_VIOLATION, ex.getMessage());
    }

    /**
     * 平台权限不足（RBAC / 数据域） → 403 {@code FORBIDDEN}。
     */
    @ExceptionHandler(PlatformAccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handlePlatformAccessDenied(PlatformAccessDeniedException ex) {
        return respond(ApiErrorCode.FORBIDDEN, ex.getMessage());
    }

    /**
     * 模型调用失败 → 502 {@code CHAT_MODEL_ERROR}；原始报文只进服务端日志。
     */
    @ExceptionHandler(ChatModelException.class)
    public ResponseEntity<ApiResponse<Void>> handleModel(ChatModelException ex) {
        log.warn("[api] chat model failed traceId={} error={}", TraceIds.current(), ex.getMessage(), ex);
        return respond(ApiErrorCode.CHAT_MODEL_ERROR, ApiErrorCode.CHAT_MODEL_ERROR.message());
    }

    /**
     * 工具执行失败 → 502 {@code TOOL_EXECUTION_ERROR}。
     */
    @ExceptionHandler(ToolExecutionException.class)
    public ResponseEntity<ApiResponse<Void>> handleTool(ToolExecutionException ex) {
        log.warn("[api] tool execution failed traceId={} error={}", TraceIds.current(), ex.getMessage());
        return respond(ApiErrorCode.TOOL_EXECUTION_ERROR, ApiErrorCode.TOOL_EXECUTION_ERROR.message());
    }

    /**
     * Agent 超迭代 → 500 {@code AGENT_LOOP_EXCEEDED}。
     */
    @ExceptionHandler(AgentLoopExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleLoopExceeded(AgentLoopExceededException ex) {
        log.warn("[api] agent loop exceeded traceId={} error={}", TraceIds.current(), ex.getMessage());
        return respond(ApiErrorCode.AGENT_LOOP_EXCEEDED, ApiErrorCode.AGENT_LOOP_EXCEEDED.message());
    }

    /**
     * Agent 执行异常（模型输出空白等） → 500 {@code AGENT_EXECUTION_ERROR}。
     */
    @ExceptionHandler(AgentExecutionException.class)
    public ResponseEntity<ApiResponse<Void>> handleAgentExecution(AgentExecutionException ex) {
        log.warn("[api] agent execution failed traceId={} error={}", TraceIds.current(), ex.getMessage());
        return respond(ApiErrorCode.AGENT_EXECUTION_ERROR, ApiErrorCode.AGENT_EXECUTION_ERROR.message());
    }

    /**
     * Workflow 实例不存在 → 404 {@code WORKFLOW_NOT_FOUND}。
     */
    @ExceptionHandler(WorkflowNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleWorkflowNotFound(WorkflowNotFoundException ex) {
        return respond(ApiErrorCode.WORKFLOW_NOT_FOUND, ex.getMessage());
    }

    /**
     * Workflow 非待审状态 → 409 {@code WORKFLOW_NOT_PENDING}。
     */
    @ExceptionHandler(WorkflowNotPendingException.class)
    public ResponseEntity<ApiResponse<Void>> handleWorkflowNotPending(WorkflowNotPendingException ex) {
        return respond(ApiErrorCode.WORKFLOW_NOT_PENDING, ex.getMessage());
    }

    /**
     * 平台资源不存在 → 404 {@code PLATFORM_NOT_FOUND}。
     */
    @ExceptionHandler(PlatformNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handlePlatformNotFound(PlatformNotFoundException ex) {
        return respond(ApiErrorCode.PLATFORM_NOT_FOUND, ex.getMessage());
    }

    /**
     * 平台资源冲突 → 409 {@code PLATFORM_CONFLICT}。
     */
    @ExceptionHandler(PlatformConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handlePlatformConflict(PlatformConflictException ex) {
        return respond(ApiErrorCode.PLATFORM_CONFLICT, ex.getMessage());
    }

    /**
     * Agent 已禁用 → 400 {@code AGENT_DISABLED}。
     */
    @ExceptionHandler(PlatformAgentDisabledException.class)
    public ResponseEntity<ApiResponse<Void>> handlePlatformAgentDisabled(PlatformAgentDisabledException ex) {
        return respond(ApiErrorCode.AGENT_DISABLED, ex.getMessage());
    }

    /**
     * 未预期错误 → 500 {@code INTERNAL_ERROR}；堆栈只进服务端日志。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception ex) {
        log.error("[api] unexpected error traceId={}", TraceIds.current(), ex);
        return respond(ApiErrorCode.INTERNAL_ERROR, ApiErrorCode.INTERNAL_ERROR.message());
    }

    /** 统一出参：HTTP 状态取自错误码枚举，响应体带 traceId。 */
    private ResponseEntity<ApiResponse<Void>> respond(ApiErrorCode errorCode, String message) {
        String safeMessage = (message == null || message.isBlank()) ? errorCode.message() : message;
        return ResponseEntity.status(errorCode.status())
                .body(ApiResponse.error(errorCode.name(), safeMessage, TraceIds.current()));
    }
}
