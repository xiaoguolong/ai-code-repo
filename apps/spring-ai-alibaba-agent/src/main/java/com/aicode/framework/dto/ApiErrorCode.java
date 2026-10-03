package com.aicode.framework.dto;

import org.springframework.http.HttpStatus;

/**
 * API 错误码唯一来源（Week 16 企业规范）。
 *
 * <p>规范要求：</p>
 * <ul>
 *   <li>枚举名即对外 {@code code}，统一 {@code UPPER_SNAKE_CASE}；</li>
 *   <li>成功码固定为字符串 {@code SUCCESS}，不属于错误码，故不在此枚举；</li>
 *   <li>{@link #message()} 是兜底文案，必须为中文且不含堆栈、密钥、厂商报文；</li>
 *   <li>{@link #status()} 决定 HTTP 状态码，禁止一律 200。</li>
 * </ul>
 *
 * <p>新增错误码必须同步补充 {@code docs/api/week-16-api.md} 的错误码表与契约测试。</p>
 */
public enum ApiErrorCode {

    /** 入参校验失败（Bean Validation 或用例层入参检查）。HTTP 400。 */
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "请求参数不合法"),

    /** 未登录或登录态过期。HTTP 401。 */
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "未登录或登录已过期"),

    /** 已登录但权限不足（RBAC / 数据域越权）。HTTP 403。 */
    FORBIDDEN(HttpStatus.FORBIDDEN, "无权访问该资源"),

    /** 内容安全护栏拦截（注入、超长、敏感）。HTTP 400。 */
    GUARDRAIL_VIOLATION(HttpStatus.BAD_REQUEST, "请求内容未通过安全校验"),

    /** 平台资源不存在（Agent / 执行记录 / 角色）。HTTP 404。 */
    PLATFORM_NOT_FOUND(HttpStatus.NOT_FOUND, "平台资源不存在"),

    /** 平台资源状态冲突（重复注册等）。HTTP 409。 */
    PLATFORM_CONFLICT(HttpStatus.CONFLICT, "平台资源状态冲突"),

    /** Agent 已被禁用。HTTP 400。 */
    AGENT_DISABLED(HttpStatus.BAD_REQUEST, "该 Agent 已被禁用"),

    /** 模型调用失败（上游超时、限流、报文异常）。HTTP 502。 */
    CHAT_MODEL_ERROR(HttpStatus.BAD_GATEWAY, "模型调用失败"),

    /** 工具执行失败（未知工具、参数非法、业务异常）。HTTP 502。 */
    TOOL_EXECUTION_ERROR(HttpStatus.BAD_GATEWAY, "工具执行失败"),

    /** Agent 未能在限定步数内完成。HTTP 500。 */
    AGENT_LOOP_EXCEEDED(HttpStatus.INTERNAL_SERVER_ERROR, "Agent 未能在限定步数内完成"),

    /** Agent 执行异常（模型输出空白等）。HTTP 500。 */
    AGENT_EXECUTION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Agent 执行失败"),

    /** Workflow 实例不存在。HTTP 404。 */
    WORKFLOW_NOT_FOUND(HttpStatus.NOT_FOUND, "工作流实例不存在"),

    /** Workflow 不处于待审批状态。HTTP 409。 */
    WORKFLOW_NOT_PENDING(HttpStatus.CONFLICT, "工作流不处于待审批状态"),

    /** 未预期异常，对外只给通用文案，堆栈仅进服务端日志。HTTP 500。 */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "系统繁忙，请稍后重试");

    private final HttpStatus status;
    private final String message;

    ApiErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    /**
     * 该错误码对应的 HTTP 状态。
     */
    public HttpStatus status() {
        return status;
    }

    /**
     * 兜底中文文案。具体接口可在不放内部细节的前提下覆盖为更精确的提示。
     */
    public String message() {
        return message;
    }
}
