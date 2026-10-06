package com.aicode.framework.observability.support;

import java.util.ArrayList;
import java.util.List;

/**
 * SkyWalking Toolkit 桩（单测用，Week 19）。
 *
 * <p>三个静态内部类分别复刻官方 Toolkit 中 {@code Tracer} / {@code ActiveSpan} /
 * {@code TraceContext} 的<b>被反射调用的方法签名</b>，并把调用记录下来。
 * 这样在不挂 Java Agent 的环境里也能验证「反射调用形态是否正确」，
 * 而真实 Toolkit 的类名只在 {@code ToolkitSpanBridge} 里出现一处。</p>
 */
public final class SkyWalkingToolkitStub {

    /** 全局调用记录（每个测试自行清空）。 */
    public static final List<String> CALLS = new ArrayList<>();

    /** 当前 SkyWalking traceId。 */
    public static String traceId = "sw-trace-1";

    /** 当前 SkyWalking spanId。 */
    public static int spanId = 3;

    /** createLocalSpan 是否抛异常（验证桥的容错）。 */
    public static boolean failOnCreate;

    private SkyWalkingToolkitStub() {
    }

    /** 清空记录与状态。 */
    public static void reset() {
        CALLS.clear();
        traceId = "sw-trace-1";
        spanId = 3;
        failOnCreate = false;
    }

    /** 复刻 {@code org.apache.skywalking.apm.toolkit.trace.Tracer}。 */
    public static final class Tracer {

        /**
         * 创建本地 span。
         *
         * @param operationName 操作名
         * @return span 引用
         */
        public static Object createLocalSpan(String operationName) {
            CALLS.add("createLocalSpan:" + operationName);
            if (failOnCreate) {
                throw new IllegalStateException("toolkit failure");
            }
            return new Object();
        }

        /** 结束当前 span。 */
        public static void stopSpan() {
            CALLS.add("stopSpan");
        }
    }

    /** 复刻 {@code org.apache.skywalking.apm.toolkit.trace.ActiveSpan}。 */
    public static final class ActiveSpan {

        /**
         * 给当前活动 span 写标签。
         *
         * @param key   键
         * @param value 值
         */
        public static void tag(String key, String value) {
            CALLS.add("tag:" + key + "=" + value);
        }

        /**
         * 标记当前活动 span 失败。
         *
         * @param message 说明
         */
        public static void error(String message) {
            CALLS.add("error:" + message);
        }
    }

    /** 复刻 {@code org.apache.skywalking.apm.toolkit.trace.TraceContext}。 */
    public static final class TraceContext {

        /**
         * 当前 traceId。
         *
         * @return traceId
         */
        public static String traceId() {
            return traceId;
        }

        /**
         * 当前 spanId。
         *
         * @return spanId
         */
        public static int spanId() {
            return spanId;
        }
    }
}
