package com.aicode.framework.observability.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * SkyWalking Toolkit 真实桥（Week 19）。
 *
 * <p><b>为什么用反射</b>：Toolkit 的静态方法由 Agent 的 {@code apm-toolkit-activation}
 * 在运行期改写，而类本身来自 {@code provided} 依赖（不打进 fat jar）。直接用编译期符号时，
 * 只要运行环境没挂 Agent 就是 {@code NoClassDefFoundError}；反射 + 显式降级可以做到
 * 「忘挂 Agent 只是没有链路数据，而不是应用起不来」——这是生产可运维性的底线。</p>
 *
 * <p>被调用的四个方法（Toolkit 9.7.0 已核实签名）：</p>
 * <ul>
 *   <li>{@code Tracer.createLocalSpan(String)} → {@code SpanRef}</li>
 *   <li>{@code Tracer.stopSpan()}</li>
 *   <li>{@code ActiveSpan.tag(String, String)} / {@code ActiveSpan.error(String)}</li>
 *   <li>{@code TraceContext.traceId()} / {@code TraceContext.spanId()}</li>
 * </ul>
 *
 * <p><b>已知硬约束</b>：{@code ActiveSpan.tag} 作用于「当前活动 span」而不是句柄，
 * 因此标签必须在 span 生命周期内、同一线程写入。装饰器正是这样用的（开 span → 写标签 → 关 span）。</p>
 */
public final class ToolkitSpanBridge implements SkyWalkingSpanBridge {

    private static final Logger log = LoggerFactory.getLogger(ToolkitSpanBridge.class);

    /** 无操作名时的兜底 span 名，避免链路后端出现空名 span。 */
    public static final String FALLBACK_OPERATION = "aicode.span";

    private static final String TRACER_CLASS = "org.apache.skywalking.apm.toolkit.trace.Tracer";
    private static final String ACTIVE_SPAN_CLASS = "org.apache.skywalking.apm.toolkit.trace.ActiveSpan";
    private static final String TRACE_CONTEXT_CLASS = "org.apache.skywalking.apm.toolkit.trace.TraceContext";

    private final Method createLocalSpan;
    private final Method stopSpan;
    private final Method tag;
    private final Method error;
    private final Method traceId;
    private final Method spanId;

    private ToolkitSpanBridge(
            Method createLocalSpan, Method stopSpan, Method tag, Method error, Method traceId, Method spanId) {
        this.createLocalSpan = createLocalSpan;
        this.stopSpan = stopSpan;
        this.tag = tag;
        this.error = error;
        this.traceId = traceId;
        this.spanId = spanId;
    }

    /**
     * 尝试创建真实桥（使用 SkyWalking 官方 Toolkit 类名）。
     *
     * @return Toolkit 类全部可加载时返回真实桥；否则返回 null（调用方降级为空实现）
     */
    public static ToolkitSpanBridge tryCreate() {
        return tryCreate(ToolkitSpanBridge.class.getClassLoader(), TRACER_CLASS, ACTIVE_SPAN_CLASS, TRACE_CONTEXT_CLASS);
    }

    /**
     * 按指定类名创建桥（单测用：可指向桩类，从而在不挂 Agent 的环境里验证反射调用形态）。
     *
     * @param classLoader      类加载器
     * @param tracerClass      等价于 {@code org.apache.skywalking.apm.toolkit.trace.Tracer} 的类名
     * @param activeSpanClass  等价于 {@code ActiveSpan} 的类名
     * @param traceContextClass 等价于 {@code TraceContext} 的类名
     * @return 桥；任一类或方法不可用时返回 null
     */
    public static ToolkitSpanBridge tryCreate(
            ClassLoader classLoader, String tracerClass, String activeSpanClass, String traceContextClass) {
        try {
            Class<?> tracer = Class.forName(tracerClass, true, classLoader);
            Class<?> activeSpan = Class.forName(activeSpanClass, true, classLoader);
            Class<?> traceContext = Class.forName(traceContextClass, true, classLoader);
            return new ToolkitSpanBridge(
                    tracer.getMethod("createLocalSpan", String.class),
                    tracer.getMethod("stopSpan"),
                    activeSpan.getMethod("tag", String.class, String.class),
                    activeSpan.getMethod("error", String.class),
                    traceContext.getMethod("traceId"),
                    traceContext.getMethod("spanId"));
        } catch (ReflectiveOperationException | LinkageError ex) {
            log.warn("[skywalking] Toolkit 类不可用（未挂 Java Agent？），手动埋点降级为空实现：{}", ex.toString());
            return null;
        }
    }

    @Override
    public boolean available() {
        // Toolkit 类在 classpath 上（provided 依赖，编译期可见）不等于 Agent 已挂载：
        // 只有 Agent 启动时注入的 skywalking.* 系统属性存在，才说明链路真的会被采集。
        // 不区分这两者会让自检接口谎报「已接入」（本地 fat jar 忘挂 Agent 时最容易发生）。
        return System.getProperty("skywalking.agent.service_name") != null
                || System.getProperty("skywalking.collector.backend_service") != null;
    }

    @Override
    public String currentTraceId() {
        Object value = invoke(traceId, null);
        return value == null ? "" : String.valueOf(value);
    }

    @Override
    public int currentSpanId() {
        Object value = invoke(spanId, null);
        return value instanceof Integer span ? span : -1;
    }

    @Override
    public SkyWalkingSpanHandle startLocalSpan(String operationName) {
        String name = operationName == null || operationName.isBlank()
                ? FALLBACK_OPERATION : operationName;
        Object handle = invoke(createLocalSpan, null, name);
        return handle == null ? NoopSpanBridge.INSTANCE.startLocalSpan(operationName) : new ToolkitHandle();
    }

    @Override
    public void tagActiveSpan(String key, String value) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            return;
        }
        invoke(tag, null, key, value);
    }

    @Override
    public void errorActiveSpan(String message) {
        invoke(error, null, message == null ? "error" : message);
    }

    @Override
    public void stopSpan() {
        invoke(stopSpan, null);
    }

    /**
     * 反射调用静态方法；任何失败都只记 DEBUG 并返回 null（可观测不得影响业务）。
     *
     * @param method 目标方法
     * @param target 实例（静态方法传 null）
     * @param args   参数
     * @return 返回值；失败返回 null
     */
    private Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            log.debug("[skywalking] Toolkit 调用失败（忽略）：{}", ex.toString());
            return null;
        }
    }

    /** Toolkit span 句柄：{@code close()} 幂等。 */
    private final class ToolkitHandle implements SkyWalkingSpanHandle {

        private boolean closed;

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            stopSpan();
        }
    }
}
