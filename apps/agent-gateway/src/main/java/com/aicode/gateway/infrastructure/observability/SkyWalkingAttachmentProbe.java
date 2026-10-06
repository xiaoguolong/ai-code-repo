package com.aicode.gateway.infrastructure.observability;

import org.springframework.stereotype.Component;

/**
 * SkyWalking Agent 挂载探针（Week 19）。
 *
 * <p>网关侧不写一行 SkyWalking 代码：HTTP / Redis / Feign / JVM 全部由 Java Agent 自动埋点。
 * 但运维需要一个「到底挂上没有」的确定答案，而不是靠翻启动脚本 —— 本探针用
 * <b>类加载探测</b>回答：Toolkit 的 {@code TraceContext} 只会在 Agent 挂载时被 Agent 的
 * 类加载器提供，因此它的可加载性就是最可靠的判据（不依赖任何配置项，也无法被误配骗过）。</p>
 */
@Component
public class SkyWalkingAttachmentProbe {

    /** Toolkit 类名：Agent 未挂载时一定不存在。 */
    private static final String TOOLKIT_CLASS = "org.apache.skywalking.apm.toolkit.trace.TraceContext";

    /**
     * 是否检测到 SkyWalking Agent。
     *
     * @return 挂载返回 true
     */
    public boolean attached() {
        try {
            Class.forName(TOOLKIT_CLASS, false, Thread.currentThread().getContextClassLoader());
            return true;
        } catch (Throwable ex) {
            return false;
        }
    }
}
