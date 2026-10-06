package com.aicode.framework.observability.infrastructure;

/**
 * 空实现桥（Week 19）：{@code observability.skywalking.enabled=false} 或 Agent 未挂载时装配。
 *
 * <p>所有方法为空操作，不加载任何 Toolkit 类，因此不挂 Agent 的环境（含全部单测）
 * 行为与 Week 18 完全一致。</p>
 */
public final class NoopSpanBridge implements SkyWalkingSpanBridge {

    /** 单例：无状态，可复用。 */
    public static final NoopSpanBridge INSTANCE = new NoopSpanBridge();

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public String currentTraceId() {
        return "";
    }

    @Override
    public int currentSpanId() {
        return -1;
    }

    @Override
    public SkyWalkingSpanHandle startLocalSpan(String operationName) {
        return NoopHandle.INSTANCE;
    }

    @Override
    public void tagActiveSpan(String key, String value) {
        // 空操作
    }

    @Override
    public void errorActiveSpan(String message) {
        // 空操作
    }

    @Override
    public void stopSpan() {
        // 空操作
    }

    /** 空句柄。 */
    private static final class NoopHandle implements SkyWalkingSpanHandle {

        private static final NoopHandle INSTANCE = new NoopHandle();

        @Override
        public void close() {
            // 空操作
        }
    }
}
