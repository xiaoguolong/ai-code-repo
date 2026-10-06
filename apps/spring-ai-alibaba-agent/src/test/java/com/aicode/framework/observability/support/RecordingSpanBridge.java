package com.aicode.framework.observability.support;

import com.aicode.framework.observability.infrastructure.SkyWalkingSpanBridge;

import java.util.ArrayList;
import java.util.List;

/**
 * SkyWalking 桥的假实现（单测用，Week 19）。
 *
 * <p>记录调用序列，便于断言装饰器「先建 span → 写标签 → 关 span」的顺序与内容；
 * 也可配置为不可用以验证降级分支。</p>
 */
public class RecordingSpanBridge implements SkyWalkingSpanBridge {

    /** 调用记录。 */
    public final List<String> calls = new ArrayList<>();

    /** 是否可用。 */
    public boolean available = true;

    /** traceId 返回值。 */
    public String traceId = "sw-trace";

    /** spanId 返回值。 */
    public int spanId = 1;

    @Override
    public boolean available() {
        return available;
    }

    @Override
    public String currentTraceId() {
        return traceId;
    }

    @Override
    public int currentSpanId() {
        return spanId;
    }

    @Override
    public SkyWalkingSpanHandle startLocalSpan(String operationName) {
        // 与真实桥同口径：空操作名用兜底名（否则会记录出 "start:" 这种空名，掩盖真实行为）
        String name = operationName == null || operationName.isBlank()
                ? "aicode.span" : operationName;
        calls.add("start:" + name);
        return () -> calls.add("stop");
    }

    @Override
    public void tagActiveSpan(String key, String value) {
        calls.add("tag:" + key + "=" + value);
    }

    @Override
    public void errorActiveSpan(String message) {
        calls.add("error:" + message);
    }

    @Override
    public void stopSpan() {
        calls.add("stop");
    }

    /**
     * 某个调用是否出现（前缀匹配）。
     *
     * @param prefix 前缀
     * @return 出现返回 true
     */
    public boolean sawCall(String prefix) {
        return calls.stream().anyMatch(call -> call.startsWith(prefix));
    }

    /**
     * 某个标签是否写入。
     *
     * @param key   标签键
     * @param value 标签值
     * @return 写入返回 true
     */
    public boolean sawTag(String key, String value) {
        return calls.contains("tag:" + key + "=" + value);
    }
}
