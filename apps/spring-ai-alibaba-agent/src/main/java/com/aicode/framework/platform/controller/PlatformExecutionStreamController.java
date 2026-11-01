package com.aicode.framework.platform.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.aicode.framework.platform.application.PlatformExecutionUseCase;
import com.aicode.framework.platform.application.PreparedExecution;
import com.aicode.framework.platform.domain.exception.PlatformStreamBusyException;
import com.aicode.framework.platform.domain.exception.PlatformStreamDisabledException;
import com.aicode.framework.platform.dto.PlatformRunRequest;
import com.aicode.framework.platform.infrastructure.config.PlatformStreamProperties;
import com.aicode.framework.platform.infrastructure.stream.PlatformStreamDispatcher;
import com.aicode.framework.platform.infrastructure.stream.SseRunEventSink;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

/**
 * 平台流式执行 API（Week 31）。
 *
 * <p>与同步端点 {@code POST /agents/{key}/runs} 的差异只有"怎么把结果给你"：</p>
 * <ul>
 *   <li>预检在<b>请求线程</b>同步完成：401/403/404/400/429/503 仍是正确状态码 + 统一 JSON 信封；</li>
 *   <li>预检通过后在<b>请求线程</b>直接执行并写 {@code text/event-stream}，事件为
 *       {@code meta → delta* → final|error}，每帧 write 后立即 flush；</li>
 *   <li>断连写失败 → 取消上游模型，执行记录落 {@code CANCELLED}，不留 RUNNING；</li>
 *   <li>并发由信号量限制（默认 4），满了在落执行记录之前 429。</li>
 * </ul>
 *
 * <p><b>为什么不用 SseEmitter / AsyncContext</b>：真机两条路都验证过，写入后不保证及时落到 socket，
 * Tomcat 响应缓冲会把整段短答案攒到结束才发（首字=总耗时）。阻塞式直接写输出流并逐帧 flush
 * 是唯一在真机上验证过首字即到的方案；代价是每条流占一个请求线程（受并发信号量约束）。</p>
 *
 * <p>请求头建议带 {@code Accept: text/event-stream, application/json}：预检失败要能回 JSON 信封。</p>
 */
@RestController
@RequestMapping("/api/v1/platform")
public class PlatformExecutionStreamController {

    private static final Logger log = LoggerFactory.getLogger(PlatformExecutionStreamController.class);

    private final PlatformExecutionUseCase platformExecutionUseCase;
    private final PlatformStreamProperties properties;
    private final PlatformStreamDispatcher dispatcher;
    private final ObjectMapper objectMapper;

    public PlatformExecutionStreamController(
            PlatformExecutionUseCase platformExecutionUseCase,
            PlatformStreamProperties properties,
            PlatformStreamDispatcher dispatcher,
            ObjectMapper objectMapper
    ) {
        this.platformExecutionUseCase = platformExecutionUseCase;
        this.properties = properties;
        this.dispatcher = dispatcher;
        this.objectMapper = objectMapper;
    }

    /**
     * 流式执行一个 Agent（仅 {@code knowledge-qa} / {@code patient-analysis}）。
     *
     * @param agentKey Agent 标识
     * @param request  与同步端点相同的入参
     * @param response 写 SSE 响应头与输出流
     * @throws IOException 获取输出流失败
     */
    @PostMapping("/agents/{agentKey}/runs/stream")
    public void streamAgentRun(
            @PathVariable String agentKey,
            @Valid @RequestBody PlatformRunRequest request,
            HttpServletResponse response
    ) throws IOException {
        StpUtil.checkLogin();
        long userId = StpUtil.getLoginIdAsLong();
        if (!properties.isEnabled()) {
            throw new PlatformStreamDisabledException("流式输出未启用");
        }
        Map<String, Object> input = request.input() == null ? Map.of() : request.input();
        PreparedExecution prepared = platformExecutionUseCase.prepareStream(userId, agentKey, input);
        if (!dispatcher.tryAcquire()) {
            throw new PlatformStreamBusyException("当前流式并发已满，请稍后再试");
        }

        response.setContentType("text/event-stream;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        // 小缓冲 + 每帧 flush：即使容器/中间层有额外缓冲，首字也不会被攒到结束。
        response.setBufferSize(256);

        SseRunEventSink sink = new SseRunEventSink(response.getOutputStream(), objectMapper);
        try {
            platformExecutionUseCase.executeStream(prepared, sink);
        } catch (RuntimeException ex) {
            sink.onUnhandled(ex);
        } finally {
            sink.close();
            dispatcher.release();
        }
        log.info("[platform] sse run finished agentKey={} cancelled={}", agentKey, sink.isCancelled());
    }
}
