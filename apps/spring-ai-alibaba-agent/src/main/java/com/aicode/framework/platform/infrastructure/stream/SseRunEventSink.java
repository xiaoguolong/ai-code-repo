package com.aicode.framework.platform.infrastructure.stream;

import com.aicode.core.domain.exception.ChatModelException;
import com.aicode.core.domain.exception.GuardrailViolationException;
import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.core.domain.exception.ToolExecutionException;
import com.aicode.framework.dto.ApiErrorCode;
import com.aicode.framework.dto.ApiResponse;
import com.aicode.framework.infrastructure.logging.TraceIds;
import com.aicode.framework.platform.application.RunEventSink;
import com.aicode.framework.platform.domain.model.ExecutionRecord;
import com.aicode.framework.platform.dto.ExecutionRecordResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SSE 事件出口（Week 31）：阻塞式逐帧写 + flush。
 *
 * <p><b>真机教训</b>：{@code SseEmitter} 与 {@code AsyncContext} 两条路都验证过——写入后不保证
 * 及时落到 socket，Tomcat 的响应缓冲会把整段短答案攒到结束才发（表现：首字=总耗时；
 * meta 因为触发首次 flush 反而最早到达）。最终改为在<b>请求线程</b>同步执行、直接写
 * {@link OutputStream} 并逐帧 {@code flush}，用并发信号量限制同时进行的流数量。</p>
 *
 * <p>事件：{@code meta} / {@code delta} / {@code final} / {@code error}。
 * 写失败（浏览器关闭/代理断开）只置取消标记，不抛异常；终态只发一次；
 * {@code final} 显式带 traceId（SSE 绕过 ResponseBodyAdvice）。</p>
 */
public final class SseRunEventSink implements RunEventSink {

    private static final Logger log = LoggerFactory.getLogger(SseRunEventSink.class);

    private final OutputStream output;
    private final ObjectMapper objectMapper;
    private final Object sendLock = new Object();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicBoolean terminal = new AtomicBoolean();

    public SseRunEventSink(OutputStream output, ObjectMapper objectMapper) {
        this.output = output;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onStarted(ExecutionRecord running) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("executionId", running.executionId());
        meta.put("agentKey", running.agentKey());
        meta.put("startedAt", running.startedAt().toString());
        meta.put("traceId", TraceIds.current());
        send("meta", meta);
    }

    @Override
    public void onDelta(String text) {
        send("delta", Map.of("text", text));
    }

    @Override
    public void onCompleted(ExecutionRecord record) {
        if (terminal.compareAndSet(false, true)) {
            ApiResponse<ExecutionRecordResponse> envelope = new ApiResponse<>(
                    ApiResponse.SUCCESS_CODE, "OK",
                    ExecutionRecordResponse.from(record), TraceIds.current());
            send("final", envelope);
        }
    }

    @Override
    public void onFailed(ExecutionRecord record, RuntimeException error) {
        if (!terminal.compareAndSet(false, true)) {
            return;
        }
        ApiErrorCode code = codeOf(error);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code.name());
        payload.put("message", code.message());
        payload.put("traceId", TraceIds.current());
        payload.put("executionId", record.executionId());
        send("error", payload);
    }

    @Override
    public void onCancelled(ExecutionRecord record) {
        terminal.compareAndSet(false, true);
        log.info("[platform] sse cancelled executionId={} agentKey={}",
                record.executionId(), record.agentKey());
    }

    @Override
    public boolean isCancelled() {
        return cancelled.get();
    }

    @Override
    public boolean isTerminal() {
        return terminal.get();
    }

    /**
     * 兜底：执行在终态之前抛出（例如落 RUNNING 失败），仍给前端一条错误事件。
     *
     * @param error 异常
     */
    public void onUnhandled(RuntimeException error) {
        if (terminal.compareAndSet(false, true)) {
            ApiErrorCode code = codeOf(error);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("code", code.name());
            payload.put("message", code.message());
            payload.put("traceId", TraceIds.current());
            send("error", payload);
        }
    }

    /** 心跳：写一行 SSE 注释，避免空闲连接被中间层掐断。 */
    public void ping() {
        if (cancelled.get() || terminal.get()) {
            return;
        }
        writeFrame(": ping\n\n");
    }

    /** 结束前的收尾 flush（响应由容器关闭）。 */
    public void close() {
        try {
            output.flush();
        } catch (IOException | IllegalStateException ex) {
            log.debug("[platform] sse flush on close failed: {}", ex.getMessage());
        }
    }

    private void cancel(String reason) {
        if (cancelled.compareAndSet(false, true)) {
            log.info("[platform] sse client gone reason={}", reason);
        }
    }

    private void send(String event, Object payload) {
        if (cancelled.get()) {
            return;
        }
        try {
            String frame = "event:" + event + "\ndata:" + objectMapper.writeValueAsString(payload) + "\n\n";
            writeFrame(frame);
        } catch (IOException | IllegalStateException ex) {
            cancel("send failed: " + ex.getMessage());
        }
    }

    private void writeFrame(String frame) {
        try {
            byte[] bytes = frame.getBytes(StandardCharsets.UTF_8);
            synchronized (sendLock) {
                output.write(bytes);
                output.flush();
            }
        } catch (IOException | IllegalStateException ex) {
            cancel("write failed: " + ex.getMessage());
        }
    }

    private ApiErrorCode codeOf(RuntimeException error) {
        if (error instanceof ChatModelException) {
            return ApiErrorCode.CHAT_MODEL_ERROR;
        }
        if (error instanceof ToolExecutionException) {
            return ApiErrorCode.TOOL_EXECUTION_ERROR;
        }
        if (error instanceof GuardrailViolationException) {
            return ApiErrorCode.GUARDRAIL_VIOLATION;
        }
        if (error instanceof InvalidChatRequestException) {
            return ApiErrorCode.VALIDATION_ERROR;
        }
        return ApiErrorCode.INTERNAL_ERROR;
    }
}
